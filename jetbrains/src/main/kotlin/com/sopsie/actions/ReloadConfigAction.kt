package com.sopsie.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.sopsie.config.SopsConfigManager

private val LOG = logger<ReloadConfigAction>()

/**
 * Action to manually reload all SOPS configurations.
 * Useful when .sops.yaml files are modified externally.
 */
class ReloadConfigAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        LOG.info("ReloadConfigAction: Reloading all SOPS configurations")

        val configManager = SopsConfigManager.getInstance(project)
        configManager.reloadAll()

        val configs = configManager.getAllConfigs()
        val message = if (configs.isEmpty()) {
            "No .sops.yaml configurations found"
        } else {
            "Reloaded ${configs.size} configuration(s)"
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Sopsie.Notifications")
            .createNotification("SOPSie", message, NotificationType.INFORMATION)
            .notify(project)
    }
}
