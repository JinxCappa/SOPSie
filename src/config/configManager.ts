import * as vscode from 'vscode';
import * as path from 'path';
import { parseConfig } from './configParser';
import { RulesMatcher, clearRegexCache } from './rulesMatcher';
import { SopsConfig, SopsCreationRule } from '../types';
import { logger } from '../services/loggerService';
import { getErrorMessage } from '../utils/errorUtils';

interface LoadedConfig {
    config: SopsConfig;
    configPath: string;
    configDir: string;
    matcher: RulesMatcher;
}

/**
 * Manages SOPS configuration across workspaces.
 * Supports .sops.yaml files in any directory, matching SOPS CLI behavior
 * which searches up the directory tree from the target file.
 */
export class ConfigManager implements vscode.Disposable {
    // Map from config file path to loaded config
    private configs: Map<string, LoadedConfig> = new Map();
    private _onDidChangeConfig = new vscode.EventEmitter<vscode.Uri>();

    /**
     * Event fired when a configuration changes
     */
    readonly onDidChangeConfig = this._onDidChangeConfig.event;

    /**
     * Initialize configurations for all workspace folders
     */
    async initialize(): Promise<void> {
        const workspaceFolders = vscode.workspace.workspaceFolders;
        if (!workspaceFolders) {
            return;
        }

        // Find all .sops.yaml files in all workspaces
        const configFiles = await vscode.workspace.findFiles(
            '**/.sops.{yaml,yml}',
            '**/node_modules/**'
        );

        await Promise.all(
            configFiles.map((uri) => this.loadConfig(uri))
        );
    }

    /**
     * Load a specific config file
     */
    async loadConfig(configUri: vscode.Uri): Promise<void> {
        try {
            const content = await vscode.workspace.fs.readFile(configUri);
            const text = Buffer.from(content).toString('utf-8');
            const config = parseConfig(text);
            const configDir = path.dirname(configUri.fsPath);

            this.configs.set(configUri.fsPath, {
                config,
                configPath: configUri.fsPath,
                configDir,
                matcher: new RulesMatcher(config, configDir)
            });
        } catch (error) {
            // Config file doesn't exist or is invalid
            logger.error(`Failed to load config ${configUri.fsPath}:`, getErrorMessage(error));
            this.configs.delete(configUri.fsPath);
        }
    }

    /**
     * Reload configuration when a config file changes
     */
    async reloadConfig(configUri: vscode.Uri): Promise<void> {
        // Clear regex cache to prevent stale patterns
        clearRegexCache();
        await this.loadConfig(configUri);
        this._onDidChangeConfig.fire(configUri);
    }

    /**
     * Handle config file deletion
     */
    removeConfig(configUri: vscode.Uri): void {
        this.configs.delete(configUri.fsPath);
        this._onDidChangeConfig.fire(configUri);
    }

    /**
     * Find the nearest .sops.yaml config for a file by walking up the directory tree.
     * This matches SOPS CLI behavior.
     */
    private findNearestConfig(fileUri: vscode.Uri): LoadedConfig | null {
        const workspaceFolder = vscode.workspace.getWorkspaceFolder(fileUri);
        if (!workspaceFolder) {
            return null;
        }

        const workspaceRoot = workspaceFolder.uri.fsPath;
        let currentDir = path.dirname(fileUri.fsPath);

        // Walk up the directory tree until we hit the workspace root
        while (currentDir.startsWith(workspaceRoot)) {
            // Check for .sops.yaml or .sops.yml in current directory
            for (const configName of ['.sops.yaml', '.sops.yml']) {
                const configPath = path.join(currentDir, configName);
                const loadedConfig = this.configs.get(configPath);
                if (loadedConfig) {
                    return loadedConfig;
                }
            }

            // Move up one directory
            const parentDir = path.dirname(currentDir);
            if (parentDir === currentDir) {
                // Reached filesystem root
                break;
            }
            currentDir = parentDir;
        }

        return null;
    }

    /**
     * Find the matching creation rule for a file
     */
    findMatchingRule(fileUri: vscode.Uri): SopsCreationRule | null {
        const loadedConfig = this.findNearestConfig(fileUri);
        if (!loadedConfig) {
            return null;
        }

        return loadedConfig.matcher.findMatchingRule(fileUri);
    }

    /**
     * Check if a file matches any SOPS creation rule
     */
    hasMatchingRule(fileUri: vscode.Uri): boolean {
        return this.findMatchingRule(fileUri) !== null;
    }

    dispose(): void {
        this._onDidChangeConfig.dispose();
        this.configs.clear();
    }
}
