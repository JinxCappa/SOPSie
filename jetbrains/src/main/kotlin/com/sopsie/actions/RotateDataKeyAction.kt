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

private val LOG = logger<RotateDataKeyAction>()

/**
 * Action to rotate the data key for an encrypted file.
 * Decrypts and re-encrypts all values with a new data key.
 */
class RotateDataKeyAction : AnAction() {

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
            showWarning(project, "Can only rotate keys for local files")
            return
        }

        if (!SopsDetector.getInstance().isEncrypted(file)) {
            showInfo(project, "File is not SOPS-encrypted. Rotate only works on encrypted files.")
            return
        }

        // Confirm rotation if setting is enabled
        if (SopsSettingsService.getInstance().shouldConfirmRotate) {
            val result = Messages.showOkCancelDialog(
                project,
                "Rotate the data key? This will re-encrypt all values with a new data key.",
                "Rotate SOPS Data Key",
                "Rotate",
                "Cancel",
                Messages.getWarningIcon()
            )

            if (result != Messages.OK) {
                LOG.debug("RotateDataKeyAction: User cancelled")
                return
            }
        }

        LOG.debug("RotateDataKeyAction: Starting data key rotation for ${file.path}")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Rotating SOPS data key...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Save any unsaved changes first
                    ApplicationManager.getApplication().invokeAndWait {
                        FileDocumentManager.getInstance().saveAllDocuments()
                    }

                    // Rotate the data key
                    SopsRunner.getInstance().rotate(file.path)

                    // Reload the file in the editor
                    ApplicationManager.getApplication().invokeLater {
                        reloadFileInEditor(project, file)
                    }

                    LOG.debug("RotateDataKeyAction: Successfully rotated data key for ${file.path}")
                    showInfo(project, "SOPS data key rotated successfully")
                } catch (ex: SopsException) {
                    LOG.warn("RotateDataKeyAction: Failed to rotate data key for ${file.path}", ex)
                    showError(project, ex.error.message, ex.error.details)
                } catch (ex: Exception) {
                    LOG.warn("RotateDataKeyAction: Unexpected error rotating data key for ${file.path}", ex)
                    showError(project, "Data key rotation failed", ex.message)
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
