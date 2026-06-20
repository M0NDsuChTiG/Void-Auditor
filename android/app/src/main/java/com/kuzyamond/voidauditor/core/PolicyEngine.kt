package com.kuzyamond.voidauditor.core

import com.kuzyamond.voidauditor.RiskLevel

object PolicyEngine {
    private var confirmationThreshold = 50
    private var doubleConfirmationThreshold = 80

    fun evaluate(capability: Capability): PolicyDecision {
        val risk = capability.riskScore

        if (risk >= doubleConfirmationThreshold) {
            return PolicyDecision.RequireDoubleConfirmation
        }
        if (risk >= confirmationThreshold) {
            return PolicyDecision.RequireConfirmation
        }
        return PolicyDecision.Allowed
    }

    fun setThresholds(single: Int, double: Int) {
        confirmationThreshold = single
        doubleConfirmationThreshold = double
    }

    fun isDangerous(capability: Capability): Boolean {
        return capability.riskScore >= confirmationThreshold
    }

    fun severityLabel(riskScore: Int): String = when {
        riskScore >= 90 -> "CRITICAL"
        riskScore >= 70 -> "HIGH"
        riskScore >= 40 -> "MEDIUM"
        else -> "LOW"
    }

    fun severityFromScore(riskScore: Int): RiskLevel = when {
        riskScore >= 90 -> RiskLevel.CRITICAL
        riskScore >= 70 -> RiskLevel.HIGH
        riskScore >= 40 -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }
}
