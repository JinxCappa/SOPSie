package com.sopsie.util

import com.intellij.icons.AllIcons
import javax.swing.Icon

/**
 * Icon constants for the SOPSie plugin.
 * Uses standard IntelliJ platform icons for consistency.
 */
object SopsIcons {
    /** Shield icon for encrypted files */
    val Locked: Icon = AllIcons.Nodes.SecurityRole

    /** Unlock icon for decrypted files (using edit icon as unlocked indicator) */
    val Unlocked: Icon = AllIcons.Actions.Edit

    /** Warning icon for files that should be encrypted but aren't */
    val Warning: Icon = AllIcons.General.Warning

    /** Info icon for plaintext files that match SOPS rules */
    val Info: Icon = AllIcons.General.Note

    /** Shield icon for SOPS-related items */
    val Shield: Icon = AllIcons.Ide.Readonly

    /** Settings icon */
    val Settings: Icon = AllIcons.General.Settings

    /** Refresh/reload icon */
    val Refresh: Icon = AllIcons.Actions.Refresh
}
