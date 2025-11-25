import * as vscode from 'vscode';
import { CONTEXT_KEYS, FileEncryptionState } from '../types';

/**
 * Manages VS Code context keys for controlling when-clause conditions
 */
export class ContextManager {
    /**
     * Update context based on file state.
     * Context updates are fire-and-forget - no need to await setContext commands.
     */
    setFileContext(
        hasMatchingRule: boolean,
        encryptionState: FileEncryptionState
    ): void {
        const isEncrypted = encryptionState === FileEncryptionState.Encrypted;
        const isDecrypted =
            encryptionState === FileEncryptionState.Decrypted ||
            encryptionState === FileEncryptionState.PlainText;

        // Fire-and-forget - setContext is internally synchronous
        vscode.commands.executeCommand(
            'setContext',
            CONTEXT_KEYS.IS_SOPS_FILE,
            hasMatchingRule
        );
        vscode.commands.executeCommand(
            'setContext',
            CONTEXT_KEYS.IS_ENCRYPTED_FILE,
            hasMatchingRule && isEncrypted
        );
        vscode.commands.executeCommand(
            'setContext',
            CONTEXT_KEYS.IS_DECRYPTED_FILE,
            hasMatchingRule && isDecrypted
        );
        vscode.commands.executeCommand(
            'setContext',
            CONTEXT_KEYS.HAS_MATCHING_RULE,
            hasMatchingRule
        );
    }

    /**
     * Clear all context keys (e.g., when no editor is active)
     */
    clearContext(): void {
        // Fire-and-forget - setContext is internally synchronous
        vscode.commands.executeCommand('setContext', CONTEXT_KEYS.IS_SOPS_FILE, false);
        vscode.commands.executeCommand('setContext', CONTEXT_KEYS.IS_ENCRYPTED_FILE, false);
        vscode.commands.executeCommand('setContext', CONTEXT_KEYS.IS_DECRYPTED_FILE, false);
        vscode.commands.executeCommand('setContext', CONTEXT_KEYS.HAS_MATCHING_RULE, false);
    }

    /**
     * Update edit-in-place context based on setting
     */
    setEditInPlaceContext(useEditInPlace: boolean): void {
        vscode.commands.executeCommand(
            'setContext',
            CONTEXT_KEYS.USE_EDIT_IN_PLACE,
            useEditInPlace
        );
    }
}
