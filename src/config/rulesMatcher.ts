import * as vscode from 'vscode';
import * as path from 'path';
import { SopsConfig, SopsCreationRule } from '../types';
import { logger } from '../services/loggerService';

// Cache for compiled regex patterns (null means invalid regex)
const regexCache = new Map<string, RegExp | null>();
const MAX_REGEX_CACHE_SIZE = 50;

/**
 * Clear the regex pattern cache.
 * Call this when configuration is reloaded to prevent stale patterns.
 */
export function clearRegexCache(): void {
    regexCache.clear();
}

/**
 * Get or create a cached regex from a pattern string.
 * Returns null for invalid patterns.
 * Uses LRU eviction when cache exceeds MAX_REGEX_CACHE_SIZE.
 */
function getCachedRegex(pattern: string): RegExp | null {
    const cached = regexCache.get(pattern);
    if (cached !== undefined) {
        return cached;
    }

    try {
        const regex = new RegExp(pattern);

        // Evict oldest entry if cache is full (Map iterates in insertion order)
        if (regexCache.size >= MAX_REGEX_CACHE_SIZE) {
            const firstKey = regexCache.keys().next().value;
            if (firstKey !== undefined) {
                regexCache.delete(firstKey);
            }
        }

        regexCache.set(pattern, regex);
        return regex;
    } catch {
        logger.warn(`Invalid regex pattern: ${pattern}`);
        regexCache.set(pattern, null);
        return null;
    }
}

/**
 * Matches files against SOPS creation rules
 */
export class RulesMatcher {
    constructor(
        private config: SopsConfig,
        private configDir: string
    ) {}

    /**
     * Find the first matching creation rule for a file
     * SOPS uses first-match semantics
     */
    findMatchingRule(fileUri: vscode.Uri): SopsCreationRule | null {
        const { normalizedPath, filename } = this.getNormalizedPaths(fileUri);

        for (const rule of this.config.creation_rules) {
            if (this.ruleMatches(rule, normalizedPath, filename)) {
                return rule;
            }
        }

        return null;
    }

    /**
     * Get normalized path components for a file URI
     */
    private getNormalizedPaths(fileUri: vscode.Uri): { normalizedPath: string; filename: string } {
        // SOPS matches against path relative to .sops.yaml location
        const relativePath = path.relative(this.configDir, fileUri.fsPath);
        // Normalize path separators for regex matching (always use forward slashes)
        const normalizedPath = relativePath.replace(/\\/g, '/');
        const filename = path.basename(fileUri.fsPath);
        return { normalizedPath, filename };
    }

    private ruleMatches(
        rule: SopsCreationRule,
        relativePath: string,
        filename: string
    ): boolean {
        // If rule has path_regex, test against relative path
        if (rule.path_regex) {
            const regex = getCachedRegex(rule.path_regex);
            return regex !== null && regex.test(relativePath);
        }

        // If rule has filename_regex, test against filename only
        if (rule.filename_regex) {
            const regex = getCachedRegex(rule.filename_regex);
            return regex !== null && regex.test(filename);
        }

        // If no regex specified, this is a catch-all rule (matches everything)
        // This is how SOPS behaves - a rule without path_regex or filename_regex matches all files
        return true;
    }
}
