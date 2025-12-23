package com.sopsie.detection

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import java.nio.charset.StandardCharsets

private val LOG = logger<SopsDetector>()

/**
 * Detects if a file is SOPS-encrypted by checking for SOPS metadata
 */
@Service(Service.Level.APP)
class SopsDetector {

    companion object {
        // Regex to match SOPS metadata fields (mac, lastmodified, version) in YAML/JSON
        private val SOPS_METADATA_REGEX = Regex("""["']?(mac|lastmodified|version)["']?\s*:""")

        // Regex to match SOPS metadata fields in INI files (uses = instead of :)
        private val SOPS_INI_METADATA_REGEX = Regex("""^(mac|lastmodified|version)\s*=""", RegexOption.MULTILINE)

        // Regex to match SOPS key in YAML/JSON
        private val SOPS_KEY_REGEX = Regex("""["']?sops["']?\s*:""", RegexOption.MULTILINE)

        // Regex to match INI [sops] section
        private val SOPS_INI_SECTION_REGEX = Regex("""^\[sops\]\s*$""", RegexOption.MULTILINE)

        @JvmStatic
        fun getInstance(): SopsDetector = service()
    }

    /**
     * Check if a file is SOPS-encrypted by looking for the sops metadata key.
     * SOPS-encrypted files always contain a "sops:" key with metadata including
     * mac, version, lastmodified, etc.
     */
    fun isEncrypted(file: VirtualFile): Boolean {
        return try {
            val content = String(file.contentsToByteArray(), StandardCharsets.UTF_8)
            isContentEncrypted(content)
        } catch (e: Exception) {
            LOG.debug("Failed to read file for encryption check ${file.path}: ${e.message}")
            false
        }
    }

    /**
     * Check if content string is SOPS-encrypted
     */
    fun isContentEncrypted(content: String): Boolean {
        // Quick check: if no "sops" anywhere, not encrypted
        if (!content.contains("sops")) {
            return false
        }

        // Check for SOPS metadata marker in various formats
        // YAML format: sops:
        // JSON format: "sops":
        // The sops key contains mac, version, and key information
        if (SOPS_KEY_REGEX.containsMatchIn(content)) {
            // Additional check: ensure it has expected SOPS metadata fields
            // to avoid false positives with files that just have a "sops" key
            return SOPS_METADATA_REGEX.containsMatchIn(content)
        }

        // INI files have sops metadata in a [sops] section
        if (SOPS_INI_SECTION_REGEX.containsMatchIn(content)) {
            return SOPS_INI_METADATA_REGEX.containsMatchIn(content)
        }

        // ENV files have sops metadata as prefixed keys
        if (content.contains("sops_version=") || content.contains("sops_mac=")) {
            return true
        }

        // Binary format detection (rarely used)
        if (content.startsWith("SOPS")) {
            return true
        }

        return false
    }

    /**
     * Check if a file at the given path is SOPS-encrypted
     */
    fun isPathEncrypted(filePath: String): Boolean {
        return try {
            val content = java.io.File(filePath).readText(StandardCharsets.UTF_8)
            isContentEncrypted(content)
        } catch (e: Exception) {
            LOG.debug("Failed to read file for encryption check $filePath: ${e.message}")
            false
        }
    }
}
