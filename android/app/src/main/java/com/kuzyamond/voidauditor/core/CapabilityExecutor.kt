package com.kuzyamond.voidauditor.core


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
     *
     * Delegates confirmation orchestration to [ConfirmationFlow] and command
     * building to [CommandMapper], keeping this class focused on execution,
     * evidence parsing, and audit logging.
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

        // Confirmation gate — shared for all capability types
        val gateResult = ConfirmationFlow.gate(
            capability = coreCapability,
            decision = decision,
            onCancel = {
                blockedOps++
                logListener?.invoke("POLICY", "CANCELLED: ${coreCapability.description}")
            }
        )

        // If denied/blocked, log and return early
        if (gateResult is ConfirmationFlow.GateResult.Blocked) {
            val reason = gateResult.reason
            logListener?.invoke("POLICY", "DENIED: $reason")
            AuditLogger.log(
                actor = context.actor,
                capability = coreCapability::class.simpleName ?: "unknown",
                riskLevel = RiskLevel.CRITICAL,
                decision = "DENIED",
                target = coreCapability.description,
                details = reason
            )
            auditIssues.add(
                USFPipeline.AuditIssue(
                    capability = coreCapability,
                    severity = "CRITICAL",
                    description = "Policy blocked: $reason",
                    fixCommand = null
                )
            )
            return USFPipeline.Result(
                commandResult = ShizukuExecutor.CommandResult(
                    success = false, output = "",
                    error = reason, exitCode = -1, executionTimeMs = 0
                ),
                decision = decision,
                capability = coreCapability,
                context = context
            )
        }

        // Execute — RemediationIntent uses its own execution path
        val (commandResult, evidenceResult) = if (coreCapability is Capability.RemediationIntent) {
            executeRawRemediation(coreCapability)
        } else {
            executeRaw(coreCapability)
        }

        return USFPipeline.Result(
            commandResult = commandResult,
            decision = decision,
            capability = coreCapability,
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

    /** Delegates to [CommandMapper.toCommand] — kept for backward compatibility. */
    internal fun capabilityToCommand(cap: Capability): String = CommandMapper.toCommand(cap)

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

    /** Delegates to [CommandMapper.fixCommand] — kept for backward compatibility. */
    private fun capabilityToFixCommand(cap: Capability): String? = CommandMapper.fixCommand(cap)
}