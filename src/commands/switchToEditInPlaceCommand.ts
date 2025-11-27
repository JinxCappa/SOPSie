import * as vscode from 'vscode';
import { DecryptedViewService } from '../services/decryptedViewService';
import { EditorGroupTracker } from '../services/editorGroupTracker';
import { logger } from '../services/loggerService';
import { getErrorMessage } from '../utils/errorUtils';
import { SOPS_DECRYPTED_SCHEME } from '../types';

/**
 * Register the switch-to-edit-in-place command.
 * Switches from a read-only preview to an editable temp file.
 * Used when clicking the edit button on a preview tab.
 */
export function registerSwitchToEditInPlaceCommand(
    decryptedViewService: DecryptedViewService,
    editorGroupTracker: EditorGroupTracker
): vscode.Disposable {
    return vscode.commands.registerCommand(
        'sopsie.switchToEditInPlace',
        async () => {
            const editor = vscode.window.activeTextEditor;
            if (!editor) {
                logger.debug('SwitchToEditInPlace command: No active editor');
                return;
            }

            // Verify this is a preview tab
            if (editor.document.uri.scheme !== SOPS_DECRYPTED_SCHEME) {
                logger.debug('SwitchToEditInPlace command: Not a preview tab');
                vscode.window.showWarningMessage('This command only works on decrypted preview tabs');
                return;
            }

            const previewUri = editor.document.uri;

            // Untrack the preview document (returns tracking info and prevents handleTabsChanged from interfering)
            const tracked = editorGroupTracker.untrackDocument(previewUri);
            if (!tracked || !tracked.originalActiveUri) {
                logger.debug('SwitchToEditInPlace command: No tracked document or original URI');
                vscode.window.showErrorMessage('Could not find the original encrypted file');
                return;
            }

            const originalUri = vscode.Uri.parse(tracked.originalActiveUri);
            const previewColumn = tracked.openedInColumn;
            const originalColumn = tracked.originalColumn;
            logger.debug(`SwitchToEditInPlace command: Switching from preview to edit-in-place for ${originalUri.fsPath}, preserving originalColumn=${originalColumn}`);

            // Set guard flag BEFORE closing preview to prevent DocumentWatcher from
            // opening a new preview when focus returns to the encrypted file
            editorGroupTracker.setExtensionTriggeredOpen(true);
            try {
                // Find and close the preview tab directly
                // (closeAllTrackedDocuments may not find virtual document tabs reliably)
                for (const tabGroup of vscode.window.tabGroups.all) {
                    for (const tab of tabGroup.tabs) {
                        if (tab.input instanceof vscode.TabInputText &&
                            tab.input.uri.toString() === previewUri.toString()) {
                            logger.debug('SwitchToEditInPlace command: Found preview tab, closing it');
                            await vscode.window.tabGroups.close(tab);
                            break;
                        }
                    }
                }

                // Open edit-in-place in the same column the preview was in
                // Pass originalColumn to preserve correct column tracking for subsequent switches
                await decryptedViewService.openEditInPlace(originalUri, {
                    preserveFocus: false,
                    showInfoMessage: true,
                    targetColumn: previewColumn,
                    originalColumn: originalColumn
                });

                logger.debug(`SwitchToEditInPlace command: Successfully switched to edit-in-place for ${originalUri.fsPath}`);
            } catch (error) {
                logger.error(`SwitchToEditInPlace command: Failed - ${getErrorMessage(error)}`);
                vscode.window.showErrorMessage(
                    `Failed to switch to edit mode: ${getErrorMessage(error)}`
                );
            } finally {
                editorGroupTracker.setExtensionTriggeredOpen(false);
            }
        }
    );
}
