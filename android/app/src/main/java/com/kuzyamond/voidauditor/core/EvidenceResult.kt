package com.kuzyamond.voidauditor.core

import com.kuzyamond.voidauditor.core.evidence.Evidence

sealed interface EvidenceResult {
    val capabilityId: String

    data class Parsed(
        override val capabilityId: String,
        val evidence: Evidence
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