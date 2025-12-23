import * as vscode from 'vscode';

/**
 * Tracks the decryption state of files.
 * Files are marked as decrypted when they've been decrypted in-place
 * and need to be re-encrypted on save.
 */
export class FileStateTracker {
    private decryptedFiles = new Set<string>();

    /**
     * Mark a file as decrypted
     */
    markDecrypted(uri: vscode.Uri): void {
        this.decryptedFiles.add(uri.toString());
    }

    /**
     * Mark a file as encrypted (remove from decrypted set)
     */
    markEncrypted(uri: vscode.Uri): void {
        this.decryptedFiles.delete(uri.toString());
    }

    /**
     * Check if a file is marked as decrypted
     */
    isMarkedDecrypted(uri: vscode.Uri): boolean {
        return this.decryptedFiles.has(uri.toString());
    }

    /**
     * Clear tracking for a specific file
     */
    clearFile(uri: vscode.Uri): void {
        this.decryptedFiles.delete(uri.toString());
    }

    /**
     * Clear all tracked files
     */
    clear(): void {
        this.decryptedFiles.clear();
    }
}
