import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
import { SopsRunner } from '../sops/sopsRunner';
import { logger } from '../services/loggerService';
import { getErrorMessage } from '../utils/errorUtils';

/**
 * Manages temporary files for edit-in-place functionality.
 * Tracks mapping between temp files and original encrypted files,
 * handles encryption on save, and cleanup on close.
 */
export class TempFileHandler implements vscode.Disposable {
    private tempToOriginal = new Map<string, string>();
    private disposables: vscode.Disposable[] = [];

    constructor(private sopsRunner: SopsRunner) {
        // Listen for document save events
        this.disposables.push(
            vscode.workspace.onDidSaveTextDocument((doc) => {
                this.onDocumentSaved(doc);
            })
        );

        // Listen for document close events
        this.disposables.push(
            vscode.workspace.onDidCloseTextDocument((doc) => {
                this.onDocumentClosed(doc);
            })
        );
    }

    /**
     * Create a temporary file with decrypted content
     * @returns The URI of the temp file
     */
    async createTempFile(
        originalUri: vscode.Uri,
        decryptedContent: string
    ): Promise<vscode.Uri> {
        const originalPath = originalUri.fsPath;
        const ext = path.extname(originalPath);
        const nameWithoutExt = path.basename(originalPath, ext);

        // Create unique temp file name: {name}.sops-edit.{ext}
        const tempFileName = `${nameWithoutExt}.sops-edit${ext}`;
        const tempDir = os.tmpdir();
        const tempPath = path.join(tempDir, tempFileName);

        // Write decrypted content to temp file
        await fs.promises.writeFile(tempPath, decryptedContent, 'utf8');

        // Track the mapping
        this.tempToOriginal.set(tempPath, originalPath);
        logger.debug(`Created temp file: ${tempPath} -> ${originalPath}`);

        return vscode.Uri.file(tempPath);
    }

    /**
     * Handle document save - encrypt and write back to original
     */
    private async onDocumentSaved(doc: vscode.TextDocument): Promise<void> {
        const tempPath = doc.uri.fsPath;
        const originalPath = this.tempToOriginal.get(tempPath);

        if (!originalPath) {
            // Not a managed temp file
            return;
        }

        try {
            const content = doc.getText();

            // Show progress notification
            await vscode.window.withProgress(
                {
                    location: vscode.ProgressLocation.Notification,
                    title: 'Encrypting and saving to original file...',
                    cancellable: false
                },
                async () => {
                    // Encrypt content using SOPS
                    const encrypted = await this.sopsRunner.encryptContent(
                        content,
                        originalPath
                    );

                    // Write encrypted content to original file
                    await fs.promises.writeFile(originalPath, encrypted, 'utf8');
                }
            );

            vscode.window.showInformationMessage(
                `Encrypted and saved to ${path.basename(originalPath)}`
            );
            logger.debug(`Encrypted temp file ${tempPath} -> ${originalPath}`);
        } catch (error) {
            logger.error(`Failed to encrypt: ${getErrorMessage(error)}`);
            vscode.window.showErrorMessage(
                `Failed to encrypt and save: ${getErrorMessage(error)}`
            );
        }
    }

    /**
     * Handle document close - clean up temp file tracking and delete file
     * Note: EditorGroupTracker handles auto-collapse via DocumentWatcher
     */
    private onDocumentClosed(doc: vscode.TextDocument): void {
        const tempPath = doc.uri.fsPath;

        if (this.tempToOriginal.has(tempPath)) {
            this.tempToOriginal.delete(tempPath);
            logger.debug(`Cleaned up temp file tracking: ${tempPath}`);

            // Delete the temp file from disk
            fs.promises.unlink(tempPath).catch((err) => {
                logger.warn(`Could not delete temp file ${tempPath}: ${getErrorMessage(err)}`);
            });
        }
    }

    /**
     * Dispose of resources
     */
    dispose(): void {
        this.disposables.forEach((d) => d.dispose());
        this.disposables = [];

        // Clean up any remaining temp files
        for (const tempPath of this.tempToOriginal.keys()) {
            fs.promises.unlink(tempPath).catch((err) => {
                logger.warn(`Could not delete temp file on dispose ${tempPath}: ${getErrorMessage(err)}`);
            });
        }
        this.tempToOriginal.clear();
    }
}
