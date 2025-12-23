package com.sopsie.model

import com.intellij.openapi.vfs.VirtualFile

/**
 * KMS key configuration
 */
data class KmsKey(
    val arn: String,
    val role: String? = null,
    val context: Map<String, String>? = null,
    val awsProfile: String? = null
)

/**
 * GCP KMS key configuration
 */
data class GcpKmsKey(
    val resourceId: String
)

/**
 * Azure Key Vault key configuration
 */
data class AzureKvKey(
    val vaultUrl: String,
    val key: String,
    val version: String
)

/**
 * Key group for Shamir secret sharing in SOPS
 */
data class KeyGroup(
    val age: List<String>? = null,
    val pgp: List<String>? = null,
    val kms: List<KmsKey>? = null,
    val gcpKms: List<GcpKmsKey>? = null,
    val azureKv: List<AzureKvKey>? = null,
    val hcVaultTransit: List<String>? = null
)

/**
 * A single creation rule from .sops.yaml
 */
data class SopsCreationRule(
    val pathRegex: String? = null,
    val filenameRegex: String? = null,
    val encryptedRegex: String? = null,
    val encryptedSuffix: String? = null,
    val unencryptedSuffix: String? = null,
    val age: String? = null,
    val pgp: String? = null,
    val kms: String? = null,
    val gcpKms: String? = null,
    val azureKv: String? = null,
    val hcVaultTransit: String? = null,
    val keyGroups: List<KeyGroup>? = null,
    val shamirThreshold: Int? = null
)

/**
 * Parsed .sops.yaml configuration
 */
data class SopsConfig(
    val creationRules: List<SopsCreationRule>,
    val configFile: VirtualFile? = null
)
