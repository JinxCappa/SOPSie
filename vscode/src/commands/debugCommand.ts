import * as vscode from 'vscode';
import { LoggerService, logger } from '../services/loggerService';

/**
 * Register the toggle debug logging command.
 * This toggles debug mode for the current VS Code session only,
 * without modifying the user's settings.
 */
export function registerToggleDebugCommand(): vscode.Disposable {
    return vscode.commands.registerCommand('sopsie.toggleDebugLogging', async () => {
        const loggerService = LoggerService.getInstance();
        const isNowDebug = loggerService.toggleSessionDebug();

        const message = isNowDebug
            ? 'SOPSie debug logging ENABLED for this session'
            : 'SOPSie debug logging DISABLED for this session';

        logger.info(`Debug logging toggled: ${isNowDebug ? 'ON' : 'OFF'}`);

        const action = await vscode.window.showInformationMessage(
            message,
            'Show Output'
        );

        if (action === 'Show Output') {
            loggerService.show();
        }
    });
}
