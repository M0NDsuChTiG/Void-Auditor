package com.kuzyamond.voidauditor.core.analyzer

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.AuditIntelligence
import com.kuzyamond.voidauditor.core.RiskLevel
import com.kuzyamond.voidauditor.core.evidence.SystemPropEvidence

/**
 * Evaluates [SystemPropEvidence] and interprets risk classification (e.g. build types and system flags).
 */
class SystemPropAnalyzer : EvidenceAnalyzer<SystemPropEvidence> {
    override fun analyze(capability: Capability, evidence: SystemPropEvidence): AuditIntelligence? {
        if (capability !is Capability.ReadSystemProp) return null
        val value = evidence.value ?: return null

        val risk = when (evidence.prop) {
            "ro.build.type" -> when (value) {
                "user" -> RiskLevel.LOW
                "userdebug" -> RiskLevel.MEDIUM
                "eng" -> RiskLevel.HIGH
                else -> return null
            }
            "ro.debuggable" -> when (value) {
                "0" -> RiskLevel.LOW
                "1" -> RiskLevel.HIGH
                else -> return null
            }
            "ro.secure" -> when (value) {
                "1" -> RiskLevel.LOW
                "0" -> RiskLevel.HIGH
                else -> return null
            }
            else -> return null
        }

        return AuditIntelligence(
            capabilityId = evidence.capabilityId,
            riskLevel = risk,
            description = "System property ${evidence.prop} has value '$value' evaluated as $risk"
        )
    }
}
