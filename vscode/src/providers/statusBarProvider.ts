import * as vscode from 'vscode';
import { FileEncryptionState } from '../types';

/**
 * Manages the status bar item for SOPS file status
 */
export class StatusBarProvider implements vscode.Disposable {
    private statusBarItem: vscode.StatusBarItem;
    private visible = false;

    constructor() {
        this.statusBarItem = vscode.window.createStatusBarItem(
            vscode.StatusBarAlignment.Right,
            100
        );
    }

    /**
     * Update status bar based on file state
     */
    update(
        hasMatchingRule: boolean,
        encryptionState: FileEncryptionState,
        showStatusBar: boolean
    ): void {
        if (!showStatusBar || !hasMatchingRule) {
            this.hide();
            return;
        }

        switch (encryptionState) {
            case FileEncryptionState.Encrypted:
                this.statusBarItem.text = '$(lock) SOPS: Encrypted';
                this.statusBarItem.tooltip =
                    'This file is SOPS-encrypted. Click to decrypt.';
                this.statusBarItem.command = 'sopsie.decrypt';
                this.statusBarItem.backgroundColor = undefined;
                break;

            case FileEncryptionState.Decrypted:
                this.statusBarItem.text = '$(unlock) SOPS: Decrypted';
                this.statusBarItem.tooltip =
                    'This file is decrypted. Click to encrypt.';
                this.statusBarItem.command = 'sopsie.encrypt';
                this.statusBarItem.backgroundColor = new vscode.ThemeColor(
                    'statusBarItem.warningBackground'
                );
                break;

            case FileEncryptionState.PlainText:
                this.statusBarItem.text = '$(shield) SOPS: Not Encrypted';
                this.statusBarItem.tooltip =
                    'This file matches a SOPS rule but is not encrypted. Click to encrypt.';
                this.statusBarItem.command = 'sopsie.encrypt';
                this.statusBarItem.backgroundColor = new vscode.ThemeColor(
                    'statusBarItem.warningBackground'
                );
                break;

            default:
                this.hide();
                return;
        }

        this.show();
    }

    /**
     * Show the status bar item
     */
    show(): void {
        if (!this.visible) {
            this.statusBarItem.show();
            this.visible = true;
        }
    }

    /**
     * Hide the status bar item
     */
    hide(): void {
        if (this.visible) {
            this.statusBarItem.hide();
            this.visible = false;
        }
    }

    dispose(): void {
        this.statusBarItem.dispose();
    }
}
