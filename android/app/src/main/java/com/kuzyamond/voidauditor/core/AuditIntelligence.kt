package com.kuzyamond.voidauditor.core

/**
 * Domain model representing the interpreted risk and semantic evaluation of captured evidence.
 */
data class AuditIntelligence(
    val capabilityId: String,
    val riskLevel: RiskLevel,
    val description: String
)
