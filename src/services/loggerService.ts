import * as vscode from 'vscode';

/**
 * Centralized logging service using VS Code's native LogOutputChannel API.
 * Provides log levels (debug, info, warn, error) with configurable verbosity.
 *
 * Log level can be controlled via:
 * 1. VS Code setting: sopsie.enableDebugLogging
 * 2. Toggle command: sopsie.toggleDebugLogging (session-only override)
 * 3. VS Code's "Developer: Set Log Level..." command (affects all extensions)
 */
export class LoggerService implements vscode.Disposable {
    private static instance: LoggerService | null = null;
    private outputChannel: vscode.LogOutputChannel;
    private sessionDebugOverride: boolean | null = null;
    private _debugEnabled: boolean = false;

    private constructor() {
        // Creates a LogOutputChannel with built-in log level support
        this.outputChannel = vscode.window.createOutputChannel('SOPSie', { log: true });
    }

    /**
     * Get the singleton instance of LoggerService.
     * Creates the instance if it doesn't exist.
     */
    static getInstance(): LoggerService {
        if (!LoggerService.instance) {
            LoggerService.instance = new LoggerService();
        }
        return LoggerService.instance;
    }

    /**
     * Log a debug message (only shown when debug logging is enabled)
     */
    debug(message: string, ...args: unknown[]): void {
        // Only log debug messages when debug is enabled (via setting or session override)
        if (this.isDebugEnabled()) {
            this.outputChannel.debug(message, ...args);
        }
    }

    /**
     * Log an info message
     */
    info(message: string, ...args: unknown[]): void {
        this.outputChannel.info(message, ...args);
    }

    /**
     * Log a warning message
     */
    warn(message: string, ...args: unknown[]): void {
        this.outputChannel.warn(message, ...args);
    }

    /**
     * Log an error message
     */
    error(message: string, ...args: unknown[]): void {
        this.outputChannel.error(message, ...args);
    }

    /**
     * Log a trace message (most verbose level)
     */
    trace(message: string, ...args: unknown[]): void {
        // Only log trace messages when debug is enabled
        if (this.isDebugEnabled()) {
            this.outputChannel.trace(message, ...args);
        }
    }

    /**
     * Update the log level based on the debug setting.
     * Session override takes precedence over the setting.
     * @param debugEnabled - The value from sopsie.enableDebugLogging setting
     */
    updateLogLevel(debugEnabled: boolean): void {
        this._debugEnabled = this.sessionDebugOverride ?? debugEnabled;
    }

    /**
     * Toggle debug mode for the current session only.
     * This override persists until the extension is reloaded or reset.
     * @returns The new debug state (true = debug enabled)
     */
    toggleSessionDebug(): boolean {
        if (this.sessionDebugOverride === null) {
            // First toggle: enable debug mode
            this.sessionDebugOverride = true;
        } else {
            // Subsequent toggles: flip the state
            this.sessionDebugOverride = !this.sessionDebugOverride;
        }
        this._debugEnabled = this.sessionDebugOverride;
        return this.sessionDebugOverride;
    }

    /**
     * Reset the session override and return to setting-based log level.
     * @param debugEnabled - The value from sopsie.enableDebugLogging setting
     */
    resetSessionOverride(debugEnabled: boolean): void {
        this.sessionDebugOverride = null;
        this.updateLogLevel(debugEnabled);
    }

    /**
     * Check if debug logging is currently enabled.
     */
    isDebugEnabled(): boolean {
        return this._debugEnabled;
    }

    /**
     * Show the output channel in the VS Code panel.
     * @param preserveFocus - If true, the editor focus is not changed
     */
    show(preserveFocus?: boolean): void {
        this.outputChannel.show(preserveFocus);
    }

    /**
     * Get the underlying LogOutputChannel.
     * Useful for error handlers that need to show the channel.
     */
    getOutputChannel(): vscode.LogOutputChannel {
        return this.outputChannel;
    }

    /**
     * Dispose of the logger and its output channel.
     */
    dispose(): void {
        this.outputChannel.dispose();
        LoggerService.instance = null;
    }
}

/**
 * Convenience logger object for direct import and use.
 *
 * @example
 * import { logger } from './services/loggerService';
 *
 * logger.debug('Processing file:', filePath);
 * logger.info('Operation completed');
 * logger.warn('File not found, skipping');
 * logger.error('Failed to decrypt:', error.message);
 */
export const logger = {
    debug: (message: string, ...args: unknown[]) => LoggerService.getInstance().debug(message, ...args),
    info: (message: string, ...args: unknown[]) => LoggerService.getInstance().info(message, ...args),
    warn: (message: string, ...args: unknown[]) => LoggerService.getInstance().warn(message, ...args),
    error: (message: string, ...args: unknown[]) => LoggerService.getInstance().error(message, ...args),
    trace: (message: string, ...args: unknown[]) => LoggerService.getInstance().trace(message, ...args),
};
