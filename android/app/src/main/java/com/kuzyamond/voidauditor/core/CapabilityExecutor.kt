package com.kuzyamond.voidauditor.core

import com.kuzyamond.voidauditor.RiskLevel
import kotlinx.coroutines.delay

object CapabilityExecutor : USFPipeline {
    var logListener: ((type: String, message: String) -> Unit)? = null

    private val evidenceParser = DefaultEvidenceParser()

    private val auditIssues = mutableListOf<USFPipeline.AuditIssue>()
    private var totalAuditOps = 0
    private var passedOps = 0
    private var failedOps = 0
    private var blockedOps = 0

    override fun resetAudit() {
        auditIssues.clear()
        totalAuditOps = 0
        passedOps = 0
        failedOps = 0
        blockedOps = 0
    }

    /**
     * USFPipeline entry point — all privileged operations route here.
     */
    override suspend fun execute(
        context: USFPipeline.Context,
        capability: USFPipeline.Capability
    ): USFPipeline.Result {
        // Handle RemediationIntent first (separate sealed hierarchy)
        val remediationIntent = capability as? Capability.RemediationIntent
        if (remediationIntent != null) {
            return executeRemediation(context, remediationIntent)
        }

        val coreCapability = capability as? Capability
            ?: return USFPipeline.Result(
                commandResult = ShizukuExecutor.CommandResult(
                    success = false, output = "",
                    error = "UNSUPPORTED_CAPABILITY: ${capability::class.simpleName}",
                    exitCode = -1, executionTimeMs = 0
                ),
                decision = PolicyDecision.Denied("Unsupported capability type"),
                capability = capability,
                context = context
            )

        totalAuditOps++
        val decision = PolicyEngine.evaluate(coreCapability)

        var evidenceResult: EvidenceResult? = null

        val commandResult = when (decision) {
            is PolicyDecision.Denied -> {
                blockedOps++
                logListener?.invoke("POLICY", "DENIED: ${decision.reason}")
                AuditLogger.log(
                    actor = context.actor,
                    capability = coreCapability::class.simpleName ?: "unknown",
                    riskLevel = RiskLevel.CRITICAL,
                    decision = "DENIED",
                    target = coreCapability.description,
                    details = decision.reason
                )
                auditIssues.add(
                    USFPipeline.AuditIssue(
                        capability = coreCapability,
                        severity = "CRITICAL",
                        description = "Policy blocked: ${decision.reason}",
                        fixCommand = null
                    )
                )
                ShizukuExecutor.CommandResult(
                    success = false, output = "",
                    error = "DENIED: ${decision.reason}",
                    exitCode = -1, executionTimeMs = 0
                )
            }
            is PolicyDecision.RequireConfirmation -> {
                val confirmed = ConfirmationManager.awaitConfirmation(
                    intent = coreCapability,
                    onCancel = {
                        blockedOps++
                        logListener?.invoke("POLICY", "CANCELLED: ${coreCapability.description}")
                    }
                )
                if (confirmed) {
                    val (cmdResult, evResult) = executeRaw(coreCapability)
                    evidenceResult = evResult
                    cmdResult
                } else {
                    ShizukuExecutor.CommandResult(
                        success = false, output = "",
                        error = "CANCELLED", exitCode = -1, executionTimeMs = 0
                    )
                }
            }
            is PolicyDecision.RequireDoubleConfirmation -> {
                val firstConfirmed = ConfirmationManager.awaitConfirmation(
                    intent = coreCapability,
                    onCancel = {
                        blockedOps++
                        logListener?.invoke("POLICY", "DOUBLE_CANCELLED: ${coreCapability.description}")
                    }
                )
                val secondConfirmed = if (firstConfirmed) {
                    ConfirmationManager.awaitConfirmation(
                        intent = coreCapability,
                        onCancel = {
                            blockedOps++
                            logListener?.invoke("POLICY", "SECOND_CANCELLED: ${coreCapability.description}")
                        }
                    )
                } else {
                    false
                }
                if (secondConfirmed) {
                    val (cmdResult, evResult) = executeRaw(coreCapability)
                    evidenceResult = evResult
                    cmdResult
                } else {
                    ShizukuExecutor.CommandResult(
                        success = false, output = "",
                        error = "CANCELLED", exitCode = -1, executionTimeMs = 0
                    )
                }
            }
            is PolicyDecision.Allowed -> {
                val (cmdResult, evResult) = executeRaw(coreCapability)
                evidenceResult = evResult
                cmdResult
            }
        }

        return USFPipeline.Result(
            commandResult = commandResult,
            decision = decision,
            capability = coreCapability,
            context = context,
            evidence = evidenceResult
        )
    }

    private suspend fun executeRemediation(
        context: USFPipeline.Context,
        intent: Capability.RemediationIntent
    ): USFPipeline.Result {
        totalAuditOps++
        val decision = PolicyEngine.evaluate(intent)

        var evidenceResult: EvidenceResult? = null

        val commandResult = when (decision) {
            is PolicyDecision.Denied -> {
                blockedOps++
                logListener?.invoke("POLICY", "DENIED: ${decision.reason}")
                AuditLogger.log(
                    actor = context.actor,
                    capability = intent::class.simpleName ?: "unknown",
                    riskLevel = RiskLevel.CRITICAL,
                    decision = "DENIED",
                    target = intent.description,
                    details = decision.reason
                )
                auditIssues.add(
                    USFPipeline.AuditIssue(
                        capability = intent,
                        severity = "CRITICAL",
                        description = "Policy blocked: ${decision.reason}",
                        fixCommand = null
                    )
                )
                ShizukuExecutor.CommandResult(
                    success = false, output = "",
                    error = "DENIED: ${decision.reason}",
                    exitCode = -1, executionTimeMs = 0
                )
            }
            is PolicyDecision.RequireConfirmation -> {
                val confirmed = ConfirmationManager.awaitConfirmation(
                    intent = intent,
                    onCancel = {
                        blockedOps++
                        logListener?.invoke("POLICY", "CANCELLED: ${intent.description}")
                    }
                )
                if (confirmed) {
                    val (cmdResult, evResult) = executeRaw(intent)
                    evidenceResult = evResult
                    cmdResult
                } else {
                    ShizukuExecutor.CommandResult(
                        success = false, output = "",
                        error = "CANCELLED", exitCode = -1, executionTimeMs = 0
                    )
                }
            }
            is PolicyDecision.RequireDoubleConfirmation -> {
                val firstConfirmed = ConfirmationManager.awaitConfirmation(
                    intent = intent,
                    onCancel = {
                        blockedOps++
                        logListener?.invoke("POLICY", "DOUBLE_CANCELLED: ${intent.description}")
                    }
                )
                val secondConfirmed = if (firstConfirmed) {
                    ConfirmationManager.awaitConfirmation(
                        intent = intent,
                        onCancel = {
                            blockedOps++
                            logListener?.invoke("POLICY", "SECOND_CANCELLED: ${intent.description}")
                        }
                    )
                } else {
                    false
                }
                if (secondConfirmed) {
                    val (cmdResult, evResult) = executeRawRemediation(intent)
                    evidenceResult = evResult
                    cmdResult
                } else {
                    ShizukuExecutor.CommandResult(
                        success = false, output = "",
                        error = "CANCELLED", exitCode = -1, executionTimeMs = 0
                    )
                }
            }
            is PolicyDecision.Allowed -> {
                val (cmdResult, evResult) = executeRawRemediation(intent)
                evidenceResult = evResult
                cmdResult
            }
        }

        return USFPipeline.Result(
            commandResult = commandResult,
            decision = decision,
            capability = intent,
            context = context,
            evidence = evidenceResult
        )
    }

    /**
     * Backward-compatible entry point — delegates to USFPipeline.execute.
     */
    suspend fun execute(capability: Capability): ShizukuExecutor.CommandResult {
        val result = execute(USFPipeline.Context(), capability)
        return result.commandResult
    }

    private suspend fun executeRaw(capability: Capability): Pair<ShizukuExecutor.CommandResult, EvidenceResult?> {
        // Validate parameters before execution
        val validation = CapabilityValidator.validate(capability)
        if (validation is CapabilityValidator.ValidationResult.Invalid) {
            val errorMsg = "VALIDATION_FAILED: ${validation.errors.joinToString(", ")}"
            logListener?.invoke("VALIDATION", errorMsg)
            return Pair(
                ShizukuExecutor.CommandResult(
                    success = false, output = "", error = errorMsg,
                    exitCode = -1, executionTimeMs = 0
                ),
                EvidenceResult.ParseFailed(capability.description, "Validation failed: ${validation.errors.joinToString(", ")}", null)
            )
        }

        val commandResult = if (capability is Capability.ConfigureAdbTcp) {
            executeConfigureAdbTcp(capability)
        } else {
            val command = capabilityToCommand(capability)
            ShizukuExecutor.executeCommand(command)
        }

        val evidenceResult = evidenceParser.parse(capability, commandResult)

        if (commandResult.isSuccessful) {
            passedOps++
            logListener?.invoke("EXEC", "${capability::class.simpleName} -> OK")
            AuditLogger.log(
                actor = ActorType.SYSTEM,
                capability = capability::class.simpleName ?: "unknown",
                riskLevel = PolicyEngine.severityFromScore(capability.riskScore),
                decision = "ALLOWED",
                target = capability.description,
                exitCode = commandResult.exitCode,
                durationMs = commandResult.executionTimeMs
            )
        } else {
            failedOps++
            logListener?.invoke("EXEC", "${capability::class.simpleName} -> FAIL: ${commandResult.error}")

            AuditLogger.log(
                actor = ActorType.SYSTEM,
                capability = capability::class.simpleName ?: "unknown",
                riskLevel = RiskLevel.HIGH,
                decision = "DENIED",
                target = capability.description,
                exitCode = commandResult.exitCode,
                durationMs = commandResult.executionTimeMs,
                details = commandResult.error.take(120)
            )

            if (commandResult.error.contains("DENIED") || commandResult.error.contains("PERMISSION")) {
                auditIssues.add(
                    USFPipeline.AuditIssue(
                        capability = capability,
                        severity = "HIGH",
                        description = "Permission denied for: ${capability.description}",
                        fixCommand = capabilityToFixCommand(capability)
                    )
                )
            }
        }

        return Pair(commandResult, evidenceResult)
    }

    private suspend fun executeRawRemediation(intent: Capability.RemediationIntent): Pair<ShizukuExecutor.CommandResult, EvidenceResult?> {
        // RemediationIntent doesn't go through parameter validation
        val commandResult = when (intent) {
            is Capability.RemediationIntent.EnableFirewall -> ShizukuExecutor.executeCommand("settings put global firewall_enabled 1")
            is Capability.RemediationIntent.DisableDebuggable -> ShizukuExecutor.executeCommand("setprop ro.debuggable 0")
            is Capability.RemediationIntent.HardenSsh -> ShizukuExecutor.executeCommand("settings put secure ssh_hardened 1")
            is Capability.RemediationIntent.DisableService -> ShizukuExecutor.executeCommand("pm disable-user --user 0 com.example.vulnerable")
        }

        val evidenceResult = evidenceParser.parse(intent, commandResult)

        if (commandResult.isSuccessful) {
            passedOps++
            logListener?.invoke("EXEC", "${intent::class.simpleName} -> OK")
            AuditLogger.log(
                actor = ActorType.SYSTEM,
                capability = intent::class.simpleName ?: "unknown",
                riskLevel = PolicyEngine.severityFromScore(intent.riskScore),
                decision = "ALLOWED",
                target = intent.description,
                exitCode = commandResult.exitCode,
                durationMs = commandResult.executionTimeMs
            )
        } else {
            failedOps++
            logListener?.invoke("EXEC", "${intent::class.simpleName} -> FAIL: ${commandResult.error}")

            AuditLogger.log(
                actor = ActorType.SYSTEM,
                capability = intent::class.simpleName ?: "unknown",
                riskLevel = RiskLevel.HIGH,
                decision = "DENIED",
                target = intent.description,
                exitCode = commandResult.exitCode,
                durationMs = commandResult.executionTimeMs,
                details = commandResult.error.take(120)
            )

            if (commandResult.error.contains("DENIED") || commandResult.error.contains("PERMISSION")) {
                auditIssues.add(
                    USFPipeline.AuditIssue(
                        capability = intent,
                        severity = "HIGH",
                        description = "Permission denied for: ${intent.description}",
                        fixCommand = null
                    )
                )
            }
        }

        return Pair(commandResult, evidenceResult)
    }

    override fun getSummary(): USFPipeline.AuditSummary {
        return USFPipeline.AuditSummary(
            total = totalAuditOps,
            passed = passedOps,
            failed = failedOps,
            blocked = blockedOps,
            issues = auditIssues.toList()
        )
    }

    internal fun capabilityToCommand(cap: Capability): String {
        return when (cap) {
            // READ tier
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

            // ACTION tier
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
                append('\$')
                append("ip\" >/dev/null 2>&1 && echo \"")
                append('\$')
                append("ip\") & done; wait")
            }

            // REMEDIATION tier - mapped per intent
            is Capability.RemediationIntent -> cap.let { intent ->
                when (intent) {
                    is Capability.RemediationIntent.EnableFirewall -> "settings put global firewall_enabled 1"
                    is Capability.RemediationIntent.DisableDebuggable -> "setprop ro.debuggable 0"
                    is Capability.RemediationIntent.HardenSsh -> "settings put secure ssh_hardened 1"
                    is Capability.RemediationIntent.DisableService -> "pm disable-user --user 0 com.example.vulnerable"
                }
            }

            // ARBITRARY tier
            is Capability.ExecuteArbitraryShell -> cap.commandString
            is Capability.ExecuteScript -> when (cap.language) {
                Capability.ScriptLanguage.BASH -> "sh -c \"${cap.payload.replace("\"", "\\\\\"")}\""
                Capability.ScriptLanguage.PYTHON3 -> "python3 -c \"${cap.payload.replace("\"", "\\\\\"")}\""
            }

            // Existing capabilities
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
            is Capability.ConfigureAdbTcp -> "" // handled by executeConfigureAdbTcp()
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

    private suspend fun executeConfigureAdbTcp(cap: Capability.ConfigureAdbTcp): ShizukuExecutor.CommandResult {
        val steps = mutableListOf<ShizukuExecutor.CompositeStep>()
        val startTime = System.currentTimeMillis()

        // Step 1: setprop — abort on failure
        val setprop = ShizukuExecutor.executeCommand("setprop service.adb.tcp.port ${cap.port}")
        steps.add(ShizukuExecutor.CompositeStep("setprop service.adb.tcp.port ${cap.port}", setprop))
        if (!setprop.isSuccessful) {
            val elapsed = System.currentTimeMillis() - startTime
            return ShizukuExecutor.CommandResult(
                success = false,
                output = buildCompositeOutput(steps),
                error = setprop.error.ifBlank { "SETPROP_FAILED (code ${setprop.exitCode})" },
                exitCode = setprop.exitCode,
                executionTimeMs = elapsed
            )
        }

        // Step 2: stop adbd — record, continue regardless
        val stop = ShizukuExecutor.executeCommand("stop adbd")
        steps.add(ShizukuExecutor.CompositeStep("stop adbd", stop))

        // Step 3: start adbd — record, continue regardless
        val start = ShizukuExecutor.executeCommand("start adbd")
        steps.add(ShizukuExecutor.CompositeStep("start adbd", start))

        // Step 4: let adbd restart
        delay(800)

        // Step 5: verify via getprop — VERIFY gate
        val verify = ShizukuExecutor.executeCommand("getprop service.adb.tcp.port")
        steps.add(ShizukuExecutor.CompositeStep("getprop service.adb.tcp.port", verify))

        val verified = verify.isSuccessful && verify.output.trim() == cap.port.toString()
        val elapsed = System.currentTimeMillis() - startTime

        return if (verified) {
            ShizukuExecutor.CommandResult(
                success = true,
                output = buildCompositeOutput(steps),
                error = "",
                exitCode = 0,
                executionTimeMs = elapsed
            )
        } else {
            ShizukuExecutor.CommandResult(
                success = false,
                output = buildCompositeOutput(steps),
                error = "VERIFY_FAILED: setprop reported success but port ${cap.port} not active per getprop",
                exitCode = -1,
                executionTimeMs = elapsed
            )
        }
    }

    private fun buildCompositeOutput(steps: List<ShizukuExecutor.CompositeStep>): String {
        return steps.joinToString("\n") { step ->
            val status = if (step.result.isSuccessful) "OK" else "FAIL: ${step.result.error.ifBlank { "exit=${step.result.exitCode}" }}"
            "[${step.command}] -> $status"
        }
    }

    private fun capabilityToFixCommand(cap: Capability): String? {
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