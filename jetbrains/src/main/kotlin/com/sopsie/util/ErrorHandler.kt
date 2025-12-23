package com.sopsie.util

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.sopsie.model.SopsError
import com.sopsie.model.SopsErrorType
import com.sopsie.model.SopsException

/**
 * User-friendly error handling and notification utilities.
 */
object ErrorHandler {

    /**
     * Show a user-friendly error notification for a SopsException
     */
    fun showError(project: Project?, exception: SopsException) {
        showError(project, exception.error)
    }

    /**
     * Show a user-friendly error notification for a SopsError
     */
    fun showError(project: Project?, error: SopsError) {
        val title = getTitleForError(error)
        val content = buildErrorContent(error)

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Sopsie.Notifications")
            .createNotification(title, content, NotificationType.ERROR)

        // Add suggested action if available
        when (error.type) {
            SopsErrorType.CLI_NOT_FOUND -> {
                notification.addAction(NotificationAction.createSimple("Open Settings") {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, "SOPSie")
                })
            }
            SopsErrorType.CONFIG_NOT_FOUND -> {
                notification.addAction(NotificationAction.createSimple("Learn More") {
                    com.intellij.ide.BrowserUtil.browse("https://github.com/getsops/sops#configuration")
                })
            }
            SopsErrorType.KEY_ACCESS_DENIED -> {
                notification.addAction(NotificationAction.createSimple("Troubleshoot") {
                    com.intellij.ide.BrowserUtil.browse("https://github.com/getsops/sops#troubleshooting")
                })
            }
            else -> {}
        }

        notification.notify(project)
    }

    /**
     * Show an error notification for a generic exception
     */
    fun showError(project: Project?, title: String, message: String?, details: String? = null) {
        val content = buildString {
            if (message != null) append(message)
            if (details != null) {
                if (isNotEmpty()) append("\n\n")
                append(details)
            }
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Sopsie.Notifications")
            .createNotification(title, content, NotificationType.ERROR)
            .notify(project)
    }

    /**
     * Show an info notification
     */
    fun showInfo(project: Project?, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Sopsie.Notifications")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }

    /**
     * Show a warning notification
     */
    fun showWarning(project: Project?, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Sopsie.Notifications")
            .createNotification(message, NotificationType.WARNING)
            .notify(project)
    }

    /**
     * Get a user-friendly title for the error type
     */
    private fun getTitleForError(error: SopsError): String {
        return when (error.type) {
            SopsErrorType.CLI_NOT_FOUND -> "SOPS Not Found"
            SopsErrorType.CONFIG_PARSE_ERROR -> "Configuration Error"
            SopsErrorType.CONFIG_NOT_FOUND -> "Configuration Missing"
            SopsErrorType.DECRYPTION_FAILED -> "Decryption Failed"
            SopsErrorType.ENCRYPTION_FAILED -> "Encryption Failed"
            SopsErrorType.KEY_ACCESS_DENIED -> "Key Access Denied"
            SopsErrorType.INVALID_FILE -> "Invalid File"
            SopsErrorType.TIMEOUT -> "Operation Timed Out"
            SopsErrorType.UNKNOWN -> "SOPS Error"
        }
    }

    /**
     * Build the error content with message, details, and suggested action
     */
    private fun buildErrorContent(error: SopsError): String {
        return buildString {
            append(error.message)

            if (error.details != null) {
                append("\n\n")
                // Truncate long details
                val details = if (error.details.length > 500) {
                    error.details.take(500) + "..."
                } else {
                    error.details
                }
                append(details)
            }

            if (error.suggestedAction != null) {
                append("\n\n")
                append("💡 ${error.suggestedAction}")
            }
        }
    }

    /**
     * Get a user-friendly message for common exceptions
     */
    fun getUserFriendlyMessage(exception: Throwable): String {
        return when (exception) {
            is SopsException -> exception.error.message
            is java.io.IOException -> "File operation failed: ${exception.message}"
            is SecurityException -> "Permission denied: ${exception.message}"
            is InterruptedException -> "Operation was cancelled"
            else -> exception.message ?: "An unexpected error occurred"
        }
    }

    /**
     * Wrap an action with error handling
     */
    inline fun <T> withErrorHandling(project: Project?, title: String, action: () -> T): T? {
        return try {
            action()
        } catch (e: SopsException) {
            showError(project, e)
            null
        } catch (e: Exception) {
            showError(project, title, getUserFriendlyMessage(e))
            null
        }
    }
}
