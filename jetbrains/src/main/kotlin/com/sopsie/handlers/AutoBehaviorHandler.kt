package com.sopsie.handlers

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.sopsie.config.SopsConfigManager
import com.sopsie.detection.SopsDetector
import com.sopsie.execution.SopsRunner
import com.sopsie.model.SopsException
import com.sopsie.services.FileStateTracker
import com.sopsie.services.OpenBehavior
import com.sopsie.services.SaveBehavior
import com.sopsie.services.SopsSettingsService

private val LOG = logger<AutoBehaviorHandler>()

/**
 * Handles auto-decrypt on open and auto-encrypt on save behaviors.
 * Note: showDecrypted behavior is handled by SopsFileEditorListener.
 */
@Service(Service.Level.PROJECT)
class AutoBehaviorHandler(private val project: Project) {

    private val settings: SopsSettingsService
        get() = SopsSettingsService.getInstance()

    private val configManager: SopsConfigManager
        get() = SopsConfigManager.getInstance(project)

    private val sopsDetector: SopsDetector
        get() = SopsDetector.getInstance()

    private val sopsRunner: SopsRunner
        get() = SopsRunner.getInstance()

    private val fileStateTracker: FileStateTracker
        get() = FileStateTracker.getInstance(project)

    private val decryptedEditorHandler: DecryptedEditorHandler
        get() = DecryptedEditorHandler.getInstance(project)

    /**
     * Handle file opened event - auto-decrypt or show decrypted based on settings.
     * Called by SopsFileEditorListener when a file is opened.
     */
    fun handleFileOpened(file: VirtualFile) {
        // Only handle regular files
        if (!file.isValid || file.isDirectory) {
            return
        }

        // Skip temp files managed by the plugin
        if (TempFileHandler.getInstance(project).isTempFile(file) ||
            decryptedEditorHandler.isManagedFile(file)) {
            return
        }

        val openBehavior = settings.openBehavior
        LOG.debug("handleFileOpened: ${file.name}, behavior: $openBehavior")

        when (openBehavior) {
            OpenBehavior.AUTO_DECRYPT -> handleAutoDecrypt(file)
            OpenBehavior.SHOW_DECRYPTED -> handleShowDecrypted(file)
            OpenBehavior.SHOW_ENCRYPTED -> {
                // Do nothing - show encrypted content as-is
            }
        }
    }

    /**
     * Handle auto-decrypt behavior - decrypt file in-place if it's SOPS encrypted.
     */
    private fun handleAutoDecrypt(file: VirtualFile) {
        // Check if file matches a SOPS rule
        if (!configManager.hasMatchingRule(file)) {
            LOG.debug("handleAutoDecrypt: ${file.name} has no matching rule")
            return
        }

        // Check if file is actually encrypted
        if (!sopsDetector.isEncrypted(file)) {
            LOG.debug("handleAutoDecrypt: ${file.name} is not encrypted")
            return
        }

        LOG.debug("handleAutoDecrypt: decrypting ${file.name}")
        autoDecryptFile(file)
    }

    /**
     * Handle show decrypted behavior - open decrypted preview/edit based on settings.
     */
    private fun handleShowDecrypted(file: VirtualFile) {
        // Check if file matches a SOPS rule
        if (!configManager.hasMatchingRule(file)) {
            LOG.debug("handleShowDecrypted: ${file.name} has no matching rule")
            return
        }

        // Check if file is actually encrypted
        if (!sopsDetector.isEncrypted(file)) {
            LOG.debug("handleShowDecrypted: ${file.name} is not encrypted")
            return
        }

        LOG.debug("handleShowDecrypted: opening decrypted view for ${file.name}")

        // Open decrypted view based on settings (preview or edit-in-place)
        decryptedEditorHandler.openDecryptedView(
            file,
            ShowDecryptedOptions(
                preserveFocus = false,
                showInfoMessage = false
            )
        )
    }

    /**
     * Decrypt file content in-place and update the document.
     */
    private fun autoDecryptFile(file: VirtualFile) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Auto-decrypting file...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val decrypted = sopsRunner.decrypt(file.path)

                    // Update document content on EDT
                    ApplicationManager.getApplication().invokeLater({
                        val document = FileDocumentManager.getInstance().getDocument(file)
                        if (document != null) {
                            WriteCommandAction.runWriteCommandAction(project) {
                                document.setText(decrypted)
                            }
                            // Mark as decrypted for save behavior tracking
                            fileStateTracker.markDecrypted(file)
                            LOG.debug("Auto-decrypted ${file.name}")
                        }
                    }, ModalityState.defaultModalityState())

                } catch (ex: SopsException) {
                    LOG.warn("Auto-decrypt failed for ${file.name}: ${ex.error.message}", ex)
                    showWarning("Auto-decrypt failed: ${ex.error.message}. You can manually decrypt using the SOPS menu.")
                } catch (ex: Exception) {
                    LOG.warn("Auto-decrypt failed for ${file.name}: ${ex.message}", ex)
                    showWarning("Auto-decrypt failed: ${ex.message}. You can manually decrypt using the SOPS menu.")
                }
            }
        })
    }

    /**
     * Handle document will save event - auto-encrypt or prompt based on settings.
     * Called by SopsSaveListener before a document is saved.
     * @return true if save should proceed, false to cancel
     */
    fun handleDocumentWillSave(document: Document): Boolean {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return true

        // Skip if not marked as decrypted
        if (!fileStateTracker.isMarkedDecrypted(file)) {
            return true
        }

        val saveBehavior = settings.saveBehavior
        LOG.debug("handleDocumentWillSave: ${file.name}, behavior: $saveBehavior")

        return when (saveBehavior) {
            SaveBehavior.AUTO_ENCRYPT -> {
                autoEncryptDocument(document, file)
                true
            }
            SaveBehavior.PROMPT -> {
                promptBeforeSave(document, file)
            }
            SaveBehavior.MANUAL_ENCRYPT -> {
                // Do nothing - user must manually encrypt
                true
            }
        }
    }

    /**
     * Mark a file as decrypted (called after successful decrypt operations).
     */
    fun markDecrypted(file: VirtualFile) {
        fileStateTracker.markDecrypted(file)
    }

    /**
     * Mark a file as encrypted (called after successful encrypt operations).
     */
    fun markEncrypted(file: VirtualFile) {
        fileStateTracker.markEncrypted(file)
    }

    /**
     * Auto-encrypt document content before save.
     */
    private fun autoEncryptDocument(document: Document, file: VirtualFile) {
        val content = document.text

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Encrypting before save...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val encrypted = sopsRunner.encryptContent(content, file.path)

                    // Update document with encrypted content
                    ApplicationManager.getApplication().invokeLater({
                        WriteCommandAction.runWriteCommandAction(project) {
                            document.setText(encrypted)
                        }
                        // Mark as encrypted
                        fileStateTracker.markEncrypted(file)
                        LOG.debug("Auto-encrypted ${file.name}")
                    }, ModalityState.defaultModalityState())

                } catch (ex: SopsException) {
                    LOG.warn("Auto-encrypt failed for ${file.name}: ${ex.error.message}", ex)
                    showError("Auto-encrypt failed", ex.error.message)
                } catch (ex: Exception) {
                    LOG.warn("Auto-encrypt failed for ${file.name}: ${ex.message}", ex)
                    showError("Auto-encrypt failed", ex.message)
                }
            }
        })
    }

    /**
     * Prompt user before saving a decrypted file.
     * @return true to proceed with save, false to cancel
     */
    private fun promptBeforeSave(document: Document, file: VirtualFile): Boolean {
        var result = true

        // Must show dialog on EDT synchronously
        ApplicationManager.getApplication().invokeAndWait({
            val choice = Messages.showYesNoCancelDialog(
                project,
                "This file is decrypted. How would you like to save?",
                "Save Decrypted File",
                "Encrypt & Save",
                "Save Without Encryption",
                "Cancel",
                Messages.getQuestionIcon()
            )

            when (choice) {
                Messages.YES -> {
                    // Encrypt & Save
                    autoEncryptDocument(document, file)
                    result = true
                }
                Messages.NO -> {
                    // Save Without Encryption
                    result = true
                }
                else -> {
                    // Cancel
                    result = false
                }
            }
        }, ModalityState.defaultModalityState())

        return result
    }

    private fun showWarning(message: String) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sopsie.Notifications")
                .createNotification(message, NotificationType.WARNING)
                .notify(project)
        }
    }

    private fun showError(title: String, message: String?) {
        ApplicationManager.getApplication().invokeLater {
            val content = message ?: "Unknown error"
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sopsie.Notifications")
                .createNotification(title, content, NotificationType.ERROR)
                .notify(project)
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): AutoBehaviorHandler = project.service()
    }
}
