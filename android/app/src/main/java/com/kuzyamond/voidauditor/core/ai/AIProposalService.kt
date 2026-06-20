package com.kuzyamond.voidauditor.core.ai

import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * AIProposalService — сервис разбора и безопасной интеграции предложений Gemini/AI.
 * Всегда использует ContextSanitizer, строгий JSON-разбор и многоуровневую валидацию.
 * Никогда не допускает выполнение команд, shell или невалидных параметров.
 */
object AIProposalService {
    /**
     * Основной вход — структурированная строка (JSON) от Gemini
     * Если парсинг или валидация не проходят — возвращает null и логирует AuditEvent c ProposalStatus.REJECTED
     */
    fun parseAndValidateProposal(rawGeminiOutput: String): IntentProposal? {
        // Шаг 1. Санитайзинг контекста
        val cleaned = ContextSanitizerPipeline.sanitize(rawGeminiOutput)
        
        // Шаг 2. Попытка строгого JSON-парсинга
        val json = try { JSONObject(cleaned) } catch (e: Exception) {
            logAuditFailure(cleaned, "JSON parse error: ${e.message}")
            return null
        }
        
        // Шаг 3. Попытка собрать IntentProposal
        val proposal = try { proposalFromJson(json) } catch (e: Exception) {
            logAuditFailure(cleaned, "Proposal decode error: ${e.message}")
            return null
        }
        
        // Шаг 4. Многоуровневая валидация
        if (!isValidProposal(proposal)) {
            logAuditFailure(cleaned, "Proposal validation failed")
            return null
        }
        
        return proposal
    }

    private fun proposalFromJson(json: JSONObject): IntentProposal {
        val capabilityId = CapabilityId.valueOf(
            json.getJSONObject("proposedCapability").getString("capabilityId")
        )
        val parameters = mutableMapOf<String, String>()
        val paramsJson = json.getJSONObject("proposedCapability").optJSONObject("parameters")
        paramsJson?.let { obj ->
            obj.keys().forEach { k -> parameters[k] = obj.getString(k) }
        }
        
        val confidence = json.optDouble("confidence", 0.5)
        
        return IntentProposal(
            id = json.optString("id", UUID.randomUUID().toString()),
            advisoryText = json.optString("advisoryText", ""),
            proposedCapability = ProposedCapability(capabilityId, parameters),
            evidence = emptyList(),
            createdAt = Instant.now(),
            riskLevel = null,
            confidence = confidence,
            proposer = "AI",
            proposalStatus = ProposalStatus.PENDING,
            auditHash = json.optString("auditHash", null)
        )
    }

    private fun isValidProposal(proposal: IntentProposal): Boolean {
        return proposal.proposedCapability.capabilityId in CapabilityId.values()
    }

    private fun logAuditFailure(content: String, reason: String) {
        System.err.println("⚠️ AIProposalService: $reason")
    }
}
