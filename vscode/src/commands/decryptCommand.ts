import * as vscode from 'vscode';
import { SopsRunner } from '../sops/sopsRunner';
import { SopsDetector } from '../sops/sopsDetector';
import { handleError } from '../utils/errorHandler';
import { logger } from '../services/loggerService';

/**
 * Register the decrypt command.
 * Decrypts the active file in-place, replacing encrypted content with plaintext.
 */
export function registerDecryptCommand(
    sopsRunner: SopsRunner,
    sopsDetector: SopsDetector,
    onDecrypted: (uri: vscode.Uri) => Promise<void>
): vscode.Disposable {
    return vscode.commands.registerCommand('sopsie.decrypt', async () => {
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            logger.debug('Decrypt command: No active editor');
            vscode.window.showWarningMessage('No active editor');
            return;
        }

        const uri = editor.document.uri;
        logger.debug(`Decrypt command: Processing ${uri.fsPath}`);

        if (uri.scheme !== 'file') {
            logger.debug('Decrypt command: Skipping non-file scheme');
            vscode.window.showWarningMessage('Can only decrypt local files');
            return;
        }

        if (!sopsDetector.isDocumentEncrypted(editor.document)) {
            logger.debug('Decrypt command: File is not encrypted');
            vscode.window.showInformationMessage('File is not SOPS-encrypted');
            return;
        }

        logger.debug('Decrypt command: Starting decryption');
        try {
            await vscode.window.withProgress(
                {
                    location: vscode.ProgressLocation.Notification,
                    title: 'Decrypting file...',
                    cancellable: false
                },
                async () => {
                    const decrypted = await sopsRunner.decrypt(uri.fsPath);

                    // Replace editor content with decrypted content
                    const edit = new vscode.WorkspaceEdit();
                    const fullRange = new vscode.Range(
                        editor.document.positionAt(0),
                        editor.document.positionAt(editor.document.getText().length)
                    );
                    edit.replace(uri, fullRange, decrypted);
                    await vscode.workspace.applyEdit(edit);

                    // Notify that file was decrypted
                    await onDecrypted(uri);
                }
            );

            logger.debug(`Decrypt command: Successfully decrypted ${uri.fsPath}`);
            vscode.window.showInformationMessage('File decrypted successfully');
        } catch (error) {
            logger.debug(`Decrypt command: Failed to decrypt ${uri.fsPath}`);
            handleError(error);
        }
    });
}

