package com.sopsie.handlers

import com.intellij.ide.actions.OpenInRightSplitAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.sopsie.execution.SopsRunner
import com.sopsie.model.SopsException
import com.sopsie.services.DecryptedViewMode
import com.sopsie.services.SopsSettingsService
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<DecryptedEditorHandler>()

/**
 * Options for showing decrypted content
 */
data class ShowDecryptedOptions(
    /** Whether to preserve focus on the original editor (true for auto, false for manual) */
    val preserveFocus: Boolean = false,
    /** Show info message on success (for edit-in-place mode) */
    val showInfoMessage: Boolean = true,
    /** Open in split view beside the original */
    val openBeside: Boolean? = null
)

/**
 * Service for opening decrypted views of SOPS files.
 * Handles both read-only preview and editable temp file modes.
 */
@Service(Service.Level.PROJECT)
class DecryptedEditorHandler(private val project: Project) : Disposable {

    private val settings: SopsSettingsService
        get() = SopsSettingsService.getInstance()

    private val tempFileHandler: TempFileHandler
        get() = TempFileHandler.getInstance(project)

    // Track preview files (read-only temp files)
    // Map: original file path -> preview temp file path
    private val previewFiles = ConcurrentHashMap<String, String>()

    // Reverse lookup: preview temp file path -> original file path
    private val previewToOriginal = ConcurrentHashMap<String, String>()

    init {
        // Subscribe to editor close events to clean up preview files
        val connection = project.messageBus.connect(this)
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                onFileClosed(file)
            }
        })
    }

    /**
     * Handle file close - clean up preview temp file.
     */
    private fun onFileClosed(file: VirtualFile) {
        val originalPath = previewToOriginal.remove(file.path) ?: return
        previewFiles.remove(originalPath)

        LOG.debug("Cleaning up preview file: ${file.path}")

        // Delete the preview file from disk
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val previewFile = File(file.path)
                if (previewFile.exists()) {
                    previewFile.setWritable(true)
                    previewFile.delete()
                    LOG.debug("Deleted preview file: ${file.path}")
                }
            } catch (e: Exception) {
                LOG.warn("Could not delete preview file ${file.path}: ${e.message}")
            }
        }
    }

    /**
     * Open a decrypted view based on current settings.
     * Routes to either preview or edit-in-place based on decryptedViewMode setting.
     */
    fun openDecryptedView(sourceFile: VirtualFile, options: ShowDecryptedOptions = ShowDecryptedOptions()) {
        if (settings.useEditInPlace()) {
            openEditInPlace(sourceFile, options)
        } else {
            openPreview(sourceFile, options)
        }
    }

    /**
     * Open a read-only preview of the decrypted content.
     * Creates a temporary file with decrypted content and opens it as read-only.
     */
    fun openPreview(sourceFile: VirtualFile, options: ShowDecryptedOptions = ShowDecryptedOptions()) {
        LOG.debug("Opening preview for ${sourceFile.path}")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Decrypting file...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Decrypt the file
                    val decrypted = SopsRunner.getInstance().decrypt(sourceFile.path)

                    // Create preview temp file
                    val previewFile = createPreviewTempFile(sourceFile, decrypted)
                    if (previewFile == null) {
                        showError("Failed to create preview", "Could not create temporary file")
                        return
                    }

                    // Track the preview file
                    previewFiles[sourceFile.path] = previewFile.path
                    previewToOriginal[previewFile.path] = sourceFile.path

                    // Open the preview file
                    ApplicationManager.getApplication().invokeLater {
                        openFileInEditor(previewFile, sourceFile, options)
                    }

                    LOG.debug("Opened preview for ${sourceFile.path}")
                } catch (ex: SopsException) {
                    LOG.warn("Failed to decrypt for preview: ${ex.error.message}", ex)
                    showError("Failed to show decrypted preview", ex.error.message)
                } catch (ex: Exception) {
                    LOG.warn("Failed to decrypt for preview: ${ex.message}", ex)
                    showError("Failed to show decrypted preview", ex.message)
                }
            }
        })
    }

    /**
     * Open an editable temp file with decrypted content.
     * Creates a temporary file that automatically encrypts back to the original on save.
     */
    fun openEditInPlace(sourceFile: VirtualFile, options: ShowDecryptedOptions = ShowDecryptedOptions()) {
        LOG.debug("Opening edit-in-place for ${sourceFile.path}")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Decrypting file...", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Decrypt the file
                    val decrypted = SopsRunner.getInstance().decrypt(sourceFile.path)

                    // Create temp file via TempFileHandler (which tracks and handles save events)
                    val tempFile = tempFileHandler.createTempFile(sourceFile, decrypted)
                    if (tempFile == null) {
                        showError("Failed to open for editing", "Could not create temporary file")
                        return
                    }

                    // Open the temp file
                    ApplicationManager.getApplication().invokeLater {
                        openFileInEditor(tempFile, sourceFile, options)

                        if (options.showInfoMessage) {
                            showInfo("Editing decrypted copy. Save to encrypt back to ${sourceFile.name}")
                        }
                    }

                    LOG.debug("Opened edit-in-place for ${sourceFile.path}")
                } catch (ex: SopsException) {
                    LOG.warn("Failed to decrypt for editing: ${ex.error.message}", ex)
                    showError("Failed to open for editing", ex.error.message)
                } catch (ex: Exception) {
                    LOG.warn("Failed to decrypt for editing: ${ex.message}", ex)
                    showError("Failed to open for editing", ex.message)
                }
            }
        })
    }

    /**
     * Create a preview temp file (read-only).
     */
    private fun createPreviewTempFile(originalFile: VirtualFile, decryptedContent: String): VirtualFile? {
        val ext = originalFile.extension ?: ""
        val nameWithoutExt = originalFile.nameWithoutExtension
        val uniqueSuffix = System.currentTimeMillis()

        // Create unique temp file name: {name}.sops-preview-{timestamp}.{ext}
        val tempFileName = if (ext.isNotEmpty()) {
            "$nameWithoutExt.sops-preview-$uniqueSuffix.$ext"
        } else {
            "$nameWithoutExt.sops-preview-$uniqueSuffix"
        }

        val tempDir = System.getProperty("java.io.tmpdir")
        val tempPath = File(tempDir, tempFileName).absolutePath

        try {
            // Write decrypted content to temp file
            val tempFile = File(tempPath)
            tempFile.writeText(decryptedContent, Charsets.UTF_8)

            // Make the file read-only
            tempFile.setReadOnly()

            // Refresh VFS to see the new file
            return LocalFileSystem.getInstance().refreshAndFindFileByPath(tempPath)
        } catch (e: Exception) {
            LOG.warn("Failed to create preview temp file: ${e.message}", e)
        }

        return null
    }

    /**
     * Open a file in the editor, optionally beside the source file in a split view.
     */
    private fun openFileInEditor(fileToOpen: VirtualFile, sourceFile: VirtualFile, options: ShowDecryptedOptions) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val shouldOpenBeside = options.openBeside ?: settings.shouldOpenDecryptedBeside

        if (shouldOpenBeside) {
            // First ensure the source file is open and focused
            fileEditorManager.openFile(sourceFile, true)

            // Use the OpenInRightSplitAction API to open the file in a right split
            val editorWindow = OpenInRightSplitAction.openInRightSplit(project, fileToOpen, null, true)

            if (editorWindow == null) {
                // Fallback: just open normally if split failed
                fileEditorManager.openFile(fileToOpen, !options.preserveFocus)
            }
        } else {
            // Open in the current editor group
            fileEditorManager.openFile(fileToOpen, !options.preserveFocus)
        }

        // Return focus to original if requested
        if (options.preserveFocus) {
            fileEditorManager.openFile(sourceFile, true)
        }
    }

    /**
     * Check if a file is a preview file (read-only temp).
     */
    fun isPreviewFile(file: VirtualFile): Boolean {
        return file.name.contains(".sops-preview-")
    }

    /**
     * Check if a file is managed (either preview or edit temp).
     */
    fun isManagedFile(file: VirtualFile): Boolean {
        return isPreviewFile(file) || tempFileHandler.isTempFile(file)
    }

    /**
     * Get the original file for a preview file.
     */
    fun getOriginalFileForPreview(previewFile: VirtualFile): VirtualFile? {
        val originalPath = previewToOriginal[previewFile.path] ?: return null
        return LocalFileSystem.getInstance().findFileByPath(originalPath)
    }

    /**
     * Switch from preview mode to edit-in-place mode.
     * Closes the preview, cleans up, and opens an editable temp file.
     */
    fun switchToEditMode(previewFile: VirtualFile) {
        val originalPath = previewToOriginal[previewFile.path]
        if (originalPath == null) {
            showError("Cannot switch to edit mode", "Original file not found for this preview")
            return
        }

        val originalFile = LocalFileSystem.getInstance().findFileByPath(originalPath)
        if (originalFile == null) {
            showError("Cannot switch to edit mode", "Original file no longer exists: $originalPath")
            return
        }

        LOG.debug("Switching from preview to edit mode for ${originalFile.path}")

        // Close the preview tab
        ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(project).closeFile(previewFile)

            // Clean up preview file
            cleanupPreviewFile(previewFile.path, originalPath)

            // Open edit-in-place for the original file
            openEditInPlace(originalFile, ShowDecryptedOptions(
                preserveFocus = false,
                showInfoMessage = true
            ))
        }
    }

    /**
     * Clean up a preview file from tracking and disk.
     */
    private fun cleanupPreviewFile(previewPath: String, originalPath: String) {
        previewFiles.remove(originalPath)
        previewToOriginal.remove(previewPath)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val previewFileOnDisk = File(previewPath)
                if (previewFileOnDisk.exists()) {
                    previewFileOnDisk.setWritable(true)
                    previewFileOnDisk.delete()
                    LOG.debug("Deleted preview file: $previewPath")
                }
            } catch (e: Exception) {
                LOG.warn("Could not delete preview file $previewPath: ${e.message}")
            }
        }
    }

    /**
     * Close and clean up preview file for a source file.
     */
    fun closePreview(sourceFile: VirtualFile) {
        val previewPath = previewFiles.remove(sourceFile.path) ?: return
        previewToOriginal.remove(previewPath)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val previewFileOnDisk = File(previewPath)
                if (previewFileOnDisk.exists()) {
                    // Make writable so we can delete
                    previewFileOnDisk.setWritable(true)
                    previewFileOnDisk.delete()
                    LOG.debug("Deleted preview file: $previewPath")
                }
            } catch (e: Exception) {
                LOG.warn("Could not delete preview file $previewPath: ${e.message}")
            }
        }
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

    override fun dispose() {
        // Clean up any remaining preview files
        for (previewPath in previewToOriginal.keys) {
            try {
                val previewFile = File(previewPath)
                if (previewFile.exists()) {
                    previewFile.setWritable(true)
                    previewFile.delete()
                    LOG.debug("Deleted preview file on dispose: $previewPath")
                }
            } catch (e: Exception) {
                LOG.warn("Could not delete preview file on dispose $previewPath: ${e.message}")
            }
        }
        previewFiles.clear()
        previewToOriginal.clear()
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): DecryptedEditorHandler = project.service()
    }
}
