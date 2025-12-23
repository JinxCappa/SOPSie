package com.sopsie.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.sopsie.config.SopsConfigManager
import com.sopsie.detection.SopsDetector
import com.sopsie.services.FileStateTracker
import com.sopsie.util.SopsIcons
import java.util.function.Function
import javax.swing.JComponent

/**
 * Provides editor notification banners for SOPS-encrypted files.
 * Shows contextual actions based on the file's encryption state.
 */
class SopsEditorNotificationProvider : EditorNotificationProvider, DumbAware {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        // Skip directories and non-local files
        if (file.isDirectory || !file.isInLocalFileSystem) {
            return null
        }

        // Check if file matches a SOPS rule
        val configManager = SopsConfigManager.getInstance(project)
        if (!configManager.hasMatchingRule(file)) {
            return null
        }

        val detector = SopsDetector.getInstance()
        val fileStateTracker = FileStateTracker.getInstance(project)

        val isEncrypted = detector.isEncrypted(file)
        val isMarkedDecrypted = fileStateTracker.isMarkedDecrypted(file)

        return when {
            isEncrypted -> createEncryptedPanel(project, file)
            isMarkedDecrypted -> createDecryptedPanel(project, file)
            else -> createPlaintextPanel(project, file)
        }
    }

    private fun createEncryptedPanel(project: Project, file: VirtualFile): Function<FileEditor, JComponent?> {
        return Function { _ ->
            EditorNotificationPanel(EditorNotificationPanel.Status.Info).apply {
                icon(SopsIcons.Locked)
                text = "This file is SOPS-encrypted"

                createActionLabel("Preview Decrypted") {
                    executeAction("Sopsie.ShowPreview", project, file)
                }

                createActionLabel("Edit In Place") {
                    executeAction("Sopsie.EditInPlace", project, file)
                }

                createActionLabel("Decrypt") {
                    executeAction("Sopsie.Decrypt", project, file)
                }
            }
        }
    }

    private fun createDecryptedPanel(project: Project, file: VirtualFile): Function<FileEditor, JComponent?> {
        return Function { _ ->
            EditorNotificationPanel(EditorNotificationPanel.Status.Warning).apply {
                icon(SopsIcons.Unlocked)
                text = "This file was decrypted and should be re-encrypted"

                createActionLabel("Encrypt") {
                    executeAction("Sopsie.Encrypt", project, file)
                }
            }
        }
    }

    private fun createPlaintextPanel(project: Project, file: VirtualFile): Function<FileEditor, JComponent?> {
        return Function { _ ->
            EditorNotificationPanel(EditorNotificationPanel.Status.Info).apply {
                icon(SopsIcons.Info)
                text = "This file matches a SOPS rule and can be encrypted"

                createActionLabel("Encrypt") {
                    executeAction("Sopsie.Encrypt", project, file)
                }
            }
        }
    }

    private fun executeAction(actionId: String, project: Project, file: VirtualFile) {
        val action = ActionManager.getInstance().getAction(actionId) ?: return

        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, file)
            .build()

        val event = AnActionEvent.createFromAnAction(
            action,
            null,
            "EditorNotification",
            dataContext
        )

        action.actionPerformed(event)
    }
}
