import * as yaml from 'yaml';
import { SopsConfig, SopsCreationRule } from '../types';

/**
 * Parse .sops.yaml configuration file content
 */
export function parseConfig(content: string): SopsConfig {
    const parsed = yaml.parse(content);

    if (!parsed) {
        throw new Error('Empty or invalid YAML content');
    }

    if (!parsed.creation_rules) {
        throw new Error('Missing required "creation_rules" field');
    }

    if (!Array.isArray(parsed.creation_rules)) {
        throw new Error('"creation_rules" must be an array');
    }

    const creation_rules: SopsCreationRule[] = parsed.creation_rules.map(
        (rule: unknown, index: number) => validateRule(rule, index)
    );

    return { creation_rules };
}

/**
 * Validate a single creation rule
 */
function validateRule(rule: unknown, index: number): SopsCreationRule {
    if (typeof rule !== 'object' || rule === null) {
        throw new Error(`creation_rules[${index}] must be an object`);
    }

    const r = rule as Record<string, unknown>;

    // Validate regex patterns if present
    if (r.path_regex !== undefined && typeof r.path_regex !== 'string') {
        throw new Error(`creation_rules[${index}].path_regex must be a string`);
    }

    if (r.filename_regex !== undefined && typeof r.filename_regex !== 'string') {
        throw new Error(`creation_rules[${index}].filename_regex must be a string`);
    }

    // Validate regex compiles
    if (r.path_regex) {
        try {
            new RegExp(r.path_regex as string);
        } catch (e) {
            throw new Error(
                `creation_rules[${index}].path_regex is invalid: ${(e as Error).message}`
            );
        }
    }

    if (r.filename_regex) {
        try {
            new RegExp(r.filename_regex as string);
        } catch (e) {
            throw new Error(
                `creation_rules[${index}].filename_regex is invalid: ${(e as Error).message}`
            );
        }
    }

    // Return the rule with proper typing
    return {
        path_regex: r.path_regex as string | undefined,
        filename_regex: r.filename_regex as string | undefined,
        encrypted_regex: r.encrypted_regex as string | undefined,
        encrypted_suffix: r.encrypted_suffix as string | undefined,
        unencrypted_suffix: r.unencrypted_suffix as string | undefined,
        age: r.age as string | undefined,
        pgp: r.pgp as string | undefined,
        kms: r.kms as string | undefined,
        gcp_kms: r.gcp_kms as string | undefined,
        azure_kv: r.azure_kv as string | undefined,
        hc_vault_transit: r.hc_vault_transit as string | undefined,
        key_groups: r.key_groups as SopsCreationRule['key_groups'],
        shamir_threshold: r.shamir_threshold as number | undefined
    };
}
