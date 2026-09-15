package com.kuzyamond.voidauditor.core.analyzer

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.AuditIntelligence
import com.kuzyamond.voidauditor.core.evidence.Evidence
import com.kuzyamond.voidauditor.core.evidence.SystemPropEvidence
import kotlin.reflect.KClass

/**
 * Registry mapping typed evidence to its corresponding analyzer.
 */
class AnalyzerRegistry(
    private val analyzers: Map<KClass<out Evidence>, EvidenceAnalyzer<*>>
) {
    @Suppress("UNCHECKED_CAST")
    fun <E : Evidence> getAnalyzer(evidenceClass: KClass<E>): EvidenceAnalyzer<E>? {
        return analyzers[evidenceClass] as? EvidenceAnalyzer<E>
    }

    fun analyze(capability: Capability, evidence: Evidence): AuditIntelligence? {
        val analyzer = analyzers[evidence::class] as? EvidenceAnalyzer<Evidence> ?: return null
        return analyzer.analyze(capability, evidence)
    }
}

object DefaultAnalyzerRegistry {
    val instance = AnalyzerRegistry(mapOf(
        SystemPropEvidence::class to SystemPropAnalyzer()
    ))
}
