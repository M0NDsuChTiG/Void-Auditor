package com.kuzyamond.voidauditor.core

import com.kuzyamond.voidauditor.RiskLevel

/**
 * Unified System Framework — formalizes the implicit pipeline:
 *   Capability → PolicyEngine → Confirmation → ShizukuExecutor → AuditLogger
 *
 * Every privileged operation in the app MUST flow through this interface.
 * Subsystems that currently bypass it (Cache, Network, Permission) will be
 * migrated incrementally.
 */
interface USFPipeline {

    /**
     * Common contract for anything the pipeline can execute.
     * The existing core.Capability sealed class already satisfies this.
     * Cache.Capability and future subsystem capabilities will also implement it.
     */
    interface Capability {
        val description: String
        val riskScore: Int
    }

    /**
     * Execution context — identifies who is requesting the action.
     * Maps to ActorType in the audit trail.
     */
    data class Context(
        val actor: ActorType = ActorType.USER,
        val source: String = "app"
    )

    /**
     * Result of a pipeline execution.
     * Wraps ShizukuExecutor.CommandResult with additional pipeline metadata.
     */
    data class Result(
        val commandResult: ShizukuExecutor.CommandResult,
        val decision: PolicyDecision,
        val capability: Capability,
        val context: Context
    ) {
        val isAllowed: Boolean get() = decision is PolicyDecision.Allowed
        val isDenied: Boolean get() = decision is PolicyDecision.Denied
        val wasConfirmed: Boolean get() =
            decision is PolicyDecision.RequireConfirmation ||
            decision is PolicyDecision.RequireDoubleConfirmation
    }

    suspend fun execute(context: Context, capability: Capability): Result

    fun getSummary(): AuditSummary
    fun resetAudit()

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
}
