package com.sopsie.listeners

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.sopsie.handlers.AutoBehaviorHandler
import com.sopsie.handlers.TempFileHandler

private val LOG = logger<SopsSaveListener>()

/**
 * Listens to document save events to trigger auto-encrypt behavior.
 * Handles the beforeDocumentSaving event to encrypt decrypted files before save.
 *
 * Note: TempFileHandler already handles encryption for edit-in-place temp files.
 * This listener handles encryption for files that were decrypted in-place
 * (when openBehavior is AUTO_DECRYPT).
 */
class SopsSaveListener(private val project: Project) : FileDocumentManagerListener {

    private val autoHandler: AutoBehaviorHandler
        get() = AutoBehaviorHandler.getInstance(project)

    private val tempFileHandler: TempFileHandler
        get() = TempFileHandler.getInstance(project)

    /**
     * Called before a document is saved.
     * Delegates to AutoBehaviorHandler to handle auto-encrypt or prompt behavior.
     */
    override fun beforeDocumentSaving(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return

        LOG.debug("beforeDocumentSaving: ${file.name}")

        // Skip temp files - they are handled by TempFileHandler
        if (tempFileHandler.isTempFile(file)) {
            LOG.debug("Skipping temp file save handling: ${file.name}")
            return
        }

        // Delegate to AutoBehaviorHandler for regular files
        // This handles files that were decrypted in-place (AUTO_DECRYPT mode)
        autoHandler.handleDocumentWillSave(document)
    }

}
