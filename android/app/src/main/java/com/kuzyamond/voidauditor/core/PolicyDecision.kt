package com.kuzyamond.voidauditor.core

sealed class PolicyDecision {
    data object Allowed : PolicyDecision()
    data class Denied(val reason: String) : PolicyDecision()
    data object RequireConfirmation : PolicyDecision()
    data object RequireDoubleConfirmation : PolicyDecision()
}
