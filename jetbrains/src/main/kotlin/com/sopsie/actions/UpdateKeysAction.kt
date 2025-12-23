package com.sopsie.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.sopsie.detection.SopsDetector
import com.sopsie.execution.SopsRunner
import com.sopsie.model.SopsException
import com.sopsie.services.SopsSettingsService

private val LOG = logger<UpdateKeysAction>()

/**
 * Action to update keys for an encrypted file.
 * Re-encrypts the file with keys defined in .sops.yaml,
 * which may change who can access the file.
 */
class UpdateKeysAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project

        // Enable only for local files that are encrypted
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
            showWarning(project, "Can only update keys for local files")
            return
        }

        if (!SopsDetector.getInstance().isEncrypted(file)) {
            showInfo(project, "File is not SOPS-encrypted. Update keys only works on encrypted files.")
            return
        }

        // Confirm update if setting is enabled
        if (SopsSettingsService.getInstance().shouldConfirmUpdateKeys) {
            val result = Messages.showOkCancelDialog(
                project,
                "Update SOPS keys? This will re-encrypt the file with keys from .sops.yaml, which may change who can access this file.",
                "Update SOPS Keys",
                "Update Keys",
                "Cancel",
                Messages.getWarningIcon()
            )

            if (result != Messages.OK) {
                LOG.debug("UpdateKeysAction: User cancelled")
                return
            }
        }

        LOG.debug("UpdateKeysAction: Starting key update for ${file.path}")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Updating SOPS keys...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Save any unsaved changes first
                    ApplicationManager.getApplication().invokeAndWait {
                        FileDocumentManager.getInstance().saveAllDocuments()
                    }

                    // Update the keys
                    SopsRunner.getInstance().updateKeys(file.path)

                    // Reload the file in the editor
                    ApplicationManager.getApplication().invokeLater {
                        reloadFileInEditor(project, file)
                    }

                    LOG.debug("UpdateKeysAction: Successfully updated keys for ${file.path}")
                    showInfo(project, "SOPS keys updated successfully")
                } catch (ex: SopsException) {
                    LOG.warn("UpdateKeysAction: Failed to update keys for ${file.path}", ex)
                    showError(project, ex.error.message, ex.error.details)
                } catch (ex: Exception) {
                    LOG.warn("UpdateKeysAction: Unexpected error updating keys for ${file.path}", ex)
                    showError(project, "Key update failed", ex.message)
                }
            }
        })
    }

    /**
     * Reload the file in the editor after in-place modification by SOPS CLI.
     */
    private fun reloadFileInEditor(@Suppress("UNUSED_PARAMETER") project: Project, file: VirtualFile) {
        // Refresh the VFS to pick up changes
        file.refresh(false, false)

        // If the file is open in an editor, reload the document
        val document = FileDocumentManager.getInstance().getDocument(file)
        if (document != null) {
            FileDocumentManager.getInstance().reloadFromDisk(document)
        }
    }

    private fun showInfo(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sopsie.Notifications")
                .createNotification(message, NotificationType.INFORMATION)
                .notify(project)
        }
    }

    private fun showWarning(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sopsie.Notifications")
                .createNotification(message, NotificationType.WARNING)
                .notify(project)
        }
    }

    private fun showError(project: Project, message: String, details: String? = null) {
        ApplicationManager.getApplication().invokeLater {
            val content = if (details != null) "$message\n$details" else message
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sopsie.Notifications")
                .createNotification("SOPS Error", content, NotificationType.ERROR)
                .notify(project)
        }
    }
}
