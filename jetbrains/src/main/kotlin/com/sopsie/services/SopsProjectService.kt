package com.sopsie.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.sopsie.config.SopsConfigManager
import com.sopsie.detection.SopsDetector
import com.sopsie.execution.SopsRunner
import com.sopsie.handlers.AutoBehaviorHandler
import com.sopsie.handlers.DecryptedEditorHandler
import com.sopsie.handlers.TempFileHandler
import com.sopsie.model.FileEncryptionState
import com.sopsie.model.SopsCreationRule

private val LOG = logger<SopsProjectService>()

/**
 * Project-level coordinator service for SOPSie.
 * Provides a unified API for other components to interact with SOPS functionality.
 */
@Service(Service.Level.PROJECT)
class SopsProjectService(private val project: Project) : Disposable {

    private val configManager: SopsConfigManager
        get() = SopsConfigManager.getInstance(project)

    private val tempFileHandler: TempFileHandler
        get() = TempFileHandler.getInstance(project)

    private val decryptedEditorHandler: DecryptedEditorHandler
        get() = DecryptedEditorHandler.getInstance(project)

    private val fileStateTracker: FileStateTracker
        get() = FileStateTracker.getInstance(project)

    private val autoBehaviorHandler: AutoBehaviorHandler
        get() = AutoBehaviorHandler.getInstance(project)

    /**
     * Get the encryption state of a file
     */
    fun getEncryptionState(file: VirtualFile): FileEncryptionState {
        if (!file.isInLocalFileSystem || file.isDirectory) {
            return FileEncryptionState.UNKNOWN
        }

        val detector = SopsDetector.getInstance()
        val hasRule = configManager.hasMatchingRule(file)
        val isEncrypted = detector.isEncrypted(file)

        return when {
            isEncrypted -> FileEncryptionState.ENCRYPTED
            hasRule -> FileEncryptionState.PLAINTEXT
            else -> FileEncryptionState.UNKNOWN
        }
    }

    /**
     * Check if a file is SOPS-encrypted
     */
    fun isEncrypted(file: VirtualFile): Boolean {
        return SopsDetector.getInstance().isEncrypted(file)
    }

    /**
     * Check if a file matches a SOPS creation rule
     */
    fun hasMatchingRule(file: VirtualFile): Boolean {
        return configManager.hasMatchingRule(file)
    }

    /**
     * Get the creation rule that matches a file
     */
    fun getMatchingRule(file: VirtualFile): SopsCreationRule? {
        return configManager.findMatchingRule(file)
    }

    /**
     * Check if the SOPS CLI is available
     */
    fun isCliAvailable(): Boolean {
        return SopsRunner.getInstance().checkCliAvailable()
    }

    /**
     * Get the SOPS CLI version, or null if unavailable
     */
    fun getCliVersion(): String? {
        return SopsRunner.getInstance().getVersion()
    }

    /**
     * Check if a file is a temp file managed by this plugin
     */
    fun isManagedTempFile(file: VirtualFile): Boolean {
        return tempFileHandler.isTempFile(file)
    }

    /**
     * Get the source file for a temp file, if any
     */
    fun getSourceFile(tempFile: VirtualFile): VirtualFile? {
        return tempFileHandler.getOriginalFile(tempFile)
    }

    /**
     * Open decrypted view for a file based on settings
     */
    fun openDecryptedView(file: VirtualFile) {
        decryptedEditorHandler.openDecryptedView(file)
    }

    /**
     * Force reload all configurations
     */
    fun reloadConfigurations() {
        LOG.info("SOPSie: Reloading all configurations")
        configManager.reloadAll()
    }

    /**
     * Mark a file as decrypted (tracked for auto-encrypt)
     */
    fun markFileDecrypted(file: VirtualFile) {
        fileStateTracker.markDecrypted(file)
    }

    /**
     * Mark a file as encrypted
     */
    fun markFileEncrypted(file: VirtualFile) {
        fileStateTracker.markEncrypted(file)
    }

    /**
     * Check if a file is marked as decrypted
     */
    fun isFileMarkedDecrypted(file: VirtualFile): Boolean {
        return fileStateTracker.isMarkedDecrypted(file)
    }

    override fun dispose() {
        LOG.debug("SOPSie: Disposing project service for ${project.name}")
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): SopsProjectService = project.service()
    }
}
