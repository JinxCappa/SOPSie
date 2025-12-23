import * as vscode from 'vscode';

/**
 * Centralized service for accessing extension settings.
 * Single source of truth for all configuration values.
 * Caches configuration and invalidates on changes.
 */
export class SettingsService implements vscode.Disposable {
    private static readonly CONFIG_NAMESPACE = 'sopsie';
    private cachedConfig: vscode.WorkspaceConfiguration | null = null;
    private disposables: vscode.Disposable[] = [];

    constructor() {
        // Invalidate cache when configuration changes
        this.disposables.push(
            vscode.workspace.onDidChangeConfiguration((e) => {
                if (e.affectsConfiguration(SettingsService.CONFIG_NAMESPACE)) {
                    this.cachedConfig = null;
                }
            })
        );
    }

    /**
     * Get the cached configuration, refreshing if needed
     */
    private getConfig(): vscode.WorkspaceConfiguration {
        if (!this.cachedConfig) {
            this.cachedConfig = vscode.workspace.getConfiguration(SettingsService.CONFIG_NAMESPACE);
        }
        return this.cachedConfig;
    }

    /**
     * Get the SOPS CLI path
     */
    getSopsPath(): string {
        return this.getConfig().get<string>('sopsPath', 'sops');
    }

    /**
     * Get the decryption timeout in milliseconds
     */
    getTimeout(): number {
        return this.getConfig().get<number>('decryptionTimeout', 30000);
    }

    /**
     * Check if rotation confirmation is enabled
     */
    shouldConfirmRotate(): boolean {
        return this.getConfig().get<boolean>('confirmRotate', true);
    }

    /**
     * Check if update keys confirmation is enabled
     */
    shouldConfirmUpdateKeys(): boolean {
        return this.getConfig().get<boolean>('confirmUpdateKeys', true);
    }

    /**
     * Get the open behavior setting
     */
    getOpenBehavior(): 'showEncrypted' | 'autoDecrypt' | 'showDecrypted' {
        return this.getConfig().get<'showEncrypted' | 'autoDecrypt' | 'showDecrypted'>(
            'openBehavior',
            'showEncrypted'
        );
    }

    /**
     * Get the save behavior setting
     */
    getSaveBehavior(): 'manualEncrypt' | 'autoEncrypt' | 'prompt' {
        return this.getConfig().get<'manualEncrypt' | 'autoEncrypt' | 'prompt'>(
            'saveBehavior',
            'manualEncrypt'
        );
    }

    /**
     * Check if status bar should be shown
     */
    shouldShowStatusBar(): boolean {
        return this.getConfig().get<boolean>('showStatusBar', true);
    }

    /**
     * Get the decrypted view mode setting
     */
    getDecryptedViewMode(): 'preview' | 'editInPlace' {
        return this.getConfig().get<'preview' | 'editInPlace'>('decryptedViewMode', 'preview');
    }

    /**
     * Check if edit-in-place mode is enabled
     */
    useEditInPlace(): boolean {
        return this.getDecryptedViewMode() === 'editInPlace';
    }

    /**
     * Check if auto-close tab is enabled
     * When enabled, decrypted tabs close automatically when opening another file
     */
    shouldAutoCloseTab(): boolean {
        return this.getConfig().get<boolean>('autoCloseTab', true);
    }

    /**
     * Check if decrypted view should open in a side-by-side column
     * When false, opens in the same editor group
     */
    shouldOpenDecryptedBeside(): boolean {
        return this.getConfig().get<boolean>('openDecryptedBeside', true);
    }

    /**
     * Check if paired tabs should auto-close together
     * When enabled, closing an encrypted or decrypted tab will close its pair
     */
    shouldAutoClosePairedTab(): boolean {
        return this.getConfig().get<boolean>('autoClosePairedTab', true);
    }

    /**
     * Check if debug logging is enabled
     * When enabled, debug-level messages are shown in the output channel
     */
    isDebugLoggingEnabled(): boolean {
        return this.getConfig().get<boolean>('enableDebugLogging', false);
    }

    /**
     * Dispose of resources
     */
    dispose(): void {
        this.disposables.forEach(d => d.dispose());
        this.disposables = [];
        this.cachedConfig = null;
    }
}
