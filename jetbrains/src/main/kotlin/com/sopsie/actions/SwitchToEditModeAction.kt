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
import com.sopsie.handlers.DecryptedEditorHandler

private val LOG = logger<SwitchToEditModeAction>()

/**
 * Action to switch from a read-only preview to an editable temp file.
 * Only available when viewing a .sops-preview temp file.
 */
class SwitchToEditModeAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project

        // Enable only for preview files
        val enabled = project != null &&
                file != null &&
                DecryptedEditorHandler.getInstance(project).isPreviewFile(file)

        e.presentation.isEnabledAndVisible = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: run {
            showWarning(project, "No file selected")
            return
        }

        val handler = DecryptedEditorHandler.getInstance(project)

        if (!handler.isPreviewFile(file)) {
            showWarning(project, "This command only works on decrypted preview tabs")
            return
        }

        LOG.debug("SwitchToEditModeAction: Switching to edit mode for ${file.path}")

        handler.switchToEditMode(file)
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
