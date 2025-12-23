package com.sopsie.handlers

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.sopsie.execution.SopsRunner
import com.sopsie.model.SopsException
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<TempFileHandler>()

/**
 * Manages temporary files for edit-in-place functionality.
 * Tracks mapping between temp files and original encrypted files,
 * handles encryption on save, and cleanup on close.
 */
@Service(Service.Level.PROJECT)
class TempFileHandler(private val project: Project) : Disposable {

    // Map: temp file path -> original file path
    private val tempToOriginal = ConcurrentHashMap<String, String>()

    // Map: temp file path -> VirtualFile for the temp file
    private val tempFiles = ConcurrentHashMap<String, VirtualFile>()

    init {
        // Subscribe to document save events
        val connection = project.messageBus.connect(this)
        connection.subscribe(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
            override fun beforeDocumentSaving(document: Document) {
                onDocumentSaved(document)
            }
        })

        // Subscribe to editor close events
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                onFileClosed(file)
            }
        })
    }

    /**
     * Create a temporary file with decrypted content.
     * @return The VirtualFile of the temp file, or null if creation failed.
     */
    fun createTempFile(originalFile: VirtualFile, decryptedContent: String): VirtualFile? {
        val originalPath = originalFile.path
        val ext = originalFile.extension ?: ""
        val nameWithoutExt = originalFile.nameWithoutExtension

        // Create unique temp file name: {name}.sops-edit.{ext}
        val tempFileName = if (ext.isNotEmpty()) {
            "$nameWithoutExt.sops-edit.$ext"
        } else {
            "$nameWithoutExt.sops-edit"
        }

        val tempDir = System.getProperty("java.io.tmpdir")
        val tempPath = File(tempDir, tempFileName).absolutePath

        try {
            // Write decrypted content to temp file
            File(tempPath).writeText(decryptedContent, Charsets.UTF_8)

            // Refresh VFS to see the new file
            val tempVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(tempPath)
            if (tempVirtualFile != null) {
                // Track the mapping
                tempToOriginal[tempPath] = originalPath
                tempFiles[tempPath] = tempVirtualFile
                LOG.debug("Created temp file: $tempPath -> $originalPath")
                return tempVirtualFile
            } else {
                LOG.warn("Failed to find temp file in VFS: $tempPath")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to create temp file: ${e.message}", e)
        }

        return null
    }

    /**
     * Check if a file is a managed temp file.
     */
    fun isTempFile(file: VirtualFile): Boolean {
        return tempToOriginal.containsKey(file.path)
    }

    /**
     * Check if a file path is a managed temp file.
     */
    fun isTempFile(path: String): Boolean {
        return tempToOriginal.containsKey(path)
    }

    /**
     * Get the original file path for a temp file.
     */
    fun getOriginalPath(tempFile: VirtualFile): String? {
        return tempToOriginal[tempFile.path]
    }

    /**
     * Get the original file for a temp file.
     */
    fun getOriginalFile(tempFile: VirtualFile): VirtualFile? {
        val originalPath = tempToOriginal[tempFile.path] ?: return null
        return LocalFileSystem.getInstance().findFileByPath(originalPath)
    }

    /**
     * Handle document save - encrypt and write back to original.
     */
    private fun onDocumentSaved(document: Document) {
        val tempFile = FileDocumentManager.getInstance().getFile(document) ?: return
        val tempPath = tempFile.path
        val originalPath = tempToOriginal[tempPath] ?: return

        val content = document.text
        LOG.debug("Encrypting temp file $tempPath -> $originalPath")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Encrypting and saving...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Encrypt content using SOPS
                    val encrypted = SopsRunner.getInstance().encryptContent(content, originalPath)

                    // Write encrypted content to original file
                    ApplicationManager.getApplication().invokeAndWait {
                        WriteCommandAction.runWriteCommandAction(project) {
                            val originalFile = LocalFileSystem.getInstance().findFileByPath(originalPath)
                            originalFile?.setBinaryContent(encrypted.toByteArray(Charsets.UTF_8))
                        }
                    }

                    LOG.debug("Encrypted temp file $tempPath -> $originalPath")
                    showInfo("Encrypted and saved to ${File(originalPath).name}")
                } catch (ex: SopsException) {
                    LOG.warn("Failed to encrypt: ${ex.error.message}", ex)
                    showError("Failed to encrypt and save", ex.error.message)
                } catch (ex: Exception) {
                    LOG.warn("Failed to encrypt: ${ex.message}", ex)
                    showError("Failed to encrypt and save", ex.message)
                }
            }
        })
    }

    /**
     * Handle file close - clean up temp file tracking and delete file.
     */
    private fun onFileClosed(file: VirtualFile) {
        val tempPath = file.path
        if (!tempToOriginal.containsKey(tempPath)) {
            return
        }

        LOG.debug("Cleaning up temp file: $tempPath")
        tempToOriginal.remove(tempPath)
        tempFiles.remove(tempPath)

        // Delete the temp file from disk
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val tempFile = File(tempPath)
                if (tempFile.exists()) {
                    tempFile.delete()
                    LOG.debug("Deleted temp file: $tempPath")
                }
            } catch (e: Exception) {
                LOG.warn("Could not delete temp file $tempPath: ${e.message}")
            }
        }
    }

    override fun dispose() {
        // Clean up any remaining temp files
        for (tempPath in tempToOriginal.keys) {
            try {
                val tempFile = File(tempPath)
                if (tempFile.exists()) {
                    tempFile.delete()
                    LOG.debug("Deleted temp file on dispose: $tempPath")
                }
            } catch (e: Exception) {
                LOG.warn("Could not delete temp file on dispose $tempPath: ${e.message}")
            }
        }
        tempToOriginal.clear()
        tempFiles.clear()
    }

    private fun showInfo(message: String) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Sopsie.Notifications")
                .createNotification(message, NotificationType.INFORMATION)
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
        fun getInstance(project: Project): TempFileHandler = project.service()
    }
}
