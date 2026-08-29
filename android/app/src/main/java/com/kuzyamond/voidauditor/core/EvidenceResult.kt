package com.kuzyamond.voidauditor.core

sealed interface EvidenceResult {
    val capabilityId: String

    data class Parsed(
        override val capabilityId: String,
        val evidence: CapabilityEvidence
    ) : EvidenceResult

    data class NotApplicable(
        override val capabilityId: String
    ) : EvidenceResult

    data class ParseFailed(
        override val capabilityId: String,
        val reason: String,
        val rawOutput: String?
    ) : EvidenceResult
}