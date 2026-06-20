package com.kuzyamond.voidauditor.core.ai

import java.time.Instant

/**
 * Model for AI advisory intent proposals (Sprint 5, VOID Auditor)
 * All fields are capability-constrained, type-safe, parameterized, and strictly non-executable until audited/approved.
 */
data class IntentProposal(
    val id: String, // UUID or trusted unique id
    val advisoryText: String, // Human-readable summary/advice for user
    val proposedCapability: ProposedCapability, // Capability, not direct domain object
    val evidence: List<EvidenceItem> = emptyList(), // Linked evidence trust chain
    val createdAt: Instant = Instant.now(),
    val riskLevel: RiskLevel? = null, // Set by Policy, not AI
    val confidence: Double? = null, // 0..1 confidence in proposal struct
    val proposer: String = "AI", // always "AI" to distinguish human-initiated?
    val proposalStatus: ProposalStatus = ProposalStatus.PENDING, // Audit lifecycle
    val auditHash: String? = null // For audit log integrity (log snapshot/cross-fingerprint)
)

/** Only schema-validated, manifest-whitelisted actions allowed. */
data class ProposedCapability(
    val capabilityId: CapabilityId, // E.g., LIST_PACKAGES, COLLECT_LOGS, etc
    val parameters: Map<String, String> = emptyMap() // BLOCK: no nested objects/arrays, only strongly named param keys
)

data class EvidenceItem(
    val summary: String, // Short trust claim or finding
    val source: SourceIntegrity, // Evidence source description/trust metadata
    val confidence: Double // 0..1 how confident is the AI
)

data class SourceIntegrity(
    val uri: String, // Where did evidence originate? (log, file, process, etc)
    val hash: String? = null, // Optional hash for source/trust replay
    val trustLevel: TrustLevel = TrustLevel.UNKNOWN // Human, System, AI, etc
)

/**
 * Whitelisted allowed capability IDs - must match manifest and prompt!
 */
enum class CapabilityId {
    LIST_PACKAGES,
    COLLECT_LOGS,
    ANALYZE_LOG,
    INSPECT_FILE,
    GENERATE_REPORT
    // extend as needed
}

enum class TrustLevel {
    HUMAN,
    SYSTEM,
    AI,
    UNKNOWN
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class ProposalStatus {
    PENDING,
    VALIDATED,
    APPROVED,
    REJECTED,
    EXECUTED,
    AUDITED
}
