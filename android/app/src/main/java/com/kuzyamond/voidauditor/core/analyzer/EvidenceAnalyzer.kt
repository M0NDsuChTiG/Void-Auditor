package com.kuzyamond.voidauditor.core.analyzer

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.AuditIntelligence
import com.kuzyamond.voidauditor.core.evidence.Evidence

/**
 * Generic contract for evaluating captured [Evidence] and mapping it to structured [AuditIntelligence].
 */
interface EvidenceAnalyzer<in T : Evidence> {
    fun analyze(capability: Capability, evidence: T): AuditIntelligence?
}
