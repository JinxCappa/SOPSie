package com.sopsie.config

import com.sopsie.model.*
import org.yaml.snakeyaml.Yaml
import java.util.regex.PatternSyntaxException

/**
 * Parser for .sops.yaml configuration files using SnakeYAML
 */
object SopsConfigParser {

    /**
     * Parse .sops.yaml content and return a SopsConfig
     * @throws SopsConfigParseException if the content is invalid
     */
    fun parse(content: String): SopsConfig {
        val yaml = Yaml()
        val parsed: Map<String, Any>? = try {
            @Suppress("UNCHECKED_CAST")
            yaml.load(content) as? Map<String, Any>
        } catch (e: Exception) {
            throw SopsConfigParseException("Invalid YAML content: ${e.message}", e)
        }

        if (parsed == null) {
            throw SopsConfigParseException("Empty or invalid YAML content")
        }

        val creationRulesRaw = parsed["creation_rules"]
            ?: throw SopsConfigParseException("Missing required \"creation_rules\" field")

        if (creationRulesRaw !is List<*>) {
            throw SopsConfigParseException("\"creation_rules\" must be an array")
        }

        val creationRules = creationRulesRaw.mapIndexed { index, rule ->
            parseRule(rule, index)
        }

        return SopsConfig(creationRules = creationRules)
    }

    private fun parseRule(rule: Any?, index: Int): SopsCreationRule {
        if (rule == null || rule !is Map<*, *>) {
            throw SopsConfigParseException("creation_rules[$index] must be an object")
        }

        @Suppress("UNCHECKED_CAST")
        val r = rule as Map<String, Any?>

        // Validate and compile regex patterns
        val pathRegex = r["path_regex"]?.toString()
        val filenameRegex = r["filename_regex"]?.toString()

        if (pathRegex != null) {
            validateRegex(pathRegex, "creation_rules[$index].path_regex")
        }

        if (filenameRegex != null) {
            validateRegex(filenameRegex, "creation_rules[$index].filename_regex")
        }

        return SopsCreationRule(
            pathRegex = pathRegex,
            filenameRegex = filenameRegex,
            encryptedRegex = r["encrypted_regex"]?.toString(),
            encryptedSuffix = r["encrypted_suffix"]?.toString(),
            unencryptedSuffix = r["unencrypted_suffix"]?.toString(),
            age = r["age"]?.toString(),
            pgp = r["pgp"]?.toString(),
            kms = r["kms"]?.toString(),
            gcpKms = r["gcp_kms"]?.toString(),
            azureKv = r["azure_kv"]?.toString(),
            hcVaultTransit = r["hc_vault_transit"]?.toString(),
            keyGroups = parseKeyGroups(r["key_groups"], index),
            shamirThreshold = (r["shamir_threshold"] as? Number)?.toInt()
        )
    }

    private fun parseKeyGroups(keyGroupsRaw: Any?, ruleIndex: Int): List<KeyGroup>? {
        if (keyGroupsRaw == null) return null
        if (keyGroupsRaw !is List<*>) {
            throw SopsConfigParseException("creation_rules[$ruleIndex].key_groups must be an array")
        }

        return keyGroupsRaw.mapIndexed { groupIndex, group ->
            parseKeyGroup(group, ruleIndex, groupIndex)
        }
    }

    private fun parseKeyGroup(group: Any?, ruleIndex: Int, groupIndex: Int): KeyGroup {
        if (group == null || group !is Map<*, *>) {
            throw SopsConfigParseException("creation_rules[$ruleIndex].key_groups[$groupIndex] must be an object")
        }

        @Suppress("UNCHECKED_CAST")
        val g = group as Map<String, Any?>

        return KeyGroup(
            age = parseStringList(g["age"]),
            pgp = parseStringList(g["pgp"]),
            kms = parseKmsKeys(g["kms"]),
            gcpKms = parseGcpKmsKeys(g["gcp_kms"]),
            azureKv = parseAzureKvKeys(g["azure_kv"]),
            hcVaultTransit = parseStringList(g["hc_vault_transit"])
        )
    }

    private fun parseStringList(value: Any?): List<String>? {
        if (value == null) return null
        if (value !is List<*>) return listOf(value.toString())
        return value.map { it.toString() }
    }

    private fun parseKmsKeys(value: Any?): List<KmsKey>? {
        if (value == null) return null
        if (value !is List<*>) return null

        return value.mapNotNull { item ->
            when (item) {
                is String -> KmsKey(arn = item)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val m = item as Map<String, Any?>
                    KmsKey(
                        arn = m["arn"]?.toString() ?: return@mapNotNull null,
                        role = m["role"]?.toString(),
                        context = parseStringMap(m["context"]),
                        awsProfile = m["aws_profile"]?.toString()
                    )
                }
                else -> null
            }
        }
    }

    private fun parseGcpKmsKeys(value: Any?): List<GcpKmsKey>? {
        if (value == null) return null
        if (value !is List<*>) return null

        return value.mapNotNull { item ->
            when (item) {
                is String -> GcpKmsKey(resourceId = item)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val m = item as Map<String, Any?>
                    GcpKmsKey(resourceId = m["resource_id"]?.toString() ?: return@mapNotNull null)
                }
                else -> null
            }
        }
    }

    private fun parseAzureKvKeys(value: Any?): List<AzureKvKey>? {
        if (value == null) return null
        if (value !is List<*>) return null

        return value.mapNotNull { item ->
            if (item !is Map<*, *>) return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val m = item as Map<String, Any?>
            AzureKvKey(
                vaultUrl = m["vaultUrl"]?.toString() ?: return@mapNotNull null,
                key = m["key"]?.toString() ?: return@mapNotNull null,
                version = m["version"]?.toString() ?: return@mapNotNull null
            )
        }
    }

    private fun parseStringMap(value: Any?): Map<String, String>? {
        if (value == null) return null
        if (value !is Map<*, *>) return null
        return value.entries
            .filter { it.key != null && it.value != null }
            .associate { it.key.toString() to it.value.toString() }
    }

    private fun validateRegex(pattern: String, field: String) {
        try {
            Regex(pattern)
        } catch (e: PatternSyntaxException) {
            throw SopsConfigParseException("$field is invalid: ${e.message}")
        }
    }
}

/**
 * Exception thrown when parsing .sops.yaml fails
 */
class SopsConfigParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
