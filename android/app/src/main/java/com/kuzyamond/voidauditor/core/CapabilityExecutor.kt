package com.kuzyamond.voidauditor.core

import com.kuzyamond.voidauditor.RiskLevel
import kotlinx.coroutines.delay

object CapabilityExecutor : USFPipeline {
    var logListener: ((type: String, message: String) -> Unit)? = null

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
                var confirmed = false
                var result = ShizukuExecutor.CommandResult(
                    success = false, output = "",
                    error = "CANCELLED", exitCode = -1, executionTimeMs = 0
                )

                ConfirmationManager.requestConfirmation(
                    intent = coreCapability,
                    onConfirm = { confirmed = true },
                    onCancel = {
                        blockedOps++
                        logListener?.invoke("POLICY", "CANCELLED: ${coreCapability.description}")
                    }
                )

                if (confirmed) executeRaw(coreCapability) else result
            }
            is PolicyDecision.RequireDoubleConfirmation -> {
                var firstConfirmed = false
                var secondConfirmed = false
                var result = ShizukuExecutor.CommandResult(
                    success = false, output = "",
                    error = "CANCELLED", exitCode = -1, executionTimeMs = 0
                )

                ConfirmationManager.requestConfirmation(
                    intent = coreCapability,
                    onConfirm = { firstConfirmed = true },
                    onCancel = {
                        blockedOps++
                        logListener?.invoke("POLICY", "DOUBLE_CANCELLED: ${coreCapability.description}")
                    }
                )

                if (firstConfirmed) {
                    ConfirmationManager.requestConfirmation(
                        intent = coreCapability,
                        onConfirm = { secondConfirmed = true },
                        onCancel = {
                            blockedOps++
                            logListener?.invoke("POLICY", "SECOND_CANCELLED: ${coreCapability.description}")
                        }
                    )
                }

                if (secondConfirmed) executeRaw(coreCapability) else result
            }
            is PolicyDecision.Allowed -> {
                executeRaw(coreCapability)
            }
        }

        return USFPipeline.Result(
            commandResult = commandResult,
            decision = decision,
            capability = coreCapability,
            context = context
        )
    }

    /**
     * Backward-compatible entry point — delegates to USFPipeline.execute.
     */
    suspend fun execute(capability: Capability): ShizukuExecutor.CommandResult {
        val result = execute(USFPipeline.Context(), capability)
        return result.commandResult
    }

    private suspend fun executeRaw(capability: Capability): ShizukuExecutor.CommandResult {
        val result = if (capability is Capability.ConfigureAdbTcp) {
            executeConfigureAdbTcp(capability)
        } else {
            val command = capabilityToCommand(capability)
            ShizukuExecutor.executeCommand(command)
        }

        if (result.isSuccessful) {
            passedOps++
            logListener?.invoke("EXEC", "${capability::class.simpleName} -> OK")
            AuditLogger.log(
                actor = ActorType.SYSTEM,
                capability = capability::class.simpleName ?: "unknown",
                riskLevel = PolicyEngine.severityFromScore(capability.riskScore),
                decision = "ALLOWED",
                target = capability.description,
                exitCode = result.exitCode,
                durationMs = result.executionTimeMs
            )
        } else {
            failedOps++
            logListener?.invoke("EXEC", "${capability::class.simpleName} -> FAIL: ${result.error}")

            AuditLogger.log(
                actor = ActorType.SYSTEM,
                capability = capability::class.simpleName ?: "unknown",
                riskLevel = RiskLevel.HIGH,
                decision = "DENIED",
                target = capability.description,
                exitCode = result.exitCode,
                durationMs = result.executionTimeMs,
                details = result.error.take(120)
            )

            if (result.error.contains("DENIED") || result.error.contains("PERMISSION")) {
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

        return result
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

    private fun capabilityToCommand(cap: Capability): String {
        return when (cap) {
            is Capability.ReadSystemProp -> "getprop ${cap.prop}"
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
            is Capability.ExecuteArbitraryShell -> cap.commandString
            is Capability.ConfigureAdbTcp -> "" // handled by executeConfigureAdbTcp(); unreachable here
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
            is Capability.ReadDefaultRoute -> "ip route show default"
            is Capability.ReadWifiInfo -> "cmd wifi get-wifi-info 2>/dev/null"
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
            else -> null
        }
    }
}
