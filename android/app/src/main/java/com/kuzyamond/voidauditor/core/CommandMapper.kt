package com.kuzyamond.voidauditor.core

/**
 * Pure, stateless mapping from [Capability] to the shell command string
 * that [ShizukuExecutor] will execute.
 *
 * Separated from [CapabilityExecutor] so command-building logic is
 * independently testable and does not depend on execution state,
 * policy decisions, or audit logging.
 */
object CommandMapper {

    fun toCommand(cap: Capability): String {
        return when (cap) {
            // ── READ tier (risk 5–20) ─────────────────────────────
            is Capability.ReadSystemProp -> "getprop ${cap.prop}"
            is Capability.ReadSystemFeatures -> "pm list features"
            is Capability.ReadUserIdentity -> "id"
            is Capability.ReadPackageDetails -> "dumpsys package ${cap.packageName}"
            is Capability.ReadPackageCount -> "pm list packages -3 2>/dev/null | wc -l"
            is Capability.ReadDangerousPermissions -> "pm list permissions -d -g"
            is Capability.ReadDiskUsage -> "df -k ${cap.path} 2>/dev/null | tail -1 | awk '{print \$(NF-2)}'"
            is Capability.ReadDirectorySize -> "du -sb \"${cap.path}\" 2>/dev/null | cut -f1"
            is Capability.ReadFileCount -> "find \"${cap.path}\" -type f 2>/dev/null | wc -l"
            is Capability.ReadLastModified -> "stat -c %Y \"${cap.path}\" 2>/dev/null"
            is Capability.ReadARPTable -> "cat /proc/net/arp"
            is Capability.ReadAppOps -> "appops query-op ${cap.op} allow"
            is Capability.ReadSetting -> "settings get ${cap.namespace} ${cap.key}"
            is Capability.ReadDefaultRoute -> "ip route show default"
            is Capability.ReadWifiInfo -> "cmd wifi get-wifi-info 2>/dev/null"
            is Capability.ReadServiceState -> "dumpsys ${cap.service}"
            is Capability.DiscoverCacheDirectories -> buildString {
                cap.roots.forEachIndexed { i, root ->
                    if (i > 0) append("\n")
                    append("""find "$root" -mindepth 1 -maxdepth ${cap.maxDepth} -type d -name "cache" -prune 2>/dev/null""")
                }
            }
            is Capability.CacheCapability -> ""

            // ── ACTION tier (risk 25–60) ──────────────────────────
            is Capability.ExecuteSystemTrim -> "pm trim-caches ${cap.freeBytesHint}"
            is Capability.ExecuteDryRun -> when (cap.capability) {
                is Capability.CacheCapability.AppCache -> "du -sb /data/data 2>/dev/null | awk '{sum+=\$1} END {print sum}'"
                is Capability.CacheCapability.SystemCache -> "du -sb /data/system 2>/dev/null | awk '{sum+=\$1} END {print sum}'"
                is Capability.CacheCapability.TempFiles -> "du -sb /data/local/tmp 2>/dev/null | awk '{sum+=\$1} END {print sum}'"
                is Capability.CacheCapability.UserCache -> "du -sb /sdcard 2>/dev/null | awk '{sum+=\$1} END {print sum}'"
            }
            is Capability.ExecuteClean -> when (cap.capability) {
                is Capability.CacheCapability.AppCache -> "pm trim-caches 100M"
                is Capability.CacheCapability.SystemCache -> "pm trim-caches 50M"
                is Capability.CacheCapability.TempFiles -> "rm -rf /data/local/tmp/* 2>/dev/null"
                is Capability.CacheCapability.UserCache -> "pm trim-caches 200M"
            }
            is Capability.PingSweep -> buildString {
                append("for ip in ")
                append(cap.targets.joinToString(" "))
                append("; do (ping -c 1 -W 1 \"")
                append('$')
                append("ip\" >/dev/null 2>&1 && echo \"")
                append('$')
                append("ip\") & done; wait")
            }

            // ── REMEDIATION tier (risk 70+) ───────────────────────
            is Capability.RemediationIntent -> when (cap) {
                is Capability.RemediationIntent.EnableFirewall -> "settings put global firewall_enabled 1"
                is Capability.RemediationIntent.DisableDebuggable -> "setprop ro.debuggable 0"
                is Capability.RemediationIntent.HardenSsh -> "settings put secure ssh_hardened 1"
                is Capability.RemediationIntent.DisableService -> "pm disable-user --user 0 com.example.vulnerable"
            }

            // ── ARBITRARY tier (risk 85+) ─────────────────────────
            is Capability.ExecuteArbitraryShell -> cap.commandString
            is Capability.ExecuteScript -> when (cap.language) {
                // ShizukuManager already wraps every command in `sh -c <cmd>` (one shell
                // layer). Passing the BASH payload verbatim avoids a second wrapper that
                // corrupted quotes/parens. PYTHON3 needs an interpreter, so we single-quote
                // the payload so the shell layer cannot expand $ or break quotes.
                Capability.ScriptLanguage.BASH -> cap.payload
                Capability.ScriptLanguage.PYTHON3 -> "python3 -c '${cap.payload.replace("'", "'\\''")}'"
            }

            // ── Legacy / compatibility capabilities ────────────────
            is Capability.RunShellCommand -> cap.commandHint
            is Capability.QueryPackages -> "pm list packages ${cap.filter}"
            is Capability.DumpService -> "dumpsys ${cap.service}"
            is Capability.ModifySettings -> "settings put ${cap.namespace} ${cap.key}"
            is Capability.InstallPackage -> "pm install ${cap.packageName}"
            is Capability.UninstallPackage -> "pm uninstall ${cap.packageName}"
            is Capability.ForceStopPackage -> "am force-stop ${cap.packageName}"
            is Capability.DisablePackage -> "pm disable-user --user 0 ${cap.packageName}"
            is Capability.EnablePackage -> "pm enable ${cap.packageName}"
            is Capability.ClearAppData -> "pm clear ${cap.packageName}"
            is Capability.ReadFile -> "cat ${cap.path}"
            is Capability.WriteFile -> "echo > ${cap.path}"
            is Capability.RunAsRoot -> cap.commandHint
            is Capability.NetworkAction -> cap.action
            is Capability.ReadSensitiveData -> cap.dataType
            is Capability.CleanCache -> cap.safeCommand
            is Capability.ConfigureAdbTcp -> "" // handled by CapabilityExecutor.executeConfigureAdbTcp()
            is Capability.DumpPackageActivities -> "dumpsys package ${cap.packageName} | grep -oE '${cap.packageName}/[A-Za-z0-9_.\$]+' | sort -u | head -80"
            is Capability.LaunchActivity -> "am start -n ${cap.component}"
            is Capability.ListDirectory -> "ls -l ${cap.path}"
            is Capability.CalculateDiskUsage -> "du -sh ${cap.path}"
            is Capability.AdbConnect -> "adb connect ${cap.ipAddress}:${cap.port}"
            is Capability.AdbScanDevices -> "adb devices"
            is Capability.ListApkFiles -> "ls ${cap.path}*.apk 2>/dev/null"
            is Capability.InstallApk -> "pm install -r ${cap.filePath} && echo \"OK\""
            is Capability.GetPackagePath -> "pm path ${cap.packageName}"
            is Capability.CopyFile -> "cp ${cap.source} ${cap.destination} && echo \"OK\""
            is Capability.CreateDirectory -> "mkdir -p ${cap.path}"
        }
    }

    /**
     * Suggested fix command for a capability that failed with a permission error.
     */
    fun fixCommand(cap: Capability): String? {
        return when (cap) {
            is Capability.ModifySettings -> "pm grant ${cap.namespace} android.permission.WRITE_SECURE_SETTINGS"
            is Capability.InstallPackage -> "settings put global install_non_market_apps 1"
            is Capability.CleanCache -> null
            is Capability.ExecuteArbitraryShell -> null
            is Capability.ReadSetting -> "pm grant ${cap.namespace} android.permission.WRITE_SECURE_SETTINGS"
            is Capability.ExecuteSystemTrim -> "settings put global trim_caches_enabled 1"
            is Capability.ExecuteClean -> null
            is Capability.RemediationIntent -> when (cap) {
                is Capability.RemediationIntent.EnableFirewall -> "settings put global firewall_enabled 1"
                is Capability.RemediationIntent.DisableDebuggable -> "setprop ro.debuggable 0"
                is Capability.RemediationIntent.HardenSsh -> "settings put secure ssh_hardened 1"
                is Capability.RemediationIntent.DisableService -> null
            }
            else -> null
        }
    }
}
