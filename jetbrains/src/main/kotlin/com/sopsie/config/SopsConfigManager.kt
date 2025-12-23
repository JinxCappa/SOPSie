package com.sopsie.config

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.util.messages.Topic
import com.sopsie.model.SopsConfig
import com.sopsie.model.SopsCreationRule
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Event interface for configuration changes
 */
interface SopsConfigChangeListener {
    fun configChanged(configPath: String)
    fun configRemoved(configPath: String)
}

/**
 * Project-level service that manages SOPS configurations.
 * Supports .sops.yaml files in any directory, matching SOPS CLI behavior
 * which searches up the directory tree from the target file.
 */
@Service(Service.Level.PROJECT)
class SopsConfigManager(private val project: Project) : Disposable {

    companion object {
        private val LOG = Logger.getInstance(SopsConfigManager::class.java)
        private val CONFIG_NAMES = setOf(".sops.yaml", ".sops.yml")

        val TOPIC = Topic.create("SOPS Configuration Changes", SopsConfigChangeListener::class.java)

        @JvmStatic
        fun getInstance(project: Project): SopsConfigManager = project.service()
    }

    /**
     * Cached configuration with its matcher
     */
    private data class LoadedConfig(
        val config: SopsConfig,
        val configPath: String,
        val configDir: String,
        val matcher: SopsRulesMatcher
    )

    // Map from config file path to loaded config
    private val configs = ConcurrentHashMap<String, LoadedConfig>()

    /**
     * Initialize configurations by scanning the project for .sops.yaml files.
     * Should be called on project open.
     */
    fun initialize() {
        val baseDir = project.guessProjectDir() ?: return

        // Find all .sops.yaml files in the project
        VfsUtilCore.visitChildrenRecursively(baseDir, object : VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
                // Skip node_modules and hidden directories (except .sops.yaml itself)
                if (file.isDirectory) {
                    val name = file.name
                    if (name == "node_modules" || (name.startsWith(".") && name != ".sops.yaml" && name != ".sops.yml")) {
                        return false
                    }
                }

                if (!file.isDirectory && file.name in CONFIG_NAMES) {
                    loadConfig(file)
                }
                return true
            }
        })

        LOG.info("SOPSie: Initialized ${configs.size} configuration file(s)")

        // Notify listeners that configs have been loaded
        // This triggers status bar widget updates for files restored before initialization
        // Use invokeLater to ensure UI components are ready
        ApplicationManager.getApplication().invokeLater {
            project.messageBus.syncPublisher(TOPIC).configChanged("initialized")
        }
    }

    /**
     * Load a specific config file
     */
    fun loadConfig(configFile: VirtualFile) {
        try {
            val content = String(configFile.contentsToByteArray(), Charsets.UTF_8)
            val config = SopsConfigParser.parse(content)
            val configDir = configFile.parent?.path ?: return

            val loadedConfig = LoadedConfig(
                config = config.copy(configFile = configFile),
                configPath = configFile.path,
                configDir = configDir,
                matcher = SopsRulesMatcher(config, Paths.get(configDir))
            )

            configs[configFile.path] = loadedConfig
            LOG.debug("SOPSie: Loaded config from ${configFile.path}")
        } catch (e: Exception) {
            LOG.warn("SOPSie: Failed to load config ${configFile.path}: ${e.message}")
            configs.remove(configFile.path)
        }
    }

    /**
     * Reload configuration when a config file changes
     */
    fun reloadConfig(configFile: VirtualFile) {
        // Clear regex cache to prevent stale patterns
        SopsRulesMatcher.clearRegexCache()

        loadConfig(configFile)

        // Notify listeners
        project.messageBus.syncPublisher(TOPIC).configChanged(configFile.path)
    }

    /**
     * Handle config file deletion
     */
    fun removeConfig(configPath: String) {
        if (configs.remove(configPath) != null) {
            LOG.debug("SOPSie: Removed config $configPath")
            project.messageBus.syncPublisher(TOPIC).configRemoved(configPath)
        }
    }

    /**
     * Find the nearest .sops.yaml config for a file by walking up the directory tree.
     * This matches SOPS CLI behavior.
     */
    private fun findNearestConfig(file: VirtualFile): LoadedConfig? {
        val baseDir = project.guessProjectDir() ?: return null
        val basePath = baseDir.path

        var currentDir = file.parent

        // Walk up the directory tree until we hit the project root
        while (currentDir != null && currentDir.path.startsWith(basePath)) {
            // Check for .sops.yaml or .sops.yml in current directory
            for (configName in CONFIG_NAMES) {
                val configPath = "${currentDir.path}/$configName"
                val loadedConfig = configs[configPath]
                if (loadedConfig != null) {
                    return loadedConfig
                }
            }

            // Move up one directory
            currentDir = currentDir.parent
        }

        return null
    }

    /**
     * Find the matching creation rule for a file
     */
    fun findMatchingRule(file: VirtualFile): SopsCreationRule? {
        val loadedConfig = findNearestConfig(file) ?: return null
        return loadedConfig.matcher.findMatchingRule(file)
    }

    /**
     * Check if a file matches any SOPS creation rule
     */
    fun hasMatchingRule(file: VirtualFile): Boolean {
        return findMatchingRule(file) != null
    }

    /**
     * Get the config that applies to a file
     */
    fun getConfigForFile(file: VirtualFile): SopsConfig? {
        return findNearestConfig(file)?.config
    }

    /**
     * Get all loaded configurations
     */
    fun getAllConfigs(): Collection<SopsConfig> {
        return configs.values.map { it.config }
    }

    /**
     * Check if a file is a .sops.yaml config file
     */
    fun isConfigFile(file: VirtualFile): Boolean {
        return file.name in CONFIG_NAMES
    }

    /**
     * Force reload all configurations
     */
    fun reloadAll() {
        SopsRulesMatcher.clearRegexCache()
        configs.clear()
        initialize()
    }

    override fun dispose() {
        configs.clear()
    }
}
