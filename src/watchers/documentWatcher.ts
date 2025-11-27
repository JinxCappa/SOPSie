import * as vscode from 'vscode';
import { ConfigManager } from '../config/configManager';
import { ContextManager } from '../context/contextManager';
import { SopsDetector } from '../sops/sopsDetector';
import { SopsRunner } from '../sops/sopsRunner';
import { StatusBarProvider } from '../providers/statusBarProvider';
import { DecryptedContentProvider } from '../providers/decryptedContentProvider';
import { SettingsService } from '../services/settingsService';
import { EditorGroupTracker } from '../services/editorGroupTracker';
import { DecryptedViewService } from '../services/decryptedViewService';
import { FileStateTracker } from '../state/fileStateTracker';
import { AutoBehaviorHandler } from '../handlers/autoBehaviorHandler';
import { FileEncryptionState, SOPS_DECRYPTED_SCHEME } from '../types';
import { logger } from '../services/loggerService';

/**
 * Tracks document events and manages file state.
 * Central coordinator for file state changes, context updates, and auto behaviors.
 */
export class DocumentWatcher implements vscode.Disposable {
    private disposables: vscode.Disposable[] = [];
    private fileStateTracker = new FileStateTracker();
    private autoBehaviorHandler: AutoBehaviorHandler;

    constructor(
        private configManager: ConfigManager,
        private contextManager: ContextManager,
        private sopsDetector: SopsDetector,
        private sopsRunner: SopsRunner,
        private statusBarProvider: StatusBarProvider,
        private decryptedContentProvider: DecryptedContentProvider,
        private settingsService: SettingsService,
        private editorGroupTracker: EditorGroupTracker,
        private decryptedViewService: DecryptedViewService
    ) {
        // Initialize auto behavior handler
        this.autoBehaviorHandler = new AutoBehaviorHandler(
            configManager,
            sopsDetector,
            sopsRunner,
            this.settingsService,
            this.fileStateTracker
        );

        // Track active editor changes
        this.disposables.push(
            vscode.window.onDidChangeActiveTextEditor((editor) => {
                this.onEditorActivated(editor);
            })
        );

        // Track document opens for auto-preview behavior
        this.disposables.push(
            vscode.workspace.onDidOpenTextDocument((doc) => {
                this.autoBehaviorHandler.handleDocumentOpened(doc);
            })
        );

        // Track document saves for save behavior
        this.disposables.push(
            vscode.workspace.onWillSaveTextDocument((event) => {
                this.autoBehaviorHandler.handleDocumentWillSave(event);
            })
        );

        // Track document closes
        this.disposables.push(
            vscode.workspace.onDidCloseTextDocument((doc) => {
                this.onDocumentClosed(doc);
            })
        );
    }

    /**
     * Mark a file as decrypted (for tracking save behavior).
     * Called after successful decrypt operations.
     */
    async markDecrypted(uri: vscode.Uri): Promise<void> {
        this.autoBehaviorHandler.markDecrypted(uri);
        await this.updateContext(uri);
    }

    /**
     * Mark a file as encrypted.
     * Called after successful encrypt operations.
     */
    async markEncrypted(uri: vscode.Uri): Promise<void> {
        this.fileStateTracker.markEncrypted(uri);
        this.decryptedContentProvider.refresh(uri.fsPath);
        await this.updateContext(uri);
    }

    /**
     * Update context for current active editor.
     * Refreshes VS Code context keys and status bar.
     */
    async updateCurrentEditor(): Promise<void> {
        await this.onEditorActivated(vscode.window.activeTextEditor);
    }

    private async onEditorActivated(
        editor: vscode.TextEditor | undefined
    ): Promise<void> {
        if (!editor) {
            this.contextManager.clearContext();
            this.statusBarProvider.hide();
            return;
        }

        await this.updateContext(editor.document.uri, editor.document);

        // Check if we should update the preview/edit panel to match the focused file
        if (editor.document.uri.scheme === 'file') {
            await this.maybeUpdateDecryptedView(editor.document.uri);
        }
    }

    /**
     * Update the decrypted view to show the currently focused encrypted file.
     * If openBehavior is showDecrypted, opens a preview even if one isn't already open.
     * If a preview is already open (and openDecryptedBeside=true), switches it to the newly focused file.
     * Also handles closing previews when user switches to non-SOPS files (when openBehavior=showDecrypted).
     */
    private async maybeUpdateDecryptedView(focusedUri: vscode.Uri): Promise<void> {
        const fileName = focusedUri.path.split('/').pop();
        logger.debug('[DocumentWatcher] maybeUpdateDecryptedView called for:', fileName);

        // Skip if extension is in the middle of opening a document (prevent recursion)
        if (this.editorGroupTracker.isExtensionOpening()) {
            logger.debug('[DocumentWatcher] Skipping - extension is opening');
            return;
        }

        // Skip if we recently closed the preview for THIS specific file (prevent re-opening)
        if (this.editorGroupTracker.isClosingPairedFor(focusedUri)) {
            logger.debug('[DocumentWatcher] Skipping - recently closed preview for this file');
            return;
        }

        const currentTracked = this.editorGroupTracker.getCurrentTrackedDocument();
        const openBehavior = this.settingsService.getOpenBehavior();
        const openBeside = this.settingsService.shouldOpenDecryptedBeside();

        logger.debug('[DocumentWatcher] State: currentTracked=', !!currentTracked, 'openBehavior=', openBehavior, 'openBeside=', openBeside);

        // Check if the focused file is the same as the currently previewed file
        if (currentTracked && currentTracked.originalActiveUri === focusedUri.toString()) {
            logger.debug('[DocumentWatcher] Skipping - same file already tracked');
            return;
        }

        // Check if the focused file IS the tracked decrypted document itself (edit-in-place temp file)
        if (currentTracked && currentTracked.docUri === focusedUri.toString()) {
            logger.debug('[DocumentWatcher] Skipping - focused on tracked decrypted doc');
            return;
        }

        // Check if focused file is SOPS-encrypted
        const hasRule = this.configManager.hasMatchingRule(focusedUri);
        const isEncrypted = hasRule ? await this.sopsDetector.isEncrypted(focusedUri) : false;
        const isSopsEncrypted = hasRule && isEncrypted;

        logger.debug('[DocumentWatcher] SOPS check: hasRule=', hasRule, 'isEncrypted=', isEncrypted);

        // If openBehavior is not showDecrypted, we don't manage previews here
        if (openBehavior !== 'showDecrypted') {
            logger.debug('[DocumentWatcher] Skipping - openBehavior is not showDecrypted');
            return;
        }

        // If focused file is NOT a SOPS encrypted file, close any open previews
        if (!isSopsEncrypted) {
            logger.debug('[DocumentWatcher] Not SOPS encrypted, closing any tracked docs');
            if (currentTracked && this.settingsService.shouldAutoCloseTab()) {
                await this.editorGroupTracker.closeAllTrackedDocuments();
            }
            return;
        }

        // If no preview is open yet, open a fresh one
        if (!currentTracked) {
            logger.debug('[DocumentWatcher] No preview open, opening new one');
            await this.decryptedViewService.openDecryptedView(focusedUri, {
                preserveFocus: openBeside,
                showInfoMessage: false
            });
            return;
        }

        // A preview is already open for a different file
        logger.debug('[DocumentWatcher] Preview open for different file, switching...');
        if (openBeside) {
            await this.decryptedViewService.switchToFile(focusedUri);
        } else {
            // In same-group mode, close the old preview and open a new one
            logger.debug('[DocumentWatcher] Same-column mode: closing old, opening new');
            await this.editorGroupTracker.closeAllTrackedDocuments();
            await this.decryptedViewService.openDecryptedView(focusedUri, {
                preserveFocus: false,
                showInfoMessage: false
            });
        }
        logger.debug('[DocumentWatcher] maybeUpdateDecryptedView done');
    }

    private async updateContext(uri: vscode.Uri, document?: vscode.TextDocument): Promise<void> {
        // Skip non-file schemes (except our preview scheme)
        if (uri.scheme !== 'file' && uri.scheme !== SOPS_DECRYPTED_SCHEME) {
            this.contextManager.clearContext();
            this.statusBarProvider.hide();
            return;
        }

        // For preview URIs, get the original file path
        const fileUri =
            uri.scheme === SOPS_DECRYPTED_SCHEME
                ? vscode.Uri.file(DecryptedContentProvider.getOriginalPath(uri))
                : uri;

        const hasMatchingRule = this.configManager.hasMatchingRule(fileUri);

        let encryptionState: FileEncryptionState;

        if (!hasMatchingRule) {
            encryptionState = FileEncryptionState.Unknown;
        } else if (this.fileStateTracker.isMarkedDecrypted(fileUri)) {
            encryptionState = FileEncryptionState.Decrypted;
        } else {
            // Use in-memory content if document is available, otherwise read from disk
            const isEncrypted = document
                ? this.sopsDetector.isDocumentEncrypted(document)
                : await this.sopsDetector.isEncrypted(fileUri);
            encryptionState = isEncrypted
                ? FileEncryptionState.Encrypted
                : FileEncryptionState.PlainText;
        }

        this.contextManager.setFileContext(hasMatchingRule, encryptionState);
        this.statusBarProvider.update(
            hasMatchingRule,
            encryptionState,
            this.settingsService.shouldShowStatusBar()
        );
    }

    private async onDocumentClosed(doc: vscode.TextDocument): Promise<void> {
        // Clean up file state tracking only
        // Tab-based closing is handled by EditorGroupTracker.handleTabsChanged()
        this.fileStateTracker.clearFile(doc.uri);
    }

    dispose(): void {
        this.disposables.forEach((d) => d.dispose());
        this.fileStateTracker.clear();
    }
}
