package com.sopsie.model

/**
 * Types of SOPS errors for categorized handling
 */
enum class SopsErrorType {
    /** SOPS CLI executable not found */
    CLI_NOT_FOUND,

    /** Failed to parse .sops.yaml configuration */
    CONFIG_PARSE_ERROR,

    /** No .sops.yaml configuration file found */
    CONFIG_NOT_FOUND,

    /** Decryption operation failed */
    DECRYPTION_FAILED,

    /** Encryption operation failed */
    ENCRYPTION_FAILED,

    /** Access denied to encryption key */
    KEY_ACCESS_DENIED,

    /** Invalid file format */
    INVALID_FILE,

    /** Operation timed out */
    TIMEOUT,

    /** Unknown error */
    UNKNOWN
}

/**
 * Structured SOPS error with context and recovery information
 */
data class SopsError(
    val type: SopsErrorType,
    val message: String,
    val details: String? = null,
    val recoverable: Boolean = type != SopsErrorType.CLI_NOT_FOUND,
    val suggestedAction: String? = null
) {
    companion object {
        fun cliNotFound(cliPath: String) = SopsError(
            type = SopsErrorType.CLI_NOT_FOUND,
            message = "SOPS CLI not found at \"$cliPath\"",
            suggestedAction = "Install SOPS or update the sopsPath setting"
        )

        fun timeout(timeoutMs: Int) = SopsError(
            type = SopsErrorType.TIMEOUT,
            message = "Operation timed out after ${timeoutMs}ms"
        )

        fun keyAccessDenied(details: String) = SopsError(
            type = SopsErrorType.KEY_ACCESS_DENIED,
            message = "Unable to decrypt file - key access denied",
            details = details,
            suggestedAction = "Check your encryption key configuration (age, GPG, KMS, etc.)"
        )

        fun configNotFound(details: String) = SopsError(
            type = SopsErrorType.CONFIG_NOT_FOUND,
            message = "No .sops.yaml configuration found",
            details = details,
            suggestedAction = "Create a .sops.yaml file in your workspace root"
        )

        fun invalidFile(details: String) = SopsError(
            type = SopsErrorType.INVALID_FILE,
            message = "Invalid file format",
            details = details,
            suggestedAction = "Ensure the file is valid YAML/JSON"
        )

        fun encryptionFailed(details: String) = SopsError(
            type = SopsErrorType.ENCRYPTION_FAILED,
            message = "Encryption failed",
            details = details
        )

        fun decryptionFailed(details: String) = SopsError(
            type = SopsErrorType.DECRYPTION_FAILED,
            message = "Decryption failed",
            details = details
        )

        fun unknown(details: String, code: Int?) = SopsError(
            type = SopsErrorType.UNKNOWN,
            message = "SOPS failed with code $code",
            details = details
        )
    }
}

/**
 * Exception wrapper for SopsError
 */
class SopsException(val error: SopsError) : Exception(error.message)
