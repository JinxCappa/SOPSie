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

private val LOG = logger<EncryptAction>()

/**
 * Action to encrypt the current file in-place using SOPS.
 * Uses the matching creation rule from .sops.yaml.
 */
class EncryptAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project

        // Enable only for local files that are not already encrypted
        val enabled = project != null &&
                file != null &&
                file.isInLocalFileSystem &&
                !file.isDirectory &&
                !SopsDetector.getInstance().isEncrypted(file)

        e.presentation.isEnabledAndVisible = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: run {
            showWarning(project, "No file selected")
            return
        }

        if (!file.isInLocalFileSystem) {
            showWarning(project, "Can only encrypt local files")
            return
        }

        if (SopsDetector.getInstance().isEncrypted(file)) {
            showInfo(project, "File is already SOPS-encrypted")
            return
        }

        LOG.debug("EncryptAction: Starting encryption for ${file.path}")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Encrypting file...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Save any unsaved changes first
                    ApplicationManager.getApplication().invokeAndWait {
                        FileDocumentManager.getInstance().saveAllDocuments()
                    }

                    // Read current content
                    val content = String(file.contentsToByteArray(), Charsets.UTF_8)

                    // Encrypt the content
                    val encrypted = SopsRunner.getInstance().encryptContent(content, file.path)

                    // Write encrypted content back to file
                    ApplicationManager.getApplication().invokeAndWait {
                        WriteCommandAction.runWriteCommandAction(project) {
                            file.setBinaryContent(encrypted.toByteArray(Charsets.UTF_8))
                        }
                        file.refresh(true, false)  // synchronous refresh to update VFS cache immediately
                    }

                    // Update status bar widget
                    ApplicationManager.getApplication().invokeLater {
                        val statusBar = WindowManager.getInstance().getStatusBar(project)
                        val widget = statusBar?.getWidget(SopsStatusBarWidget.ID) as? SopsStatusBarWidget
                        widget?.update()
                    }

                    LOG.debug("EncryptAction: Successfully encrypted ${file.path}")
                    showInfo(project, "File encrypted successfully")
                } catch (ex: SopsException) {
                    LOG.warn("EncryptAction: Failed to encrypt ${file.path}", ex)
                    showError(project, ex.error.message, ex.error.details)
                } catch (ex: Exception) {
                    LOG.warn("EncryptAction: Unexpected error encrypting ${file.path}", ex)
                    showError(project, "Encryption failed", ex.message)
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
