package com.kuzyamond.voidauditor.core

/**
 * Orchestrates the confirmation flow for a [PolicyDecision] before execution.
 *
 * Separated from [CapabilityExecutor] so that:
 * - the confirmation logic is independently testable;
 * - the executor focuses on execution and audit, not UI coordination;
 * - the branching Allowed / RequireConfirmation / RequireDoubleConfirmation /
 *   Denied is expressed once, not duplicated across execute() and
 *   executeRemediation().
 */
object ConfirmationFlow {

    /**
     * Result of the confirmation gate.
     *
     * [Allowed] means the capability may proceed to execution.
     * [Blocked] means the policy denied or the user cancelled.
     */
    sealed interface GateResult {
        data object Allowed : GateResult
        data class Blocked(val reason: String) : GateResult
    }

    /**
     * Run the confirmation gate for [capability] under [decision].
     *
     * - `Allowed` → returns [GateResult.Allowed] immediately.
     * - `RequireConfirmation` → suspends until the user confirms or cancels.
     * - `RequireDoubleConfirmation` → suspends for two sequential confirmations.
     * - `Denied` → returns [GateResult.Blocked] with the denial reason.
     *
     * @param onCancel called once when the user cancels (for audit counting).
     */
    suspend fun gate(
        capability: USFPipeline.Capability,
        decision: PolicyDecision,
        onCancel: () -> Unit = {}
    ): GateResult {
        return when (decision) {
            is PolicyDecision.Allowed -> GateResult.Allowed

            is PolicyDecision.Denied -> GateResult.Blocked(reason = decision.reason)

            is PolicyDecision.RequireConfirmation -> {
                val confirmed = ConfirmationManager.awaitConfirmation(
                    intent = capability,
                    onCancel = onCancel
                )
                if (confirmed) GateResult.Allowed
                else GateResult.Blocked(reason = "CANCELLED")
            }

            is PolicyDecision.RequireDoubleConfirmation -> {
                val firstConfirmed = ConfirmationManager.awaitConfirmation(
                    intent = capability,
                    onCancel = onCancel
                )
                if (!firstConfirmed) return GateResult.Blocked(reason = "CANCELLED")

                val secondConfirmed = ConfirmationManager.awaitConfirmation(
                    intent = capability,
                    onCancel = onCancel
                )
                if (secondConfirmed) GateResult.Allowed
                else GateResult.Blocked(reason = "CANCELLED")
            }
        }
    }
}
