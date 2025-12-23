import * as vscode from 'vscode';
import { DecryptedViewService } from '../services/decryptedViewService';
import { logger } from '../services/loggerService';
import { getErrorMessage } from '../utils/errorUtils';

/**
 * Register the show decrypted preview command.
 * Opens a read-only preview of the decrypted content in an adjacent editor column.
 */
export function registerPreviewCommand(
    decryptedViewService: DecryptedViewService
): vscode.Disposable {
    return vscode.commands.registerCommand(
        'sopsie.showDecryptedPreview',
        async () => {
            const editor = vscode.window.activeTextEditor;
            if (!editor) {
                logger.debug('Preview command: No active editor');
                vscode.window.showWarningMessage('No active editor');
                return;
            }

            const uri = editor.document.uri;
            logger.debug(`Preview command: Processing ${uri.fsPath}`);

            if (uri.scheme !== 'file') {
                logger.debug('Preview command: Skipping non-file scheme');
                vscode.window.showWarningMessage('Can only preview local files');
                return;
            }

            try {
                logger.debug('Preview command: Opening decrypted preview');
                await decryptedViewService.openPreview(uri, { preserveFocus: false });
                logger.debug(`Preview command: Successfully opened preview for ${uri.fsPath}`);
            } catch (error) {
                logger.debug(`Preview command: Failed for ${uri.fsPath}`);
                vscode.window.showErrorMessage(`Failed to show decrypted preview: ${getErrorMessage(error)}`);
            }
        }
    );
}

/**
 * Register the reload config command.
 * Reloads all .sops.yaml configuration files from the workspace.
 */
export function registerReloadConfigCommand(
    onReload: () => Promise<void>
): vscode.Disposable {
    return vscode.commands.registerCommand('sopsie.reloadConfig', async () => {
        logger.debug('ReloadConfig command: Reloading SOPS configuration');
        try {
            await onReload();
            logger.debug('ReloadConfig command: Configuration reloaded successfully');
            vscode.window.showInformationMessage('SOPS configuration reloaded');
        } catch (error) {
            logger.debug('ReloadConfig command: Failed to reload configuration');
            vscode.window.showErrorMessage(`Failed to reload config: ${getErrorMessage(error)}`);
        }
    });
}
