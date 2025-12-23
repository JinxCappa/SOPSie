package com.sopsie.listeners

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.WindowManager
import com.sopsie.config.SopsConfigManager
import com.sopsie.execution.SopsRunner
import com.sopsie.services.SopsSettingsService
import com.sopsie.ui.SopsStatusBarWidget

private val LOG = logger<SopsStartupActivity>()

/**
 * Plugin startup activity that runs when a project opens.
 * Initializes the plugin and checks for SOPS CLI availability.
 */
class SopsStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        LOG.info("SOPSie: Plugin initializing for project ${project.name}")

        // Initialize configuration manager
        val configManager = SopsConfigManager.getInstance(project)
        configManager.initialize()

        // Update status bar widget now that configs are loaded
        // This handles restored files that were opened before config initialization
        updateStatusBarWidget(project)

        // Check if SOPS CLI is available
        val sopsRunner = SopsRunner.getInstance()
        if (!sopsRunner.checkCliAvailable()) {
            val settings = SopsSettingsService.getInstance()
            showCliNotFoundWarning(project, settings.sopsPath)
        } else {
            val version = sopsRunner.getVersion()
            LOG.info("SOPSie: SOPS CLI available${version?.let { " ($it)" } ?: ""}")
        }
    }

    private fun updateStatusBarWidget(project: Project) {
        val statusBar = WindowManager.getInstance().getStatusBar(project) ?: return
        val widget = statusBar.getWidget(SopsStatusBarWidget.ID) as? SopsStatusBarWidget ?: return
        widget.update()
    }

    private fun showCliNotFoundWarning(project: Project, configuredPath: String) {
        LOG.warn("SOPSie: SOPS CLI not found at configured path: $configuredPath")

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Sopsie.Notifications")
            .createNotification(
                "SOPSie",
                "SOPS CLI not found at \"$configuredPath\". " +
                    "Please install SOPS or update the path in Settings → Tools → SOPSie.",
                NotificationType.WARNING
            )
            .notify(project)
    }
}
