package com.kuzyamond.voidauditor.core

import com.kuzyamond.voidauditor.GlobalLog
import com.kuzyamond.voidauditor.RiskLevel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class AuditEvent(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val actor: ActorType,
    val capability: String,
    val target: String? = null,
    val riskLevel: RiskLevel,
    val decision: String,
    val exitCode: Int? = null,
    val durationMs: Long? = null,
    val details: String? = null
) {
    val formattedTime: String
        get() = timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))

    val severityLabel: String
        get() = when (riskLevel) {
            RiskLevel.CRITICAL -> "CRIT"
            RiskLevel.HIGH -> "HIGH"
            RiskLevel.MEDIUM -> "MED"
            RiskLevel.LOW -> "LOW"
            RiskLevel.UNKNOWN -> "???"
        }

    val decisionColor: String
        get() = when (decision) {
            "ALLOWED", "CONFIRMED" -> "ok"
            "DENIED", "BLOCKED" -> "crit"
            else -> "warn"
        }
}

enum class ActorType {
    USER, AI, SCRIPT, SYSTEM
}

object AuditLogger {
    private val events = mutableListOf<AuditEvent>()
    private val maxEvents = 500

    fun log(event: AuditEvent) {
        synchronized(events) {
            events.add(event)
            if (events.size > maxEvents) {
                events.removeAt(0)
            }
        }
        GlobalLog.log(
            "[${event.actor}] ${event.capability} -> ${event.decision} | Risk: ${event.riskLevel}",
            if (event.riskLevel == RiskLevel.CRITICAL || event.riskLevel == RiskLevel.HIGH) "crit" else "info",
            "AUDIT"
        )
    }

    fun log(
        actor: ActorType,
        capability: String,
        riskLevel: RiskLevel,
        decision: String,
        target: String? = null,
        exitCode: Int? = null,
        durationMs: Long? = null,
        details: String? = null
    ) {
        log(
            AuditEvent(
                actor = actor,
                capability = capability,
                target = target,
                riskLevel = riskLevel,
                decision = decision,
                exitCode = exitCode,
                durationMs = durationMs,
                details = details
            )
        )
    }

    fun getRecentEvents(count: Int = 50): List<AuditEvent> {
        synchronized(events) {
            return events.takeLast(count).reversed()
        }
    }

    fun getAllEvents(): List<AuditEvent> {
        synchronized(events) {
            return events.toList().reversed()
        }
    }

    fun getStats(): AuditStats {
        synchronized(events) {
            return AuditStats(
                total = events.size,
                allowed = events.count { it.decision == "ALLOWED" },
                confirmed = events.count { it.decision == "CONFIRMED" },
                denied = events.count { it.decision == "DENIED" },
                blocked = events.count { it.decision == "BLOCKED" },
                criticalCount = events.count { it.riskLevel == RiskLevel.CRITICAL },
                highCount = events.count { it.riskLevel == RiskLevel.HIGH }
            )
        }
    }

    fun clear() {
        synchronized(events) {
            events.clear()
        }
    }

    fun integrateWithExecutor() {
        CapabilityExecutor.logListener = { type, msg ->
            if (type == "EXEC" || type == "POLICY") {
                val decision = when {
                    msg.contains("DENIED") -> "DENIED"
                    msg.contains("CANCELLED") -> "BLOCKED"
                    msg.contains("OK") || msg.contains("-> OK") -> "ALLOWED"
                    else -> "INFO"
                }
                val isDenied = decision == "DENIED" || decision == "BLOCKED"
                log(
                    actor = ActorType.SYSTEM,
                    capability = type,
                    riskLevel = if (isDenied) RiskLevel.HIGH else RiskLevel.LOW,
                    decision = decision,
                    details = msg
                )
            }
        }
    }
}

data class AuditStats(
    val total: Int,
    val allowed: Int,
    val confirmed: Int,
    val denied: Int,
    val blocked: Int,
    val criticalCount: Int,
    val highCount: Int
)
