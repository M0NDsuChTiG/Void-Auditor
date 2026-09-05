package com.kuzyamond.voidauditor.core

/**
 * Canonical risk-level classification used across the entire USF pipeline.
 *
 * `UNKNOWN` represents a risk level that has not been assessed yet.
 * `TIER_1_REVERSIBLE` is a specialized classification for operations that
 * can be trivially undone (e.g. cache trim, setting toggle).
 *
 * This enum is the single source of truth. Subsystem-specific parsers
 * (AI chat, audit trail) map from this type rather than defining
 * their own local duplicates.
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
    UNKNOWN,
    TIER_1_REVERSIBLE
}
