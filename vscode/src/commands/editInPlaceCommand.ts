import * as vscode from 'vscode';
import { DecryptedViewService } from '../services/decryptedViewService';
import { logger } from '../services/loggerService';
import { getErrorMessage } from '../utils/errorUtils';

/**
 * Register the edit-in-place command.
 * Opens an editable temporary file with decrypted content that
 * encrypts back to the original file on save.
 */
export function registerEditInPlaceCommand(
    decryptedViewService: DecryptedViewService
): vscode.Disposable {
    return vscode.commands.registerCommand(
        'sopsie.editInPlace',
        async () => {
            const editor = vscode.window.activeTextEditor;
            if (!editor) {
                logger.debug('EditInPlace command: No active editor');
                vscode.window.showWarningMessage('No active editor');
                return;
            }

            const uri = editor.document.uri;
            logger.debug(`EditInPlace command: Processing ${uri.fsPath}`);

            if (uri.scheme !== 'file') {
                logger.debug('EditInPlace command: Skipping non-file scheme');
                vscode.window.showWarningMessage('Can only edit local files');
                return;
            }

            try {
                logger.debug('EditInPlace command: Opening editable temp file');
                await decryptedViewService.openEditInPlace(uri, {
                    preserveFocus: false,
                    showInfoMessage: true
                });
                logger.debug(`EditInPlace command: Successfully opened for ${uri.fsPath}`);
            } catch (error) {
                logger.debug(`EditInPlace command: Failed for ${uri.fsPath}`);
                vscode.window.showErrorMessage(
                    `Failed to open for editing: ${getErrorMessage(error)}`
                );
            }
        }
    );
}
