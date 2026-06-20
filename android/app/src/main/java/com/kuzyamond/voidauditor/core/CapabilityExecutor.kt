package com.kuzyamond.voidauditor.core

import com.kuzyamond.voidauditor.GlobalLog
import com.kuzyamond.voidauditor.RiskLevel

object CapabilityExecutor {
    var logListener: ((type: String, message: String) -> Unit)? = null

    data class AuditSummary(
        val total: Int,
        val passed: Int,
        val failed: Int,
        val blocked: Int,
        val issues: List<AuditIssue>
    )

    data class AuditIssue(
        val capability: Capability,
        val severity: String,
        val description: String,
        val fixCommand: String?
    )

    private val auditIssues = mutableListOf<AuditIssue>()
    private var totalAuditOps = 0
    private var passedOps = 0
    private var failedOps = 0
    private var blockedOps = 0

    fun resetAudit() {
        auditIssues.clear()
        totalAuditOps = 0
        passedOps = 0
        failedOps = 0
        blockedOps = 0
    }

    suspend fun execute(capability: Capability): ShizukuExecutor.CommandResult {
        totalAuditOps++
        val decision = PolicyEngine.evaluate(capability)

        when (decision) {
            is PolicyDecision.Denied -> {
                blockedOps++
                logListener?.invoke("POLICY", "DENIED: ${decision.reason}")
                AuditLogger.log(
                    actor = ActorType.SYSTEM,
                    capability = capability::class.simpleName ?: "unknown",
                    riskLevel = RiskLevel.CRITICAL,
                    decision = "DENIED",
                    target = capability.description,
                    details = decision.reason
                )
                auditIssues.add(
                    AuditIssue(
                        capability = capability,
                        severity = "CRITICAL",
                        description = "Policy blocked: ${decision.reason}",
                        fixCommand = null
                    )
                )
                return ShizukuExecutor.CommandResult(
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
                    intent = capability,
                    onConfirm = {
                        confirmed = true
                    },
                    onCancel = {
                        blockedOps++
                        logListener?.invoke("POLICY", "CANCELLED: ${capability.description}")
                    }
                )

                if (confirmed) {
                    return executeRaw(capability)
                }
                return result
            }
            is PolicyDecision.RequireDoubleConfirmation -> {
                var firstConfirmed = false
                var secondConfirmed = false
                var result = ShizukuExecutor.CommandResult(
                    success = false, output = "",
                    error = "CANCELLED", exitCode = -1, executionTimeMs = 0
                )

                ConfirmationManager.requestConfirmation(
                    intent = capability,
                    onConfirm = { firstConfirmed = true },
                    onCancel = {
                        blockedOps++
                        logListener?.invoke("POLICY", "DOUBLE_CANCELLED: ${capability.description}")
                    }
                )

                if (firstConfirmed) {
                    ConfirmationManager.requestConfirmation(
                        intent = capability,
                        onConfirm = { secondConfirmed = true },
                        onCancel = {
                            blockedOps++
                            logListener?.invoke("POLICY", "SECOND_CANCELLED: ${capability.description}")
                        }
                    )
                }

                if (secondConfirmed) {
                    return executeRaw(capability)
                }
                return result
            }
            is PolicyDecision.Allowed -> {
                return executeRaw(capability)
            }
        }
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
                    AuditIssue(
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

    fun getSummary(): AuditSummary {
        return AuditSummary(
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
        }
    }

    private fun capabilityToFixCommand(cap: Capability): String? {
        return when (cap) {
            is Capability.ModifySettings -> "pm grant ${cap.namespace} android.permission.WRITE_SECURE_SETTINGS"
            is Capability.InstallPackage -> "settings put global install_non_market_apps 1"
            else -> null
        }
    }
}
