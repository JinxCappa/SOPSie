package com.sopsie.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.sopsie.detection.SopsDetector
import com.sopsie.handlers.DecryptedEditorHandler
import com.sopsie.handlers.ShowDecryptedOptions

private val LOG = logger<ShowDecryptedPreviewAction>()

/**
 * Action to open a read-only preview of the decrypted content.
 * Opens in a side panel without modifying the original file.
 */
class ShowDecryptedPreviewAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project

        // Enable only for SOPS-encrypted local files
        val enabled = project != null &&
                file != null &&
                file.isInLocalFileSystem &&
                !file.isDirectory &&
                SopsDetector.getInstance().isEncrypted(file)

        e.presentation.isEnabledAndVisible = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: run {
            showWarning(project, "No file selected")
            return
        }

        if (!file.isInLocalFileSystem) {
            showWarning(project, "Can only preview local files")
            return
        }

        if (!SopsDetector.getInstance().isEncrypted(file)) {
            showWarning(project, "File is not SOPS-encrypted")
            return
        }

        LOG.debug("ShowDecryptedPreviewAction: Opening preview for ${file.path}")

        DecryptedEditorHandler.getInstance(project).openPreview(
            file,
            ShowDecryptedOptions(
                preserveFocus = false,
                showInfoMessage = false
            )
        )
    }

    private fun showWarning(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sopsie.Notifications")
                .createNotification(message, NotificationType.WARNING)
                .notify(project)
        }
    }
}
