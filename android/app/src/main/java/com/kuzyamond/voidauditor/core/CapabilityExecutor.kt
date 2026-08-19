package com.kuzyamond.voidauditor.core

import com.kuzyamond.voidauditor.RiskLevel

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
        val command = capabilityToCommand(capability)
        val result = ShizukuExecutor.executeCommand(command)

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
            is Capability.ClearAppData -> "pm clear ${cap.packageName}"
            is Capability.ReadFile -> "cat ${cap.path}"
            is Capability.WriteFile -> "echo > ${cap.path}"
            is Capability.RunAsRoot -> cap.commandHint
            is Capability.NetworkAction -> cap.action
            is Capability.ReadSensitiveData -> cap.dataType
            is Capability.CleanCache -> cap.safeCommand
        }
    }

    private fun capabilityToFixCommand(cap: Capability): String? {
        return when (cap) {
            is Capability.ModifySettings -> "pm grant ${cap.namespace} android.permission.WRITE_SECURE_SETTINGS"
            is Capability.InstallPackage -> "settings put global install_non_market_apps 1"
            is Capability.CleanCache -> null
            else -> null
        }
    }
}
