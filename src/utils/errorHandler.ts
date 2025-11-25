import * as vscode from 'vscode';
import { SopsError, SopsErrorType } from '../types';
import { LoggerService, logger } from '../services/loggerService';

/**
 * Get the output channel from the LoggerService.
 * Used internally to show the output panel when needed.
 */
function getOutputChannel(): vscode.LogOutputChannel {
    return LoggerService.getInstance().getOutputChannel();
}

/**
 * Log a message to the output channel.
 * @deprecated Use `logger.info()` or `logger.debug()` from loggerService instead.
 */
export function log(message: string): void {
    logger.info(message);
}

/**
 * Log an error to the output channel.
 * @deprecated Use `logger.error()` from loggerService instead.
 */
export function logError(message: string, details?: string): void {
    logger.error(message);
    if (details) {
        logger.error(details);
    }
}

/**
 * Handle a SOPS error with user-friendly messages
 */
export async function handleError(error: unknown): Promise<void> {
    // Type guard for SopsError
    if (isSopsError(error)) {
        await handleSopsError(error);
    } else if (error instanceof Error) {
        logError(error.message, error.stack);
        vscode.window.showErrorMessage(`SOPS operation failed: ${error.message}`);
    } else {
        logError(String(error));
        vscode.window.showErrorMessage(`SOPS operation failed: ${String(error)}`);
    }
}

/**
 * Type guard for SopsError
 */
function isSopsError(error: unknown): error is SopsError {
    return (
        typeof error === 'object' &&
        error !== null &&
        'type' in error &&
        'message' in error
    );
}

/**
 * Handle a structured SOPS error
 */
async function handleSopsError(error: SopsError): Promise<void> {
    logError(`[${error.type}] ${error.message}`, error.details);

    switch (error.type) {
        case SopsErrorType.CliNotFound:
            await showCliNotFoundError(error);
            break;

        case SopsErrorType.KeyAccessDenied:
            await showKeyAccessError(error);
            break;

        case SopsErrorType.ConfigNotFound:
            await showConfigNotFoundError(error);
            break;

        case SopsErrorType.ConfigParseError:
            await showConfigParseError(error);
            break;

        case SopsErrorType.Timeout:
            await showTimeoutError(error);
            break;

        case SopsErrorType.DecryptionFailed:
        case SopsErrorType.EncryptionFailed:
        case SopsErrorType.InvalidFile:
        case SopsErrorType.Unknown:
        default:
            await showGenericError(error);
            break;
    }
}

async function showCliNotFoundError(error: SopsError): Promise<void> {
    const action = await vscode.window.showErrorMessage(
        'SOPS CLI not found. Please install SOPS or configure the path in settings.',
        'Install Guide',
        'Open Settings'
    );

    if (action === 'Install Guide') {
        vscode.env.openExternal(
            vscode.Uri.parse('https://github.com/getsops/sops#install')
        );
    } else if (action === 'Open Settings') {
        vscode.commands.executeCommand(
            'workbench.action.openSettings',
            'sopsPath'
        );
    }
}

async function showKeyAccessError(error: SopsError): Promise<void> {
    const action = await vscode.window.showErrorMessage(
        error.message,
        'Show Details',
        'Key Configuration Guide'
    );

    if (action === 'Show Details') {
        const channel = getOutputChannel();
        channel.show();
    } else if (action === 'Key Configuration Guide') {
        vscode.env.openExternal(
            vscode.Uri.parse('https://github.com/getsops/sops#usage')
        );
    }
}

async function showConfigNotFoundError(error: SopsError): Promise<void> {
    const action = await vscode.window.showErrorMessage(
        'No .sops.yaml configuration found in workspace.',
        'Create Config',
        'Documentation'
    );

    if (action === 'Create Config') {
        // Create a sample .sops.yaml file
        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        if (workspaceFolder) {
            const configUri = vscode.Uri.joinPath(
                workspaceFolder.uri,
                '.sops.yaml'
            );
            const sampleConfig = `# SOPS configuration file
# See https://github.com/getsops/sops#using-sopsyaml-conf-to-select-kms-pgp-and-age-for-new-files

creation_rules:
  # Example: Encrypt all files in secrets/ directory
  - path_regex: secrets/.*\\.yaml$
    age: age1xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

  # Example: Encrypt specific files
  # - path_regex: config/secrets\\.json$
  #   pgp: FINGERPRINT
`;
            await vscode.workspace.fs.writeFile(
                configUri,
                Buffer.from(sampleConfig)
            );
            const doc = await vscode.workspace.openTextDocument(configUri);
            await vscode.window.showTextDocument(doc);
        }
    } else if (action === 'Documentation') {
        vscode.env.openExternal(
            vscode.Uri.parse(
                'https://github.com/getsops/sops#using-sopsyaml-conf-to-select-kms-pgp-and-age-for-new-files'
            )
        );
    }
}

async function showConfigParseError(error: SopsError): Promise<void> {
    const action = await vscode.window.showErrorMessage(
        `Failed to parse .sops.yaml: ${error.message}`,
        'Open Config File',
        'Show Details'
    );

    if (action === 'Open Config File') {
        // Try to open .sops.yaml in the workspace
        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        if (workspaceFolder) {
            const configPaths = ['.sops.yaml', '.sops.yml'];
            for (const configName of configPaths) {
                const configUri = vscode.Uri.joinPath(
                    workspaceFolder.uri,
                    configName
                );
                try {
                    const doc = await vscode.workspace.openTextDocument(configUri);
                    await vscode.window.showTextDocument(doc);
                    return;
                } catch {
                    // Try next path
                }
            }
        }
    } else if (action === 'Show Details') {
        const channel = getOutputChannel();
        channel.show();
    }
}

async function showTimeoutError(error: SopsError): Promise<void> {
    const action = await vscode.window.showErrorMessage(
        error.message,
        'Retry',
        'Increase Timeout'
    );

    if (action === 'Increase Timeout') {
        vscode.commands.executeCommand(
            'workbench.action.openSettings',
            'decryptionTimeout'
        );
    }
    // Note: Retry would need to be handled by the caller
}

async function showGenericError(error: SopsError): Promise<void> {
    const action = await vscode.window.showErrorMessage(
        error.message,
        'Show Details'
    );

    if (action === 'Show Details') {
        const channel = getOutputChannel();
        channel.show();
    }
}

/**
 * Dispose the error handler.
 * @deprecated The output channel is now managed by LoggerService.
 * Add LoggerService to context.subscriptions instead.
 */
export function disposeErrorHandler(): void {
    // Output channel is now managed by LoggerService
    // This function is kept for backward compatibility but does nothing
}
