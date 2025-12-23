package com.sopsie.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger

/**
 * Centralized logging service for SOPSie plugin.
 * Respects the enableDebugLogging setting.
 */
@Service(Service.Level.APP)
class LoggerService {

    private val logger = Logger.getInstance("SOPSie")

    private val settings: SopsSettingsService
        get() = SopsSettingsService.getInstance()

    /**
     * Log a debug message. Only logs if debug logging is enabled in settings.
     */
    fun debug(message: String) {
        if (settings.isDebugLoggingEnabled) {
            logger.debug(message)
        }
    }

    /**
     * Log a debug message with a throwable. Only logs if debug logging is enabled.
     */
    fun debug(message: String, throwable: Throwable) {
        if (settings.isDebugLoggingEnabled) {
            logger.debug(message, throwable)
        }
    }

    /**
     * Log an info message.
     */
    fun info(message: String) {
        logger.info(message)
    }

    /**
     * Log a warning message.
     */
    fun warn(message: String) {
        logger.warn(message)
    }

    /**
     * Log a warning message with a throwable.
     */
    fun warn(message: String, throwable: Throwable) {
        logger.warn(message, throwable)
    }

    /**
     * Log an error message.
     */
    fun error(message: String) {
        logger.error(message)
    }

    /**
     * Log an error message with a throwable.
     */
    fun error(message: String, throwable: Throwable) {
        logger.error(message, throwable)
    }

    /**
     * Check if debug logging is currently enabled.
     */
    fun isDebugEnabled(): Boolean {
        return settings.isDebugLoggingEnabled
    }

    companion object {
        @JvmStatic
        fun getInstance(): LoggerService = service()

        /**
         * Create a logger with a specific category prefix.
         * Useful for component-specific logging.
         */
        fun forClass(clazz: Class<*>): ComponentLogger {
            return ComponentLogger(clazz.simpleName)
        }
    }

    /**
     * Component-specific logger that prefixes messages with the component name.
     */
    class ComponentLogger(private val component: String) {
        private val loggerService: LoggerService
            get() = getInstance()

        fun debug(message: String) {
            loggerService.debug("[$component] $message")
        }

        fun debug(message: String, throwable: Throwable) {
            loggerService.debug("[$component] $message", throwable)
        }

        fun info(message: String) {
            loggerService.info("[$component] $message")
        }

        fun warn(message: String) {
            loggerService.warn("[$component] $message")
        }

        fun warn(message: String, throwable: Throwable) {
            loggerService.warn("[$component] $message", throwable)
        }

        fun error(message: String) {
            loggerService.error("[$component] $message")
        }

        fun error(message: String, throwable: Throwable) {
            loggerService.error("[$component] $message", throwable)
        }
    }
}
