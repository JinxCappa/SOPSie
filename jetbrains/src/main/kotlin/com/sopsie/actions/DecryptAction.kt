package com.sopsie.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.sopsie.detection.SopsDetector
import com.sopsie.execution.SopsRunner
import com.sopsie.model.SopsException
import com.sopsie.ui.SopsStatusBarWidget

private val LOG = logger<DecryptAction>()

/**
 * Action to decrypt the current file in-place using SOPS.
 * Replaces encrypted content with plaintext.
 */
class DecryptAction : AnAction() {

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
            showWarning(project, "Can only decrypt local files")
            return
        }

        if (!SopsDetector.getInstance().isEncrypted(file)) {
            showInfo(project, "File is not SOPS-encrypted")
            return
        }

        LOG.debug("DecryptAction: Starting decryption for ${file.path}")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Decrypting file...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Save any unsaved changes first
                    ApplicationManager.getApplication().invokeAndWait {
                        FileDocumentManager.getInstance().saveAllDocuments()
                    }

                    // Decrypt the file
                    val decrypted = SopsRunner.getInstance().decrypt(file.path)

                    // Write decrypted content back to file
                    ApplicationManager.getApplication().invokeAndWait {
                        WriteCommandAction.runWriteCommandAction(project) {
                            file.setBinaryContent(decrypted.toByteArray(Charsets.UTF_8))
                        }
                        file.refresh(true, false)  // synchronous refresh to update VFS cache immediately
                    }

                    // Update status bar widget
                    ApplicationManager.getApplication().invokeLater {
                        val statusBar = WindowManager.getInstance().getStatusBar(project)
                        val widget = statusBar?.getWidget(SopsStatusBarWidget.ID) as? SopsStatusBarWidget
                        widget?.update()
                    }

                    LOG.debug("DecryptAction: Successfully decrypted ${file.path}")
                    showInfo(project, "File decrypted successfully")
                } catch (ex: SopsException) {
                    LOG.warn("DecryptAction: Failed to decrypt ${file.path}", ex)
                    showError(project, ex.error.message, ex.error.details)
                } catch (ex: Exception) {
                    LOG.warn("DecryptAction: Unexpected error decrypting ${file.path}", ex)
                    showError(project, "Decryption failed", ex.message)
                }
            }
        })
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
