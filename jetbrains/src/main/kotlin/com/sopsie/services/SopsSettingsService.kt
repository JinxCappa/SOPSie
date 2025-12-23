package com.sopsie.services

import com.intellij.openapi.components.*

/**
 * Open behavior when a SOPS-encrypted file is opened
 */
enum class OpenBehavior {
    /** Show the encrypted content as-is */
    SHOW_ENCRYPTED,

    /** Automatically decrypt the file in-place */
    AUTO_DECRYPT,

    /** Show decrypted preview in side panel */
    SHOW_DECRYPTED
}

/**
 * Save behavior for decrypted files
 */
enum class SaveBehavior {
    /** Require manual encryption command */
    MANUAL_ENCRYPT,

    /** Automatically encrypt on save */
    AUTO_ENCRYPT,

    /** Prompt user before encrypting */
    PROMPT
}

/**
 * Decrypted view mode
 */
enum class DecryptedViewMode {
    /** Read-only preview */
    PREVIEW,

    /** Editable temp file that encrypts on save */
    EDIT_IN_PLACE
}

/**
 * Persistent settings state for SOPSie plugin
 */
@State(
    name = "SopsieSettings",
    storages = [Storage("sopsie.xml")]
)
class SopsSettingsService : SimplePersistentStateComponent<SopsSettingsState>(SopsSettingsState()) {

    val sopsPath: String
        get() = state.sopsPath ?: "sops"

    val timeout: Int
        get() = state.decryptionTimeout

    val shouldConfirmRotate: Boolean
        get() = state.confirmRotate

    val shouldConfirmUpdateKeys: Boolean
        get() = state.confirmUpdateKeys

    val openBehavior: OpenBehavior
        get() = state.openBehavior

    val saveBehavior: SaveBehavior
        get() = state.saveBehavior

    val shouldShowStatusBar: Boolean
        get() = state.showStatusBar

    val decryptedViewMode: DecryptedViewMode
        get() = state.decryptedViewMode

    val shouldAutoCloseTab: Boolean
        get() = state.autoCloseTab

    val shouldOpenDecryptedBeside: Boolean
        get() = state.openDecryptedBeside

    val shouldAutoClosePairedTab: Boolean
        get() = state.autoClosePairedTab

    val isDebugLoggingEnabled: Boolean
        get() = state.enableDebugLogging

    fun useEditInPlace(): Boolean = decryptedViewMode == DecryptedViewMode.EDIT_IN_PLACE

    companion object {
        @JvmStatic
        fun getInstance(): SopsSettingsService = service()
    }
}

/**
 * Settings state class with default values
 */
class SopsSettingsState : BaseState() {
    var sopsPath by string("sops")
    var decryptionTimeout by property(30000)
    var confirmRotate by property(true)
    var confirmUpdateKeys by property(true)
    var openBehavior by enum(OpenBehavior.SHOW_ENCRYPTED)
    var saveBehavior by enum(SaveBehavior.MANUAL_ENCRYPT)
    var showStatusBar by property(true)
    var decryptedViewMode by enum(DecryptedViewMode.PREVIEW)
    var autoCloseTab by property(true)
    var openDecryptedBeside by property(true)
    var autoClosePairedTab by property(true)
    var enableDebugLogging by property(false)
}
