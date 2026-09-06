package com.kuzyamond.voidauditor.core.evidence

/**
 * Base interface for all typed evidence produced by the USF pipeline.
 *
 * Every evidence variant carries:
 * - [capabilityId]: which capability produced this evidence
 * - [capturedAt]: timestamp of capture (millis since epoch)
 *
 * Domain-specific evidence extends this with additional structured fields,
 * replacing the previous flat 20+ class hierarchy in CapabilityEvidence.kt.
 */
sealed interface Evidence {
    val capabilityId: String
    val capturedAt: Long
}

/**
 * Generic fallback for capabilities that don't have a typed parser.
 */
data class RawEvidence(
    override val capabilityId: String,
    override val capturedAt: Long = System.currentTimeMillis(),
    val output: String
) : Evidence
