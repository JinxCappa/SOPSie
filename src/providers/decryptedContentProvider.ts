import * as vscode from 'vscode';
import * as path from 'path';
import { SopsRunner } from '../sops/sopsRunner';
import { SOPS_DECRYPTED_SCHEME } from '../types';
import { logger } from '../services/loggerService';
import { getErrorMessage } from '../utils/errorUtils';

/**
 * Provides decrypted content for virtual documents.
 * Implements VS Code's TextDocumentContentProvider for read-only decrypted previews.
 * Uses an LRU cache to avoid repeated decryption of the same files.
 */
export class DecryptedContentProvider implements vscode.TextDocumentContentProvider {
    private static readonly MAX_CACHE_SIZE = 20;

    private _onDidChange = new vscode.EventEmitter<vscode.Uri>();
    private cache = new Map<string, string>();

    /** Event fired when document content changes (triggers VS Code to re-fetch content) */
    readonly onDidChange = this._onDidChange.event;

    constructor(private sopsRunner: SopsRunner) {}

    /**
     * Provide content for a virtual decrypted document.
     * Called by VS Code when opening a document with the sops-decrypted:// scheme.
     */
    async provideTextDocumentContent(uri: vscode.Uri): Promise<string> {
        // URI format: sops-decrypted:/filename (SOPS Preview)?/original/path
        // The query contains the original file path
        const originalPath = uri.query || uri.path;
        logger.debug(`DecryptedContentProvider: Providing content for ${originalPath}`);

        // Check cache first
        const cached = this.cache.get(originalPath);
        if (cached !== undefined) {
            logger.debug(`DecryptedContentProvider: Cache hit for ${originalPath}`);
            return cached;
        }

        logger.debug(`DecryptedContentProvider: Cache miss, decrypting ${originalPath}`);
        try {
            const decrypted = await this.sopsRunner.decrypt(originalPath);

            // Add a read-only header comment based on file type
            const contentWithHeader = this.addReadOnlyHeader(decrypted, originalPath);

            // Evict oldest entry if cache is full
            this.evictOldestIfNeeded();
            this.cache.set(originalPath, contentWithHeader);
            logger.debug(`DecryptedContentProvider: Cached decrypted content for ${originalPath}`);
            return contentWithHeader;
        } catch (error) {
            logger.error(`DecryptedContentProvider: Failed to decrypt ${originalPath}: ${getErrorMessage(error)}`);
            // Return error message as content so user sees what went wrong
            return `# Failed to decrypt file\n# Error: ${getErrorMessage(error)}\n#\n# Original file: ${originalPath}`;
        }
    }

    /**
     * Evict the oldest cache entry if cache exceeds max size
     */
    private evictOldestIfNeeded(): void {
        if (this.cache.size >= DecryptedContentProvider.MAX_CACHE_SIZE) {
            // Map iterates in insertion order, so first key is oldest
            const firstKey = this.cache.keys().next().value;
            if (firstKey !== undefined) {
                this.cache.delete(firstKey);
            }
        }
    }

    /**
     * Add a read-only header comment to the content
     */
    private addReadOnlyHeader(content: string, filePath: string): string {
        const ext = path.extname(filePath).toLowerCase();
        const filename = path.basename(filePath);

        // JSON doesn't support comments, so skip the header
        if (ext === '.json') {
            return content;
        }

        // All other formats use # for comments
        const commentPrefix = '#';

        const header = [
            `${commentPrefix} ╔══════════════════════════════════════════════════════════════╗`,
            `${commentPrefix} ║  SOPS DECRYPTED PREVIEW (READ-ONLY)                          ║`,
            `${commentPrefix} ║  This is a preview of: ${filename.padEnd(38)}║`,
            `${commentPrefix} ║  To edit, use "SOPS: Decrypt File" on the original file      ║`,
            `${commentPrefix} ╚══════════════════════════════════════════════════════════════╝`,
            '',
        ].join('\n');

        return header + content;
    }

    /**
     * Refresh a specific document by clearing cache and firing change event.
     * Only fires change event if the path was actually cached (has an open preview).
     */
    refresh(originalPath: string): void {
        const wasCached = this.cache.delete(originalPath);
        if (wasCached) {
            logger.debug(`DecryptedContentProvider: Refreshing cached content for ${originalPath}`);
            const filename = path.basename(originalPath);
            const uri = vscode.Uri.from({
                scheme: SOPS_DECRYPTED_SCHEME,
                path: `${filename} (SOPS Preview)`,
                query: originalPath
            });
            this._onDidChange.fire(uri);
        }
    }

    /**
     * Get the original file path from a preview URI
     */
    static getOriginalPath(previewUri: vscode.Uri): string {
        // Original path is stored in the query parameter
        return previewUri.query || previewUri.path;
    }

    /**
     * Create a preview URI from an original file URI
     * The URI path becomes the tab title, query stores the real path
     */
    static createPreviewUri(originalUri: vscode.Uri): vscode.Uri {
        const filename = path.basename(originalUri.fsPath);
        // Use Uri.from() to avoid encoding issues with special characters
        // The path component shows in the tab title
        return vscode.Uri.from({
            scheme: SOPS_DECRYPTED_SCHEME,
            path: `${filename} (SOPS Preview)`,
            query: originalUri.fsPath
        });
    }

    /**
     * Get document language ID based on file extension
     */
    static getLanguageId(filePath: string): string {
        const ext = path.extname(filePath).toLowerCase();
        switch (ext) {
            case '.json':
                return 'json';
            case '.yaml':
            case '.yml':
                return 'yaml';
            case '.env':
                return 'dotenv';
            case '.ini':
                return 'ini';
            default:
                return 'plaintext';
        }
    }

    dispose(): void {
        this._onDidChange.dispose();
        this.cache.clear();
    }
}
