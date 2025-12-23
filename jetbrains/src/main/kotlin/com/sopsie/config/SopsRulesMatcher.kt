package com.sopsie.config

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.sopsie.model.SopsConfig
import com.sopsie.model.SopsCreationRule
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Matches files against SOPS creation rules.
 * Uses first-match semantics like the SOPS CLI.
 */
class SopsRulesMatcher(
    private val config: SopsConfig,
    private val configDir: Path
) {
    companion object {
        private val LOG = Logger.getInstance(SopsRulesMatcher::class.java)
        private const val MAX_REGEX_CACHE_SIZE = 50

        // Shared regex cache with LRU eviction
        private val regexCache = LinkedHashMap<String, Regex?>(16, 0.75f, true)

        /**
         * Clear the regex cache. Call this when configuration is reloaded.
         */
        fun clearRegexCache() {
            synchronized(regexCache) {
                regexCache.clear()
            }
        }
    }

    /**
     * Find the first matching creation rule for a file.
     * SOPS uses first-match semantics.
     *
     * @param file The file to match against creation rules
     * @return The first matching rule, or null if no rules match
     */
    fun findMatchingRule(file: VirtualFile): SopsCreationRule? {
        val (normalizedPath, filename) = getNormalizedPaths(file)

        for (rule in config.creationRules) {
            if (ruleMatches(rule, normalizedPath, filename)) {
                return rule
            }
        }

        return null
    }

    /**
     * Check if a file matches any creation rule.
     */
    fun hasMatchingRule(file: VirtualFile): Boolean {
        return findMatchingRule(file) != null
    }

    /**
     * Get normalized path components for a file.
     * Returns relative path (with forward slashes) and filename.
     */
    private fun getNormalizedPaths(file: VirtualFile): Pair<String, String> {
        // SOPS matches against path relative to .sops.yaml location
        val filePath = Paths.get(file.path)
        val relativePath = try {
            configDir.relativize(filePath).toString()
        } catch (e: IllegalArgumentException) {
            // Can't relativize (different roots), use the full path
            file.path
        }

        // Normalize path separators for regex matching (always use forward slashes)
        val normalizedPath = relativePath.replace('\\', '/')
        val filename = file.name

        return Pair(normalizedPath, filename)
    }

    /**
     * Check if a rule matches the given path and filename.
     */
    private fun ruleMatches(
        rule: SopsCreationRule,
        relativePath: String,
        filename: String
    ): Boolean {
        // If rule has path_regex, test against relative path
        if (rule.pathRegex != null) {
            val regex = getCachedRegex(rule.pathRegex)
            return regex != null && regex.containsMatchIn(relativePath)
        }

        // If rule has filename_regex, test against filename only
        if (rule.filenameRegex != null) {
            val regex = getCachedRegex(rule.filenameRegex)
            return regex != null && regex.containsMatchIn(filename)
        }

        // If no regex specified, this is a catch-all rule (matches everything)
        // This is how SOPS behaves - a rule without path_regex or filename_regex matches all files
        return true
    }

    /**
     * Get or create a cached regex from a pattern string.
     * Returns null for invalid patterns.
     * Uses LRU eviction when cache exceeds MAX_REGEX_CACHE_SIZE.
     */
    private fun getCachedRegex(pattern: String): Regex? {
        synchronized(regexCache) {
            // Check if already cached (including null for invalid patterns)
            if (regexCache.containsKey(pattern)) {
                return regexCache[pattern]
            }

            // Create and cache the regex
            val regex = try {
                Regex(pattern)
            } catch (e: Exception) {
                LOG.warn("Invalid regex pattern: $pattern")
                null
            }

            // Evict oldest entry if cache is full
            if (regexCache.size >= MAX_REGEX_CACHE_SIZE) {
                val firstKey = regexCache.keys.firstOrNull()
                if (firstKey != null) {
                    regexCache.remove(firstKey)
                }
            }

            regexCache[pattern] = regex
            return regex
        }
    }
}
