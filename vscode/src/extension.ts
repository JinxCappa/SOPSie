import * as vscode from 'vscode';
import { ConfigManager } from './config/configManager';
import { ContextManager } from './context/contextManager';
import { SopsRunner } from './sops/sopsRunner';
import { SopsDetector } from './sops/sopsDetector';
import { DecryptedContentProvider } from './providers/decryptedContentProvider';
import { StatusBarProvider } from './providers/statusBarProvider';
import { ConfigWatcher } from './watchers/configWatcher';
import { DocumentWatcher } from './watchers/documentWatcher';
import { registerDecryptCommand } from './commands/decryptCommand';
import { registerEncryptCommand } from './commands/encryptCommand';
import { registerPreviewCommand, registerReloadConfigCommand } from './commands/previewCommand';
import { registerEditInPlaceCommand } from './commands/editInPlaceCommand';
import { registerSwitchToEditInPlaceCommand } from './commands/switchToEditInPlaceCommand';
import { registerUpdateKeysCommand, registerRotateCommand } from './commands/keyCommands';
import { TempFileHandler } from './handlers/tempFileHandler';
import { SOPS_DECRYPTED_SCHEME } from './types';
import { getErrorMessage } from './utils/errorUtils';
import { SettingsService } from './services/settingsService';
import { EditorGroupTracker } from './services/editorGroupTracker';
import { DecryptedViewService } from './services/decryptedViewService';
import { LoggerService, logger } from './services/loggerService';
import { registerToggleDebugCommand } from './commands/debugCommand';

export async function activate(context: vscode.ExtensionContext) {
    // Initialize logger first (before any logging calls)
    const loggerService = LoggerService.getInstance();
    const settingsService = new SettingsService();

    // Set initial log level based on settings
    loggerService.updateLogLevel(settingsService.isDebugLoggingEnabled());

    logger.info('SOPSie extension activating...');

    // Initialize core services
    const configManager = new ConfigManager();
    const contextManager = new ContextManager();
    const sopsRunner = new SopsRunner(settingsService);
    const sopsDetector = new SopsDetector();
    const statusBarProvider = new StatusBarProvider();
    const editorGroupTracker = new EditorGroupTracker(settingsService);
    const tempFileHandler = new TempFileHandler(sopsRunner);
    const decryptedViewService = new DecryptedViewService(
        sopsRunner,
        tempFileHandler,
        settingsService,
        editorGroupTracker
    );

    // Set initial edit-in-place context based on setting
    contextManager.setEditInPlaceContext(settingsService.useEditInPlace());

    // Initialize configuration
    await configManager.initialize();

    // Register TextDocumentContentProvider for decrypted previews
    const decryptedContentProvider = new DecryptedContentProvider(sopsRunner);
    context.subscriptions.push(
        vscode.workspace.registerTextDocumentContentProvider(
            SOPS_DECRYPTED_SCHEME,
            decryptedContentProvider
        )
    );

    // Initialize document watcher
    const documentWatcher = new DocumentWatcher(
        configManager,
        contextManager,
        sopsDetector,
        sopsRunner,
        statusBarProvider,
        decryptedContentProvider,
        settingsService,
        editorGroupTracker,
        decryptedViewService
    );

    // Register commands
    context.subscriptions.push(
        registerDecryptCommand(
            sopsRunner,
            sopsDetector,
            (uri) => documentWatcher.markDecrypted(uri)
        )
    );

    context.subscriptions.push(
        registerEncryptCommand(
            sopsRunner,
            sopsDetector,
            (uri) => documentWatcher.markEncrypted(uri)
        )
    );

    context.subscriptions.push(registerPreviewCommand(decryptedViewService));

    context.subscriptions.push(registerEditInPlaceCommand(decryptedViewService));

    context.subscriptions.push(
        registerSwitchToEditInPlaceCommand(decryptedViewService, editorGroupTracker)
    );

    context.subscriptions.push(
        registerUpdateKeysCommand(
            sopsRunner,
            sopsDetector,
            settingsService,
            async () => {
                await documentWatcher.updateCurrentEditor();
            }
        )
    );

    context.subscriptions.push(
        registerRotateCommand(
            sopsRunner,
            sopsDetector,
            settingsService,
            async () => {
                await documentWatcher.updateCurrentEditor();
            }
        )
    );

    context.subscriptions.push(
        registerReloadConfigCommand(async () => {
            await configManager.initialize();
            await documentWatcher.updateCurrentEditor();
        })
    );

    // Register debug toggle command
    context.subscriptions.push(registerToggleDebugCommand());

    // Set up config watcher
    const configWatcher = new ConfigWatcher();
    configWatcher.onDidChange(async (uri) => {
        try {
            logger.debug(`SOPS config changed: ${uri.fsPath}`);
            await configManager.reloadConfig(uri);
            await documentWatcher.updateCurrentEditor();
            vscode.window.showInformationMessage('SOPS configuration reloaded');
        } catch (error) {
            logger.error(`Error reloading config: ${getErrorMessage(error)}`);
        }
    });
    configWatcher.onDidCreate(async (uri) => {
        try {
            logger.debug(`SOPS config created: ${uri.fsPath}`);
            await configManager.loadConfig(uri);
            await documentWatcher.updateCurrentEditor();
            vscode.window.showInformationMessage('SOPS configuration loaded');
        } catch (error) {
            logger.error(`Error loading config: ${getErrorMessage(error)}`);
        }
    });
    configWatcher.onDidDelete(async (uri) => {
        try {
            logger.debug(`SOPS config deleted: ${uri.fsPath}`);
            configManager.removeConfig(uri);
            await documentWatcher.updateCurrentEditor();
        } catch (error) {
            logger.error(`Error removing config: ${getErrorMessage(error)}`);
        }
    });

    // Watch for file changes to invalidate decrypted content cache
    // Only watch common SOPS file types to avoid watching entire workspace
    const fileWatcher = vscode.workspace.createFileSystemWatcher('**/*.{yaml,yml,json,env,ini}');
    fileWatcher.onDidChange((uri) => {
        decryptedContentProvider.refresh(uri.fsPath);
    });
    context.subscriptions.push(fileWatcher);

    // Listen for config manager changes
    configManager.onDidChangeConfig(async () => {
        await documentWatcher.updateCurrentEditor();
    });

    // Listen for configuration changes
    context.subscriptions.push(
        vscode.workspace.onDidChangeConfiguration((e) => {
            if (e.affectsConfiguration('sopsie')) {
                contextManager.setEditInPlaceContext(settingsService.useEditInPlace());
                documentWatcher.updateCurrentEditor();

                // Update log level if debug setting changed
                if (e.affectsConfiguration('sopsie.enableDebugLogging')) {
                    loggerService.updateLogLevel(settingsService.isDebugLoggingEnabled());
                    logger.info(`Debug logging setting changed: ${settingsService.isDebugLoggingEnabled() ? 'enabled' : 'disabled'}`);
                }
            }
        })
    );

    // Register disposables
    context.subscriptions.push(configManager);
    context.subscriptions.push(statusBarProvider);
    context.subscriptions.push(configWatcher);
    context.subscriptions.push(documentWatcher);
    context.subscriptions.push(decryptedContentProvider);
    context.subscriptions.push(settingsService);
    context.subscriptions.push(tempFileHandler);
    context.subscriptions.push(editorGroupTracker);
    context.subscriptions.push(decryptedViewService);
    context.subscriptions.push(loggerService);

    // Initialize context for currently open editor
    await documentWatcher.updateCurrentEditor();

    // Check if SOPS CLI is available
    const cliAvailable = await sopsRunner.checkCliAvailable();
    if (!cliAvailable) {
        const action = await vscode.window.showWarningMessage(
            'SOPS CLI not found. Some features may not work.',
            'Install Guide',
            'Configure Path'
        );

        if (action === 'Install Guide') {
            vscode.env.openExternal(
                vscode.Uri.parse('https://github.com/getsops/sops#install')
            );
        } else if (action === 'Configure Path') {
            vscode.commands.executeCommand(
                'workbench.action.openSettings',
                'sopsPath'
            );
        }
    } else {
        const version = await sopsRunner.getVersion();
        logger.info(`SOPS CLI found: ${version}`);
    }

    logger.info('SOPSie extension activated successfully');
}

export function deactivate() {
    logger.info('SOPSie extension deactivating...');
    // LoggerService is disposed via context.subscriptions
}
