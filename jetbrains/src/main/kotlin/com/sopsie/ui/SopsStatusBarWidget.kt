package com.sopsie.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Alarm
import com.intellij.util.Consumer
import com.sopsie.config.SopsConfigManager
import com.sopsie.detection.SopsDetector
import com.sopsie.model.FileEncryptionState
import com.sopsie.services.FileStateTracker
import com.sopsie.services.SopsSettingsService
import com.sopsie.util.SopsIcons
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Status bar widget that displays SOPS encryption status for the current file.
 * Clicking the widget triggers encrypt/decrypt action based on current state.
 */
class SopsStatusBarWidget(project: Project) : EditorBasedWidget(project), StatusBarWidget.IconPresentation {

    companion object {
        const val ID = "SopsieStatusBar"
        private const val STARTUP_UPDATE_DELAY_MS = 1000
    }

    private var currentState: FileEncryptionState = FileEncryptionState.UNKNOWN
    private var hasMatchingRule: Boolean = false
    private val updateAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getIcon(): Icon? {
        if (!SopsSettingsService.getInstance().shouldShowStatusBar) return null
        if (!hasMatchingRule) return null

        return when (currentState) {
            FileEncryptionState.ENCRYPTED -> SopsIcons.Locked
            FileEncryptionState.DECRYPTED -> SopsIcons.Unlocked
            FileEncryptionState.PLAINTEXT -> SopsIcons.Info
            FileEncryptionState.UNKNOWN -> null
        }
    }

    override fun getTooltipText(): String? {
        if (!hasMatchingRule) return null

        return when (currentState) {
            FileEncryptionState.ENCRYPTED -> "SOPS: Encrypted - Click to decrypt"
            FileEncryptionState.DECRYPTED -> "SOPS: Decrypted - Click to encrypt"
            FileEncryptionState.PLAINTEXT -> "SOPS: Not encrypted - Click to encrypt"
            FileEncryptionState.UNKNOWN -> null
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer { _ ->
            val actionId = when (currentState) {
                FileEncryptionState.ENCRYPTED -> "Sopsie.Decrypt"
                FileEncryptionState.DECRYPTED, FileEncryptionState.PLAINTEXT -> "Sopsie.Encrypt"
                FileEncryptionState.UNKNOWN -> return@Consumer
            }

            val action = ActionManager.getInstance().getAction(actionId) ?: return@Consumer
            val file = getCurrentFile() ?: return@Consumer

            val dataContext: DataContext = SimpleDataContext.builder()
                .add(com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT, project)
                .add(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE, file)
                .build()

            val event = com.intellij.openapi.actionSystem.AnActionEvent.createFromAnAction(
                action,
                null,
                "StatusBar",
                dataContext
            )
            action.actionPerformed(event)
        }
    }

    /**
     * Update the widget based on the current file
     */
    fun update() {
        ApplicationManager.getApplication().invokeLater {
            val file = getCurrentFile()
            updateForFile(file)
            myStatusBar?.updateWidget(ID())
        }
    }

    private fun updateForFile(file: VirtualFile?) {
        if (file == null || file.isDirectory) {
            currentState = FileEncryptionState.UNKNOWN
            hasMatchingRule = false
            return
        }

        // Check if file matches a SOPS rule
        val configManager = SopsConfigManager.getInstance(project)
        hasMatchingRule = configManager.hasMatchingRule(file)

        if (!hasMatchingRule) {
            currentState = FileEncryptionState.UNKNOWN
            return
        }

        // Determine encryption state
        val detector = SopsDetector.getInstance()
        val fileStateTracker = FileStateTracker.getInstance(project)

        currentState = when {
            detector.isEncrypted(file) -> FileEncryptionState.ENCRYPTED
            fileStateTracker.isMarkedDecrypted(file) -> FileEncryptionState.DECRYPTED
            else -> FileEncryptionState.PLAINTEXT
        }
    }

    private fun getCurrentFile(): VirtualFile? {
        return FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
    }

    override fun install(statusBar: StatusBar) {
        super.install(statusBar)
        update()
        
        // Schedule a delayed update for restored files
        // This handles the case where configs aren't loaded yet during IDE startup
        updateAlarm.addRequest({ update() }, STARTUP_UPDATE_DELAY_MS)
    }

    override fun dispose() {
        updateAlarm.cancelAllRequests()
        super.dispose()
    }
}
