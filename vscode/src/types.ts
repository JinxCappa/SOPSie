/**
 * Key group for Shamir secret sharing in SOPS
 * (internal type - used by SopsCreationRule)
 */
interface KeyGroup {
    age?: string[];
    pgp?: string[];
    kms?: KmsKey[];
    gcp_kms?: GcpKmsKey[];
    azure_kv?: AzureKvKey[];
    hc_vault_transit?: string[];
}

interface KmsKey {
    arn: string;
    role?: string;
    context?: Record<string, string>;
    aws_profile?: string;
}

interface GcpKmsKey {
    resource_id: string;
}

interface AzureKvKey {
    vaultUrl: string;
    key: string;
    version: string;
}

/**
 * A single creation rule from .sops.yaml
 */
export interface SopsCreationRule {
    path_regex?: string;
    filename_regex?: string;
    encrypted_regex?: string;
    encrypted_suffix?: string;
    unencrypted_suffix?: string;
    age?: string;
    pgp?: string;
    kms?: string;
    gcp_kms?: string;
    azure_kv?: string;
    hc_vault_transit?: string;
    key_groups?: KeyGroup[];
    shamir_threshold?: number;
}

/**
 * Parsed .sops.yaml configuration
 */
export interface SopsConfig {
    creation_rules: SopsCreationRule[];
}

/**
 * File encryption state
 */
export enum FileEncryptionState {
    Unknown = 'unknown',
    Encrypted = 'encrypted',
    Decrypted = 'decrypted',
    PlainText = 'plaintext'
}

/**
 * Error types for better handling
 */
export enum SopsErrorType {
    CliNotFound = 'CLI_NOT_FOUND',
    ConfigParseError = 'CONFIG_PARSE_ERROR',
    ConfigNotFound = 'CONFIG_NOT_FOUND',
    DecryptionFailed = 'DECRYPTION_FAILED',
    EncryptionFailed = 'ENCRYPTION_FAILED',
    KeyAccessDenied = 'KEY_ACCESS_DENIED',
    InvalidFile = 'INVALID_FILE',
    Timeout = 'TIMEOUT',
    Unknown = 'UNKNOWN'
}

/**
 * Structured SOPS error
 */
export interface SopsError {
    type: SopsErrorType;
    message: string;
    details?: string;
    recoverable: boolean;
    suggestedAction?: string;
}

/**
 * Context keys used for when-clause conditions
 */
export const CONTEXT_KEYS = {
    IS_SOPS_FILE: 'sopsie.isSopsFile',
    IS_ENCRYPTED_FILE: 'sopsie.isEncryptedFile',
    IS_DECRYPTED_FILE: 'sopsie.isDecryptedFile',
    HAS_MATCHING_RULE: 'sopsie.hasMatchingRule',
    USE_EDIT_IN_PLACE: 'sopsie.useEditInPlace'
} as const;

/**
 * URI scheme for decrypted preview documents
 */
export const SOPS_DECRYPTED_SCHEME = 'sops-decrypted';
