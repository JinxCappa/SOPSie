import { spawn } from 'child_process';
import * as path from 'path';
import * as fs from 'fs';
import { SopsError, SopsErrorType } from '../types';
import { SettingsService } from '../services/settingsService';
import { logger } from '../services/loggerService';
import { getErrorMessage } from '../utils/errorUtils';

/**
 * Wrapper for SOPS CLI operations
 */
export class SopsRunner {
    constructor(private settingsService: SettingsService) {}

    private getWorkingDirectory(filePath: string): string {
        // SOPS looks for .sops.yaml from CWD and walks up the directory tree
        return path.dirname(filePath);
    }

    /**
     * Decrypt a file and return the decrypted content.
     * Automatically detects file type from extension.
     */
    async decrypt(filePath: string): Promise<string> {
        const ext = path.extname(filePath).slice(1);
        const fileType = this.getInputType(ext);
        logger.debug(`SopsRunner: Decrypting ${filePath} (type=${fileType})`);

        // For known structured formats, let SOPS handle it naturally
        // For binary/unknown formats, explicitly specify the type
        if (fileType === 'binary') {
            return this.runSops([
                '--decrypt',
                '--input-type', 'binary',
                '--output-type', 'binary',
                filePath
            ], filePath);
        }
        return this.runSops(['--decrypt', filePath], filePath);
    }

    /**
     * Encrypt a file and return the encrypted content.
     * Automatically detects file type from extension.
     */
    async encrypt(filePath: string): Promise<string> {
        const ext = path.extname(filePath).slice(1);
        const fileType = this.getInputType(ext);
        logger.debug(`SopsRunner: Encrypting ${filePath} (type=${fileType})`);

        // For known structured formats, let SOPS handle it naturally
        // For binary/unknown formats, explicitly specify the type
        if (fileType === 'binary') {
            return this.runSops([
                '--encrypt',
                '--input-type', 'binary',
                '--output-type', 'binary',
                filePath
            ], filePath);
        }
        return this.runSops(['--encrypt', filePath], filePath);
    }

    /**
     * Encrypt content from stdin with filename override for rule matching.
     * Used for encrypting in-memory content without writing to disk first.
     * 
     * Note: Uses a temp file approach because SOPS stdin handling is unreliable
     * across platforms (Windows doesn't support `-` or `/dev/stdin`).
     */
    async encryptContent(content: string, filePath: string): Promise<string> {
        const ext = path.extname(filePath);
        const dir = path.dirname(filePath);
        const inputType = this.getInputType(ext.slice(1));
        logger.debug(`SopsRunner: Encrypting content for ${filePath} (type=${inputType})`);

        // Create a temp file in the same directory so .sops.yaml rules match
        // Use same extension so SOPS auto-detects the format
        const tempFileName = `.sopsie-temp-${Date.now()}-${Math.random().toString(36).slice(2)}${ext}`;
        const tempFilePath = path.join(dir, tempFileName);

        try {
            // Write content to temp file
            fs.writeFileSync(tempFilePath, content, 'utf8');

            // Encrypt the temp file
            const result = await this.runSops(['--encrypt', tempFilePath], tempFilePath);

            return result;
        } finally {
            // Always clean up temp file
            try {
                if (fs.existsSync(tempFilePath)) {
                    fs.unlinkSync(tempFilePath);
                }
            } catch (cleanupError) {
                logger.debug(`Failed to clean up temp file: ${getErrorMessage(cleanupError)}`);
            }
        }
    }

    /**
     * Update keys in an encrypted file based on .sops.yaml.
     * Re-encrypts the file with the keys defined in the matching creation rule.
     */
    async updateKeys(filePath: string): Promise<void> {
        logger.debug(`SopsRunner: Updating keys for ${filePath}`);
        await this.runSops(['updatekeys', '--yes', filePath], filePath);
    }

    /**
     * Rotate the data key used to encrypt the file.
     * Decrypts and re-encrypts all values with a new data key.
     */
    async rotate(filePath: string): Promise<void> {
        logger.debug(`SopsRunner: Rotating data key for ${filePath}`);
        await this.runSops(['rotate', '--in-place', filePath], filePath);
    }

    /**
     * Check if SOPS CLI is available
     */
    async checkCliAvailable(): Promise<boolean> {
        try {
            const sopsPath = this.settingsService.getSopsPath();
            await this.runCommand(sopsPath, ['--version'], process.cwd(), '', 5000);
            return true;
        } catch (error) {
            logger.debug(`SOPS CLI check failed: ${getErrorMessage(error)}`);
            return false;
        }
    }

    /**
     * Get SOPS version
     */
    async getVersion(): Promise<string | null> {
        try {
            const sopsPath = this.settingsService.getSopsPath();
            const result = await this.runCommand(sopsPath, ['--version'], process.cwd(), '', 5000);
            return result.trim();
        } catch (error) {
            logger.debug(`Failed to get SOPS version: ${getErrorMessage(error)}`);
            return null;
        }
    }

    private getInputType(ext: string): string {
        switch (ext.toLowerCase()) {
            case 'json':
                return 'json';
            case 'env':
                return 'dotenv';
            case 'ini':
                return 'ini';
            case 'yaml':
            case 'yml':
                return 'yaml';
            default:
                // Unknown extensions use binary format
                return 'binary';
        }
    }

    private async runSops(args: string[], filePath: string): Promise<string> {
        const sopsPath = this.settingsService.getSopsPath();
        const timeout = this.settingsService.getTimeout();
        const cwd = this.getWorkingDirectory(filePath);
        return this.runCommand(sopsPath, args, cwd, '', timeout);
    }

    private runCommand(
        cmd: string,
        args: string[],
        cwd: string,
        stdin: string,
        timeout: number
    ): Promise<string> {
        return new Promise((resolve, reject) => {
            // Disable SOPS version check to suppress deprecation warning
            const env = { ...process.env, SOPS_DISABLE_VERSION_CHECK: '1' };
            const proc = spawn(cmd, args, { cwd, env });
            let isSettled = false;

            let stdout = '';
            let stderr = '';

            // Helper to ensure process is cleaned up
            const killProcess = (): void => {
                if (!proc.killed) {
                    proc.kill('SIGTERM');
                    // Give it a moment, then force kill if still running
                    setTimeout(() => {
                        if (!proc.killed) {
                            proc.kill('SIGKILL');
                        }
                    }, 1000);
                }
            };

            const timer = setTimeout(() => {
                if (!isSettled) {
                    isSettled = true;
                    killProcess();
                    reject(this.createError(SopsErrorType.Timeout, `Operation timed out after ${timeout}ms`));
                }
            }, timeout);

            proc.stdout.on('data', (data: Buffer) => {
                stdout += data.toString();
            });

            proc.stderr.on('data', (data: Buffer) => {
                stderr += data.toString();
            });

            if (stdin) {
                proc.stdin.write(stdin);
                proc.stdin.end();
            }

            proc.on('close', (code: number | null) => {
                clearTimeout(timer);
                if (!isSettled) {
                    isSettled = true;
                    if (code === 0) {
                        resolve(stdout);
                    } else {
                        reject(this.parseError(stderr, code));
                    }
                }
            });

            proc.on('error', (err: Error) => {
                clearTimeout(timer);
                if (!isSettled) {
                    isSettled = true;
                    killProcess();
                    if ((err as NodeJS.ErrnoException).code === 'ENOENT') {
                        reject(
                            this.createError(
                                SopsErrorType.CliNotFound,
                                `SOPS CLI not found at "${cmd}"`,
                                'Install SOPS or update the sopsPath setting'
                            )
                        );
                    } else {
                        reject(
                            this.createError(SopsErrorType.Unknown, `Failed to run SOPS: ${err.message}`)
                        );
                    }
                }
            });
        });
    }

    private parseError(stderr: string, code: number | null): SopsError {
        const lowerStderr = stderr.toLowerCase();

        if (
            lowerStderr.includes('could not decrypt') ||
            lowerStderr.includes('failed to get the data key') ||
            lowerStderr.includes('cannot find key')
        ) {
            return this.createError(
                SopsErrorType.KeyAccessDenied,
                'Unable to decrypt file - key access denied',
                stderr,
                'Check your encryption key configuration (age, GPG, KMS, etc.)'
            );
        }

        if (lowerStderr.includes('config file not found') || lowerStderr.includes('.sops.yaml')) {
            return this.createError(
                SopsErrorType.ConfigNotFound,
                'No .sops.yaml configuration found',
                stderr,
                'Create a .sops.yaml file in your workspace root'
            );
        }

        if (lowerStderr.includes('error parsing') || lowerStderr.includes('yaml:')) {
            return this.createError(
                SopsErrorType.InvalidFile,
                'Invalid file format',
                stderr,
                'Ensure the file is valid YAML/JSON'
            );
        }

        if (stderr.includes('encrypt')) {
            return this.createError(SopsErrorType.EncryptionFailed, 'Encryption failed', stderr);
        }

        if (stderr.includes('decrypt')) {
            return this.createError(SopsErrorType.DecryptionFailed, 'Decryption failed', stderr);
        }

        return this.createError(
            SopsErrorType.Unknown,
            `SOPS failed with code ${code}`,
            stderr
        );
    }

    private createError(
        type: SopsErrorType,
        message: string,
        details?: string,
        suggestedAction?: string
    ): SopsError {
        return {
            type,
            message,
            details,
            recoverable: type !== SopsErrorType.CliNotFound,
            suggestedAction
        };
    }
}
