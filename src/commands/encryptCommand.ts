import * as vscode from 'vscode';
import { SopsRunner } from '../sops/sopsRunner';
import { SopsDetector } from '../sops/sopsDetector';
import { handleError } from '../utils/errorHandler';
import { logger } from '../services/loggerService';

/**
 * Register the encrypt command.
 * Encrypts the active file in-place using SOPS and the matching creation rule.
 */
export function registerEncryptCommand(
    sopsRunner: SopsRunner,
    sopsDetector: SopsDetector,
    onEncrypted: (uri: vscode.Uri) => Promise<void>
): vscode.Disposable {
    return vscode.commands.registerCommand('sopsie.encrypt', async () => {
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            logger.debug('Encrypt command: No active editor');
            vscode.window.showWarningMessage('No active editor');
            return;
        }

        const uri = editor.document.uri;
        logger.debug(`Encrypt command: Processing ${uri.fsPath}`);

        if (uri.scheme !== 'file') {
            logger.debug('Encrypt command: Skipping non-file scheme');
            vscode.window.showWarningMessage('Can only encrypt local files');
            return;
        }

        if (sopsDetector.isDocumentEncrypted(editor.document)) {
            logger.debug('Encrypt command: File is already encrypted');
            vscode.window.showInformationMessage('File is already SOPS-encrypted');
            return;
        }

        logger.debug('Encrypt command: Starting encryption');
        try {
            await vscode.window.withProgress(
                {
                    location: vscode.ProgressLocation.Notification,
                    title: 'Encrypting file...',
                    cancellable: false
                },
                async () => {
                    // Get current content and encrypt it
                    const content = editor.document.getText();
                    const encrypted = await sopsRunner.encryptContent(
                        content,
                        uri.fsPath
                    );

                    // Replace editor content with encrypted content
                    const edit = new vscode.WorkspaceEdit();
                    const fullRange = new vscode.Range(
                        editor.document.positionAt(0),
                        editor.document.positionAt(content.length)
                    );
                    edit.replace(uri, fullRange, encrypted);
                    await vscode.workspace.applyEdit(edit);

                    // Save the file
                    await editor.document.save();

                    // Notify that file was encrypted
                    await onEncrypted(uri);
                }
            );

            logger.debug(`Encrypt command: Successfully encrypted ${uri.fsPath}`);
            vscode.window.showInformationMessage('File encrypted successfully');
        } catch (error) {
            logger.debug(`Encrypt command: Failed to encrypt ${uri.fsPath}`);
            handleError(error);
        }
    });
}
