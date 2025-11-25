import * as vscode from 'vscode';
import * as path from 'path';
import { SopsRunner } from '../sops/sopsRunner';
import { TempFileHandler } from '../handlers/tempFileHandler';
import { DecryptedContentProvider } from '../providers/decryptedContentProvider';
import { SettingsService } from './settingsService';
import { EditorGroupTracker } from './editorGroupTracker';

export interface ShowDecryptedOptions {
    /** Whether to preserve focus on the original editor (true for auto, false for manual) */
    preserveFocus: boolean;
    /** Show info message on success (for edit-in-place mode). Defaults to true. */
    showInfoMessage?: boolean;
}

/**
 * Service for opening decrypted views of SOPS files.
 * Handles both read-only preview and editable temp file modes.
 */
export class DecryptedViewService implements vscode.Disposable {
    constructor(
        private sopsRunner: SopsRunner,
        private tempFileHandler: TempFileHandler,
        private settingsService: SettingsService,
        private editorGroupTracker: EditorGroupTracker
    ) {}

    /**
     * Set document language with fallback to plaintext
     */
    private async setDocumentLanguage(doc: vscode.TextDocument, filePath: string): Promise<void> {
        const languageId = DecryptedContentProvider.getLanguageId(filePath);
        try {
            await vscode.languages.setTextDocumentLanguage(doc, languageId);
        } catch {
            // Language ID may not be available (e.g., 'dotenv' requires an extension)
            if (languageId !== 'plaintext') {
                await vscode.languages.setTextDocumentLanguage(doc, 'plaintext');
            }
        }
    }

    /**
     * Track document after opening, handling stale editor references
     */
    private trackOpenedDocument(
        doc: vscode.TextDocument,
        sourceUri: vscode.Uri,
        shownEditor: vscode.TextEditor,
        viewColumn: vscode.ViewColumn
    ): vscode.ViewColumn {
        // Re-fetch the editor after setTextDocumentLanguage (original shownEditor may be stale)
        const currentEditor = vscode.window.visibleTextEditors.find(
            e => e.document.uri.toString() === doc.uri.toString()
        ) ?? shownEditor;

        const effectiveViewColumn = currentEditor.viewColumn ?? viewColumn;
        this.editorGroupTracker.trackDocumentOpened(doc.uri, sourceUri, effectiveViewColumn);
        return effectiveViewColumn;
    }

    /**
     * Open a decrypted view based on current settings.
     * Routes to either preview or edit-in-place based on decryptedViewMode setting.
     */
    async openDecryptedView(
        sourceUri: vscode.Uri,
        options: ShowDecryptedOptions
    ): Promise<void> {
        if (this.settingsService.getDecryptedViewMode() === 'editInPlace') {
            await this.openEditInPlace(sourceUri, options);
        } else {
            await this.openPreview(sourceUri, options);
        }
    }

    /**
     * Open a read-only preview of the decrypted content.
     * Uses a virtual document provider to display decrypted content without modifying the file.
     */
    async openPreview(
        sourceUri: vscode.Uri,
        options: ShowDecryptedOptions
    ): Promise<void> {
        const previewUri = DecryptedContentProvider.createPreviewUri(sourceUri);

        // Set guard flag to prevent auto-close/focus-return during the entire operation
        this.editorGroupTracker.setExtensionTriggeredOpen(true);
        try {
            const doc = await vscode.workspace.openTextDocument(previewUri);
            const viewColumn = this.settingsService.shouldOpenDecryptedBeside()
                ? vscode.ViewColumn.Beside
                : vscode.ViewColumn.Active;
            const shownEditor = await vscode.window.showTextDocument(doc, {
                viewColumn,
                preview: false,
                preserveFocus: options.preserveFocus
            });

            await this.setDocumentLanguage(doc, sourceUri.fsPath);
            this.trackOpenedDocument(doc, sourceUri, shownEditor, viewColumn);
        } finally {
            this.editorGroupTracker.setExtensionTriggeredOpen(false);
        }
    }

    /**
     * Open an editable temp file with decrypted content.
     * Creates a temporary file that automatically encrypts back to the original on save.
     */
    async openEditInPlace(
        sourceUri: vscode.Uri,
        options: ShowDecryptedOptions
    ): Promise<void> {
        const decrypted = await vscode.window.withProgress(
            {
                location: vscode.ProgressLocation.Notification,
                title: 'Decrypting file...',
                cancellable: false
            },
            async () => {
                return await this.sopsRunner.decrypt(sourceUri.fsPath);
            }
        );

        const tempUri = await this.tempFileHandler.createTempFile(sourceUri, decrypted);

        // Set guard flag to prevent auto-close/focus-return during the entire operation
        this.editorGroupTracker.setExtensionTriggeredOpen(true);
        try {
            const doc = await vscode.workspace.openTextDocument(tempUri);
            const viewColumn = this.settingsService.shouldOpenDecryptedBeside()
                ? vscode.ViewColumn.Beside
                : vscode.ViewColumn.Active;
            const shownEditor = await vscode.window.showTextDocument(doc, {
                viewColumn,
                preview: false,
                preserveFocus: options.preserveFocus
            });

            await this.setDocumentLanguage(doc, sourceUri.fsPath);
            this.trackOpenedDocument(doc, sourceUri, shownEditor, viewColumn);
        } finally {
            this.editorGroupTracker.setExtensionTriggeredOpen(false);
        }

        if (options.showInfoMessage !== false) {
            vscode.window.showInformationMessage(
                `Editing decrypted copy. Save to encrypt back to ${path.basename(sourceUri.fsPath)}`
            );
        }
    }

    /**
     * Switch the current preview/edit-in-place to show a different file.
     * Handles unsaved changes in edit-in-place mode with a prompt.
     */
    async switchToFile(newSourceUri: vscode.Uri): Promise<void> {
        const currentMode = this.getCurrentMode();
        if (!currentMode) {
            await this.openDecryptedView(newSourceUri, { preserveFocus: true });
            return;
        }

        if (currentMode === 'editInPlace') {
            const tempDoc = this.getCurrentTempDocument();
            if (tempDoc?.isDirty) {
                const choice = await vscode.window.showWarningMessage(
                    'You have unsaved changes in the decrypted file. Save before switching?',
                    'Save',
                    'Discard',
                    'Cancel'
                );
                if (choice === 'Cancel' || choice === undefined) {
                    return;
                }
                if (choice === 'Save') {
                    await tempDoc.save();
                }
            }
        }

        await this.editorGroupTracker.closeAllTrackedDocuments();
        await this.openDecryptedView(newSourceUri, { preserveFocus: true, showInfoMessage: false });

        // Ensure focus returns to the source file after opening
        try {
            const sourceDoc = vscode.workspace.textDocuments.find(
                d => d.uri.toString() === newSourceUri.toString()
            );
            if (sourceDoc) {
                await vscode.window.showTextDocument(sourceDoc, {
                    viewColumn: vscode.ViewColumn.One,
                    preserveFocus: false,
                    preview: false
                });
            }
        } catch {
            // Ignore focus errors
        }
    }

    /**
     * Get the current mode based on tracked document type.
     */
    private getCurrentMode(): 'preview' | 'editInPlace' | null {
        const tracked = this.editorGroupTracker.getCurrentTrackedDocument();
        if (!tracked) {
            return null;
        }

        if (tracked.docUri.startsWith('sops-decrypted://')) {
            return 'preview';
        }
        if (tracked.docUri.includes('.sops-edit')) {
            return 'editInPlace';
        }
        return null;
    }

    /**
     * Get the TextDocument for the current temp file (if in edit-in-place mode).
     */
    private getCurrentTempDocument(): vscode.TextDocument | undefined {
        const tracked = this.editorGroupTracker.getCurrentTrackedDocument();
        if (!tracked) {
            return undefined;
        }

        return vscode.workspace.textDocuments.find(
            (doc) => doc.uri.toString() === tracked.docUri
        );
    }

    dispose(): void {
        // Currently no disposables needed - service is stateless
    }
}
