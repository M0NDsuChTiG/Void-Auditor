package com.kuzyamond.voidauditor.network

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import com.kuzyamond.voidauditor.core.USFPipeline

/**
 * Wi-Fi ADB detection and device control.
 *
 * On Android 11+ Wireless Debugging is managed by the system via
 * Developer Options → Wireless Debugging. VOID Auditor cannot
 * programmatically enable it; it can only detect the state and
 * offer to open the relevant Settings screen.
 *
 * Legacy ADB TCP mode (service.adb.tcp.port) is a separate mechanism
 * and is checked as a secondary indicator.
 */
object NetworkAdb {

    /**
     * Detect whether Wireless Debugging / ADB-over-TCP is active.
     * Checks both the modern setting (adb_wifi_enabled) and the
     * legacy TCP port property.
     */
    suspend fun isWifiAdbEnabled(): Boolean {
        // Modern Wireless Debugging setting (Android 11+)
        val modernResult = CapabilityExecutor.execute(
            USFPipeline.Context(),
            Capability.ReadSetting("global", "adb_wifi_enabled")
        )
        if (modernResult.commandResult.isSuccessful &&
            modernResult.commandResult.output.trim() == "1") {
            return true
        }

        // Legacy ADB TCP mode
        val legacyResult = CapabilityExecutor.execute(
            USFPipeline.Context(),
            Capability.ReadSystemProp("service.adb.tcp.port")
        )
        if (legacyResult.commandResult.isSuccessful) {
            val port = legacyResult.commandResult.output.trim()
            if (port.isNotEmpty() && port != "0" && port != "-1") return true
        }

        return false
    }

    /**
     * Open Developer Settings where the user can enable Wireless Debugging.
     * VOID Auditor does not attempt to modify this setting directly.
     */
    fun openDeveloperSettingsIntent(): android.content.Intent {
        return android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
