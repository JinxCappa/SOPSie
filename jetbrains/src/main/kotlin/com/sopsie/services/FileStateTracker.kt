package com.sopsie.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the decryption state of files within a project.
 * Files are marked as decrypted when they've been decrypted in-place
 * and need to be re-encrypted on save.
 */
@Service(Service.Level.PROJECT)
class FileStateTracker(private val project: Project) : Disposable {

    private val decryptedFiles = ConcurrentHashMap.newKeySet<String>()

    /**
     * Mark a file as decrypted
     */
    fun markDecrypted(file: VirtualFile) {
        decryptedFiles.add(file.path)
    }

    /**
     * Mark a file as decrypted by path
     */
    fun markDecrypted(path: String) {
        decryptedFiles.add(path)
    }

    /**
     * Mark a file as encrypted (remove from decrypted set)
     */
    fun markEncrypted(file: VirtualFile) {
        decryptedFiles.remove(file.path)
    }

    /**
     * Mark a file as encrypted by path
     */
    fun markEncrypted(path: String) {
        decryptedFiles.remove(path)
    }

    /**
     * Check if a file is marked as decrypted
     */
    fun isMarkedDecrypted(file: VirtualFile): Boolean {
        return decryptedFiles.contains(file.path)
    }

    /**
     * Check if a file is marked as decrypted by path
     */
    fun isMarkedDecrypted(path: String): Boolean {
        return decryptedFiles.contains(path)
    }

    /**
     * Clear tracking for a specific file
     */
    fun clearFile(file: VirtualFile) {
        decryptedFiles.remove(file.path)
    }

    /**
     * Clear tracking for a specific file by path
     */
    fun clearFile(path: String) {
        decryptedFiles.remove(path)
    }

    /**
     * Clear all tracked files
     */
    fun clear() {
        decryptedFiles.clear()
    }

    override fun dispose() {
        clear()
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): FileStateTracker = project.service()
    }
}
