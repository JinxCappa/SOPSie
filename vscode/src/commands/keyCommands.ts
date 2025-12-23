import * as vscode from 'vscode';
import { SopsRunner } from '../sops/sopsRunner';
import { SopsDetector } from '../sops/sopsDetector';
import { SettingsService } from '../services/settingsService';
import { handleError } from '../utils/errorHandler';
import { logger } from '../services/loggerService';

/**
 * Reload a file in the editor after in-place modification by SOPS CLI.
 * Uses VS Code's revert command to refresh the editor content from disk.
 */
async function reloadFileInEditor(uri: vscode.Uri): Promise<void> {
    const openDoc = vscode.workspace.textDocuments.find(
        doc => doc.uri.toString() === uri.toString()
    );
    if (openDoc) {
        logger.debug(`Reloading file in editor: ${uri.fsPath}`);
        await vscode.commands.executeCommand('workbench.action.files.revert', uri);
    }
}

/**
 * Validate that a URI is a local file scheme.
 * Returns false and shows a warning if not valid.
 */
function validateLocalFileScheme(uri: vscode.Uri, commandName: string): boolean {
    if (uri.scheme !== 'file') {
        logger.debug(`${commandName} command: Skipping non-file scheme`);
        vscode.window.showWarningMessage(`Can only ${commandName.toLowerCase()} for local files`);
        return false;
    }
    return true;
}

/**
 * Register the updateKeys command.
 * Re-encrypts the file with keys defined in .sops.yaml, updating access control.
 */
export function registerUpdateKeysCommand(
    sopsRunner: SopsRunner,
    sopsDetector: SopsDetector,
    settingsService: SettingsService,
    onUpdated: (uri: vscode.Uri) => Promise<void>
): vscode.Disposable {
    return vscode.commands.registerCommand(
        'sopsie.updateKeys',
        async (resourceUri?: vscode.Uri) => {
            // Support both editor context and explorer context
            const uri = resourceUri ?? vscode.window.activeTextEditor?.document.uri;

            if (!uri) {
                logger.debug('UpdateKeys command: No file selected');
                vscode.window.showWarningMessage('No file selected');
                return;
            }

            logger.debug(`UpdateKeys command: Processing ${uri.fsPath}`);

            if (!validateLocalFileScheme(uri, 'Update keys')) {
                return;
            }

            const isEncrypted = await sopsDetector.isEncrypted(uri);
            if (!isEncrypted) {
                logger.debug('UpdateKeys command: File is not encrypted');
                vscode.window.showInformationMessage(
                    'File is not SOPS-encrypted. Update keys only works on encrypted files.'
                );
                return;
            }

            // Confirm update if setting is enabled
            if (settingsService.shouldConfirmUpdateKeys()) {
                const confirm = await vscode.window.showWarningMessage(
                    'Update SOPS keys? This will re-encrypt the file with keys from .sops.yaml, which may change who can access this file.',
                    { modal: true },
                    'Update Keys'
                );

                if (confirm !== 'Update Keys') {
                    logger.debug('UpdateKeys command: User cancelled');
                    return;
                }
            }

            logger.debug('UpdateKeys command: Starting key update');
            try {
                await vscode.window.withProgress(
                    {
                        location: vscode.ProgressLocation.Notification,
                        title: 'Updating SOPS keys...',
                        cancellable: false
                    },
                    async () => {
                        await sopsRunner.updateKeys(uri.fsPath);
                        await onUpdated(uri);
                    }
                );

                await reloadFileInEditor(uri);

                logger.debug(`UpdateKeys command: Successfully updated keys for ${uri.fsPath}`);
                vscode.window.showInformationMessage('SOPS keys updated successfully');
            } catch (error) {
                logger.debug(`UpdateKeys command: Failed for ${uri.fsPath}`);
                handleError(error);
            }
        }
    );
}

/**
 * Register the rotate command.
 * Rotates the data encryption key, re-encrypting all values with a new key.
 */
export function registerRotateCommand(
    sopsRunner: SopsRunner,
    sopsDetector: SopsDetector,
    settingsService: SettingsService,
    onRotated: (uri: vscode.Uri) => Promise<void>
): vscode.Disposable {
    return vscode.commands.registerCommand(
        'sopsie.rotate',
        async (resourceUri?: vscode.Uri) => {
            // Support both editor context and explorer context
            const uri = resourceUri ?? vscode.window.activeTextEditor?.document.uri;

            if (!uri) {
                logger.debug('Rotate command: No file selected');
                vscode.window.showWarningMessage('No file selected');
                return;
            }

            logger.debug(`Rotate command: Processing ${uri.fsPath}`);

            if (!validateLocalFileScheme(uri, 'Rotate keys')) {
                return;
            }

            const isEncrypted = await sopsDetector.isEncrypted(uri);
            if (!isEncrypted) {
                logger.debug('Rotate command: File is not encrypted');
                vscode.window.showInformationMessage(
                    'File is not SOPS-encrypted. Rotate only works on encrypted files.'
                );
                return;
            }

            // Confirm rotation if setting is enabled
            if (settingsService.shouldConfirmRotate()) {
                const confirm = await vscode.window.showWarningMessage(
                    'Rotate the data key? This will re-encrypt all values with a new data key.',
                    { modal: true },
                    'Rotate'
                );

                if (confirm !== 'Rotate') {
                    logger.debug('Rotate command: User cancelled');
                    return;
                }
            }

            logger.debug('Rotate command: Starting data key rotation');
            try {
                await vscode.window.withProgress(
                    {
                        location: vscode.ProgressLocation.Notification,
                        title: 'Rotating SOPS data key...',
                        cancellable: false
                    },
                    async () => {
                        await sopsRunner.rotate(uri.fsPath);
                        await onRotated(uri);
                    }
                );

                await reloadFileInEditor(uri);

                logger.debug(`Rotate command: Successfully rotated data key for ${uri.fsPath}`);
                vscode.window.showInformationMessage('SOPS data key rotated successfully');
            } catch (error) {
                logger.debug(`Rotate command: Failed for ${uri.fsPath}`);
                handleError(error);
            }
        }
    );
}
