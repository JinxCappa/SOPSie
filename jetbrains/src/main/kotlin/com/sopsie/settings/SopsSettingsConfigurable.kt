package com.sopsie.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.sopsie.SopsiePlugin
import com.sopsie.services.DecryptedViewMode
import com.sopsie.services.OpenBehavior
import com.sopsie.services.SaveBehavior
import com.sopsie.services.SopsSettingsService

/**
 * Settings UI for SOPSie plugin.
 * Configurable in Settings > Tools > SOPSie
 */
class SopsSettingsConfigurable : BoundConfigurable(SopsiePlugin.NAME) {

    private val settings = SopsSettingsService.getInstance()
    private val state = settings.state

    override fun createPanel(): DialogPanel {
        return panel {
            group("SOPS CLI") {
                row("SOPS path:") {
                    textField()
                        .bindText(
                            getter = { state.sopsPath ?: "sops" },
                            setter = { state.sopsPath = it }
                        )
                        .comment("Path to SOPS executable (default: 'sops')")
                        .columns(COLUMNS_MEDIUM)
                }

                row("Timeout (ms):") {
                    intTextField(1000..300000)
                        .bindIntText(state::decryptionTimeout)
                        .comment("Timeout for SOPS operations in milliseconds")
                }
            }

            group("File Open Behavior") {
                row("When opening encrypted files:") {
                    comboBox(OpenBehavior.entries)
                        .bindItem(state::openBehavior.toNullableProperty())
                        .comment("Action to take when opening a SOPS-encrypted file")
                }

                row("Decrypted view mode:") {
                    comboBox(DecryptedViewMode.entries)
                        .bindItem(state::decryptedViewMode.toNullableProperty())
                        .comment("How to display decrypted content when using SHOW_DECRYPTED")
                }

                row {
                    checkBox("Open decrypted view beside original")
                        .bindSelected(state::openDecryptedBeside)
                        .comment("Open decrypted preview/edit in a split editor")
                }
            }

            group("File Save Behavior") {
                row("When saving decrypted files:") {
                    comboBox(SaveBehavior.entries)
                        .bindItem(state::saveBehavior.toNullableProperty())
                        .comment("Action to take when saving a file that was decrypted in-place")
                }
            }

            group("Tab Management") {
                row {
                    checkBox("Auto-close tab after encryption/decryption")
                        .bindSelected(state::autoCloseTab)
                        .comment("Close the original encrypted tab after opening decrypted view")
                }

                row {
                    checkBox("Auto-close paired tab")
                        .bindSelected(state::autoClosePairedTab)
                        .comment("Close the paired tab when closing original or temp file")
                }
            }

            group("Confirmations") {
                row {
                    checkBox("Confirm before rotating data key")
                        .bindSelected(state::confirmRotate)
                }

                row {
                    checkBox("Confirm before updating keys")
                        .bindSelected(state::confirmUpdateKeys)
                }
            }

            group("UI") {
                row {
                    checkBox("Show status bar indicator")
                        .bindSelected(state::showStatusBar)
                        .comment("Show encryption status in the status bar")
                }
            }

            group("Debug") {
                row {
                    checkBox("Enable debug logging")
                        .bindSelected(state::enableDebugLogging)
                        .comment("Enable verbose logging for troubleshooting")
                }
            }
        }
    }
}
