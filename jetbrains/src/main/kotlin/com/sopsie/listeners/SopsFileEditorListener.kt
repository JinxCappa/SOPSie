package com.sopsie.listeners

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.sopsie.handlers.AutoBehaviorHandler
import com.sopsie.handlers.TempFileHandler
import com.sopsie.services.FileStateTracker

private val LOG = logger<SopsFileEditorListener>()

/**
 * Listens to file editor events to trigger auto-behaviors on file open/close.
 * Central coordinator for file state changes and auto behaviors.
 */
class SopsFileEditorListener(private val project: Project) : FileEditorManagerListener {

    private val autoHandler: AutoBehaviorHandler
        get() = AutoBehaviorHandler.getInstance(project)

    private val fileStateTracker: FileStateTracker
        get() = FileStateTracker.getInstance(project)

    private val tempFileHandler: TempFileHandler
        get() = TempFileHandler.getInstance(project)

    /**
     * Called when a file is opened in the editor.
     * Triggers auto-decrypt or show-decrypted behavior based on settings.
     */
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        LOG.debug("fileOpened: ${file.name}")

        // Skip if this is a temp file managed by TempFileHandler
        if (tempFileHandler.isTempFile(file)) {
            LOG.debug("Skipping temp file: ${file.name}")
            return
        }

        // Delegate to AutoBehaviorHandler
        autoHandler.handleFileOpened(file)
    }

    /**
     * Called when a file is closed in the editor.
     * Cleans up file state tracking.
     */
    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        LOG.debug("fileClosed: ${file.name}")

        // Clean up file state tracking for regular files
        // TempFileHandler handles its own cleanup via its own listener
        if (!tempFileHandler.isTempFile(file)) {
            fileStateTracker.clearFile(file)
        }
    }

}
