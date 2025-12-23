package com.sopsie.model

/**
 * Represents the encryption state of a file
 */
enum class FileEncryptionState {
    /** State is not yet determined */
    UNKNOWN,

    /** File is SOPS-encrypted */
    ENCRYPTED,

    /** File has been decrypted (was encrypted, now plaintext) */
    DECRYPTED,

    /** File matches a SOPS rule but is not encrypted */
    PLAINTEXT
}
