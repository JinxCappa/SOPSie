package com.sopsie.ui

import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.sopsie.config.SopsConfigManager
import com.sopsie.detection.SopsDetector
import com.sopsie.util.SopsIcons
import javax.swing.Icon

/**
 * Provides custom icons for SOPS-encrypted files in the Project View.
 * Shows a lock icon overlay for encrypted files that match SOPS rules.
 */
class SopsIconProvider : IconProvider(), DumbAware {

    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        // Only handle PsiFile elements
        val psiFile = element as? PsiFile ?: return null
        val virtualFile = psiFile.virtualFile ?: return null
        val project = psiFile.project

        return getIconForFile(virtualFile, project)
    }

    private fun getIconForFile(file: VirtualFile, project: Project): Icon? {
        // Skip directories and non-local files
        if (file.isDirectory || !file.isInLocalFileSystem) {
            return null
        }

        // Check if file matches a SOPS rule
        val configManager = SopsConfigManager.getInstance(project)
        if (!configManager.hasMatchingRule(file)) {
            return null
        }

        // Check if file is encrypted
        val detector = SopsDetector.getInstance()
        return if (detector.isEncrypted(file)) {
            SopsIcons.Locked
        } else {
            // File matches rule but isn't encrypted - no special icon
            null
        }
    }
}
