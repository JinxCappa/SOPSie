/**
 * Utility functions for error handling
 */

/**
 * Extract a message string from an unknown error type.
 * Handles Error objects, strings, and other types safely.
 */
export function getErrorMessage(error: unknown): string {
    if (error instanceof Error) {
        return error.message;
    }
    return String(error);
}
