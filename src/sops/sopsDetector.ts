import * as vscode from 'vscode';
import { logger } from '../services/loggerService';
import { getErrorMessage } from '../utils/errorUtils';

/**
 * Detects if a file is SOPS-encrypted by checking for SOPS metadata
 */
export class SopsDetector {
    // Regex to match SOPS metadata fields (mac, lastmodified, version) in YAML/JSON
    private static readonly SOPS_METADATA_REGEX = /["']?(mac|lastmodified|version)["']?\s*:/;
    // Regex to match SOPS metadata fields in INI files (uses = instead of :)
    private static readonly SOPS_INI_METADATA_REGEX = /^(mac|lastmodified|version)\s*=/m;

    /**
     * Check if a file is SOPS-encrypted by looking for the sops metadata key.
     * SOPS-encrypted files always contain a "sops:" key with metadata including
     * mac, version, lastmodified, etc.
     */
    async isEncrypted(uri: vscode.Uri): Promise<boolean> {
        try {
            const content = await vscode.workspace.fs.readFile(uri);
            const text = Buffer.from(content).toString('utf-8');
            return this.isContentEncrypted(text);
        } catch (error) {
            logger.debug(`Failed to read file for encryption check ${uri.fsPath}: ${getErrorMessage(error)}`);
            return false;
        }
    }

    /**
     * Check if content string is SOPS-encrypted
     */
    isContentEncrypted(content: string): boolean {
        // Quick check: if no "sops" anywhere, not encrypted
        if (!content.includes('sops')) {
            return false;
        }

        // Check for SOPS metadata marker in various formats
        // YAML format: sops:
        // JSON format: "sops":
        // The sops key contains mac, version, and key information
        if (/["']?sops["']?\s*:/m.test(content)) {
            // Additional check: ensure it has expected SOPS metadata fields
            // to avoid false positives with files that just have a "sops" key
            // Using single regex instead of multiple includes() for performance
            return SopsDetector.SOPS_METADATA_REGEX.test(content);
        }

        // INI files have sops metadata in a [sops] section
        if (/^\[sops\]\s*$/m.test(content)) {
            return SopsDetector.SOPS_INI_METADATA_REGEX.test(content);
        }

        // ENV files have sops metadata as prefixed keys
        if (content.includes('sops_version=') || content.includes('sops_mac=')) {
            return true;
        }

        // Binary format detection (rarely used)
        if (content.startsWith('SOPS')) {
            return true;
        }

        return false;
    }

    /**
     * Check if a document is SOPS-encrypted
     */
    isDocumentEncrypted(document: vscode.TextDocument): boolean {
        return this.isContentEncrypted(document.getText());
    }
}
