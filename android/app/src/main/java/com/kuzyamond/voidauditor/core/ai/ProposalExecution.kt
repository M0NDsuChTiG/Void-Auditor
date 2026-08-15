package com.kuzyamond.voidauditor.core.ai

import com.kuzyamond.voidauditor.core.Capability

/**
 * ProposalExecution — мост между AI Intent Proposal и исполняемыми Capability.
 *
 * Извлекает строгий JSON-блок предложения из ответа Gemini и маппит его
 * в whitelisted [Capability] для последующего исполнения через
 * PolicyEngine / ConfirmationManager / AuditLogger.
 * Никакие shell-команды от AI здесь не допускаются.
 */

/** Извлекает JSON-объект предложения из ответа Gemini (маркер PROPOSAL_JSON: или ```json блок). */
fun extractProposalJson(text: String): String? {
    if (text.isBlank()) return null

    val markerIdx = text.indexOf("PROPOSAL_JSON")
    val searchFrom = if (markerIdx >= 0) markerIdx else 0
    val braceIdx = text.indexOf('{', searchFrom)
    if (braceIdx >= 0) {
        extractJsonObject(text, braceIdx)?.let { return it }
    }

    // Fallback: блок ```json ... ```
    val fence = Regex("```(?:json)?[\\s\\r\\n]*", RegexOption.IGNORE_CASE)
    val match = fence.find(text) ?: return null
    val braceAfterFence = text.indexOf('{', match.range.last + 1)
    return if (braceAfterFence >= 0) extractJsonObject(text, braceAfterFence) else null
}

/**
 * Собирает JSON-объект начиная с открывающей `{`, корректно пропуская
 * фигурные скобки внутри строковых литералов (например в путях или тексте).
 */
private fun extractJsonObject(text: String, start: Int): String? {
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until text.length) {
        val c = text[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
            continue
        }
        when (c) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return text.substring(start, i + 1)
            }
        }
    }
    return null
}

/**
 * Маппит валидированный [IntentProposal] в исполняемый [Capability].
 * Возвращает null, если capability не входит в whitelist или не хватает параметров
 * (такое предложение должно быть отклонено).
 */
fun proposalToCapability(proposal: IntentProposal): Capability? {
    val params = proposal.proposedCapability.parameters
    return when (proposal.proposedCapability.capabilityId) {
        CapabilityId.LIST_PACKAGES -> Capability.QueryPackages(filter = params["filter"] ?: "all")
        CapabilityId.COLLECT_LOGS -> Capability.DumpService(service = params["service"] ?: "logcat")
        CapabilityId.ANALYZE_LOG -> params["path"]?.let { Capability.ReadFile(it) }
        CapabilityId.INSPECT_FILE -> params["path"]?.let { Capability.ReadFile(it) }
        CapabilityId.GENERATE_REPORT -> Capability.ReadSystemProp("ro.build.version.release")
    }
}
