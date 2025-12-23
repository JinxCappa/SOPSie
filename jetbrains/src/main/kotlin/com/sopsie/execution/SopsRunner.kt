package com.sopsie.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.sopsie.model.SopsError
import com.sopsie.model.SopsErrorType
import com.sopsie.model.SopsException
import com.sopsie.services.SopsSettingsService
import java.io.File
import java.nio.charset.StandardCharsets

private val LOG = logger<SopsRunner>()

/**
 * Wrapper for SOPS CLI operations
 */
@Service(Service.Level.APP)
class SopsRunner {

    private val settings: SopsSettingsService
        get() = SopsSettingsService.getInstance()

    /**
     * Get the working directory for SOPS commands.
     * SOPS looks for .sops.yaml from CWD and walks up the directory tree.
     */
    private fun getWorkingDirectory(filePath: String): File {
        return File(filePath).parentFile
    }

    /**
     * Determine the input type based on file extension
     */
    private fun getInputType(extension: String): String {
        return when (extension.lowercase()) {
            "json" -> "json"
            "env" -> "dotenv"
            "ini" -> "ini"
            "yaml", "yml" -> "yaml"
            else -> "binary"
        }
    }

    /**
     * Decrypt a file and return the decrypted content.
     * Automatically detects file type from extension.
     */
    fun decrypt(filePath: String): String {
        val ext = File(filePath).extension
        val fileType = getInputType(ext)
        LOG.debug("SopsRunner: Decrypting $filePath (type=$fileType)")

        return if (fileType == "binary") {
            runSops(
                listOf(
                    "--decrypt",
                    "--input-type", "binary",
                    "--output-type", "binary",
                    filePath
                ),
                filePath
            )
        } else {
            runSops(listOf("--decrypt", filePath), filePath)
        }
    }

    /**
     * Encrypt a file and return the encrypted content.
     * Automatically detects file type from extension.
     */
    fun encrypt(filePath: String): String {
        val ext = File(filePath).extension
        val fileType = getInputType(ext)
        LOG.debug("SopsRunner: Encrypting $filePath (type=$fileType)")

        return if (fileType == "binary") {
            runSops(
                listOf(
                    "--encrypt",
                    "--input-type", "binary",
                    "--output-type", "binary",
                    filePath
                ),
                filePath
            )
        } else {
            runSops(listOf("--encrypt", filePath), filePath)
        }
    }

    /**
     * Encrypt content from stdin with filename override for rule matching.
     * Used for encrypting in-memory content without writing to disk first.
     *
     * Note: Uses a temp file approach because SOPS stdin handling is unreliable
     * across platforms (Windows doesn't support `-` or `/dev/stdin`).
     */
    fun encryptContent(content: String, filePath: String): String {
        val ext = File(filePath).extension
        val dir = File(filePath).parentFile
        val inputType = getInputType(ext)
        LOG.debug("SopsRunner: Encrypting content for $filePath (type=$inputType)")

        // Create a temp file in the same directory so .sops.yaml rules match
        // Use same extension so SOPS auto-detects the format
        val tempFileName = ".sopsie-temp-${System.currentTimeMillis()}-${(Math.random() * 100000).toLong()}.$ext"
        val tempFile = File(dir, tempFileName)

        try {
            // Write content to temp file
            tempFile.writeText(content, Charsets.UTF_8)

            // Encrypt the temp file
            return runSops(listOf("--encrypt", tempFile.absolutePath), tempFile.absolutePath)
        } finally {
            // Always clean up temp file
            try {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                LOG.debug("Failed to clean up temp file: ${e.message}")
            }
        }
    }

    /**
     * Update keys in an encrypted file based on .sops.yaml.
     * Re-encrypts the file with the keys defined in the matching creation rule.
     */
    fun updateKeys(filePath: String) {
        LOG.debug("SopsRunner: Updating keys for $filePath")
        runSops(listOf("updatekeys", "--yes", filePath), filePath)
    }

    /**
     * Rotate the data key used to encrypt the file.
     * Decrypts and re-encrypts all values with a new data key.
     */
    fun rotate(filePath: String) {
        LOG.debug("SopsRunner: Rotating data key for $filePath")
        runSops(listOf("rotate", "--in-place", filePath), filePath)
    }

    /**
     * Check if SOPS CLI is available
     */
    fun checkCliAvailable(): Boolean {
        return try {
            runCommand(settings.sopsPath, listOf("--version"), File("."), null, 5000)
            true
        } catch (e: Exception) {
            LOG.debug("SOPS CLI check failed: ${e.message}")
            false
        }
    }

    /**
     * Get SOPS version
     */
    fun getVersion(): String? {
        return try {
            runCommand(settings.sopsPath, listOf("--version"), File("."), null, 5000).trim()
        } catch (e: Exception) {
            LOG.debug("Failed to get SOPS version: ${e.message}")
            null
        }
    }

    private fun runSops(args: List<String>, filePath: String): String {
        return runCommand(
            settings.sopsPath,
            args,
            getWorkingDirectory(filePath),
            null,
            settings.timeout
        )
    }

    private fun runCommand(
        cmd: String,
        args: List<String>,
        workDir: File,
        stdin: String?,
        timeout: Int
    ): String {
        val commandLine = GeneralCommandLine()
            .withExePath(cmd)
            .withParameters(args)
            .withWorkDirectory(workDir)
            .withCharset(StandardCharsets.UTF_8)
            .withEnvironment("SOPS_DISABLE_VERSION_CHECK", "1")

        val handler = CapturingProcessHandler(commandLine)

        // Write stdin if provided
        if (stdin != null) {
            handler.processInput.use { outputStream ->
                outputStream.write(stdin.toByteArray(StandardCharsets.UTF_8))
            }
        }

        val output: ProcessOutput = handler.runProcess(timeout)

        if (output.isTimeout) {
            throw SopsException(SopsError.timeout(timeout))
        }

        if (output.exitCode == 0) {
            return output.stdout
        }

        throw SopsException(parseError(output.stderr, output.exitCode))
    }

    private fun parseError(stderr: String, code: Int): SopsError {
        val lowerStderr = stderr.lowercase()

        if (lowerStderr.contains("could not decrypt") ||
            lowerStderr.contains("failed to get the data key") ||
            lowerStderr.contains("cannot find key")
        ) {
            return SopsError.keyAccessDenied(stderr)
        }

        if (lowerStderr.contains("config file not found") || lowerStderr.contains(".sops.yaml")) {
            return SopsError.configNotFound(stderr)
        }

        if (lowerStderr.contains("error parsing") || lowerStderr.contains("yaml:")) {
            return SopsError.invalidFile(stderr)
        }

        if (stderr.contains("encrypt")) {
            return SopsError.encryptionFailed(stderr)
        }

        if (stderr.contains("decrypt")) {
            return SopsError.decryptionFailed(stderr)
        }

        return SopsError.unknown(stderr, code)
    }

    companion object {
        @JvmStatic
        fun getInstance(): SopsRunner = service()
    }
}
