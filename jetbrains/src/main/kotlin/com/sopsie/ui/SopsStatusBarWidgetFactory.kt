package com.sopsie.ui

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.sopsie.SopsiePlugin
import com.sopsie.config.SopsConfigChangeListener
import com.sopsie.config.SopsConfigManager
import com.sopsie.services.SopsSettingsService

/**
 * Factory for creating SOPS status bar widgets.
 * Creates a widget per project window that shows encryption status for the current file.
 */
class SopsStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = SopsStatusBarWidget.ID

    override fun getDisplayName(): String = "${SopsiePlugin.NAME} Status"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget {
        val widget = SopsStatusBarWidget(project)

        // Listen for file selection changes and file open events
        project.messageBus.connect(widget).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    widget.update()
                }

                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    // Update widget when files are opened (including restored files on IDE startup)
                    widget.update()
                }
            }
        )

        // Listen for config changes
        project.messageBus.connect(widget).subscribe(
            SopsConfigManager.TOPIC,
            object : SopsConfigChangeListener {
                override fun configChanged(configPath: String) {
                    widget.update()
                }

                override fun configRemoved(configPath: String) {
                    widget.update()
                }
            }
        )

        return widget
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
        return SopsSettingsService.getInstance().shouldShowStatusBar
    }
}
