package com.kuzyamond.voidauditor.core.ai

import com.kuzyamond.voidauditor.core.Capability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalExecutionTest {

    @Test
    fun `extractProposalJson parses PROPOSAL_JSON marker block`() {
        val text = "Ответ от ИИ...\nPROPOSAL_JSON: " +
            "{\"proposedCapability\":{\"capabilityId\":\"LIST_PACKAGES\",\"parameters\":{\"filter\":\"-3\"}}," +
            "\"advisoryText\":\"Показать сторонние приложения\",\"confidence\":0.9}"

        val json = extractProposalJson(text)
        assertTrue("JSON не извлечён", json != null && json.startsWith("{"))

        val proposal = AIProposalService.parseAndValidateProposal(json!!)
        assertEquals(CapabilityId.LIST_PACKAGES, proposal?.proposedCapability?.capabilityId)
        assertEquals("-3", proposal?.proposedCapability?.parameters?.get("filter"))
    }

    @Test
    fun `extractProposalJson handles braces inside string values`() {
        val text = "PROPOSAL_JSON: {\"proposedCapability\":{\"capabilityId\":\"INSPECT_FILE\"," +
            "\"parameters\":{\"path\":\"/data/a{b}c.txt\"}},\"advisoryText\":\"x}\",\"confidence\":0.5}"

        val json = extractProposalJson(text)
        val proposal = AIProposalService.parseAndValidateProposal(json!!)
        assertEquals("/data/a{b}c.txt", proposal?.proposedCapability?.parameters?.get("path"))
        assertEquals("x}", proposal?.advisoryText)
    }

    @Test
    fun `extractProposalJson parses fenced json block`() {
        val text = "Рекомендация...\n```json\n" +
            "{\"proposedCapability\":{\"capabilityId\":\"ANALYZE_LOG\",\"parameters\":{\"path\":\"/data/logs/x.log\"}}," +
            "\"advisoryText\":\"Анализ лога\"}\n```"

        val json = extractProposalJson(text)
        val proposal = AIProposalService.parseAndValidateProposal(json!!)
        assertEquals(CapabilityId.ANALYZE_LOG, proposal?.proposedCapability?.capabilityId)
        assertEquals("/data/logs/x.log", proposal?.proposedCapability?.parameters?.get("path"))
    }

    @Test
    fun `extractProposalJson returns null without proposal`() {
        assertNull(extractProposalJson("Просто текстовый ответ без JSON"))
        assertNull(extractProposalJson(""))
    }

    @Test
    fun `parseAndValidateProposal rejects unknown capability id`() {
        val json = "{\"proposedCapability\":{\"capabilityId\":\"DELETE_EVERYTHING\",\"parameters\":{}}}"
        assertNull(AIProposalService.parseAndValidateProposal(json))
    }

    @Test
    fun `proposalToCapability maps whitelisted ids`() {
        val listProposal = AIProposalService.parseAndValidateProposal(
            "{\"proposedCapability\":{\"capabilityId\":\"LIST_PACKAGES\",\"parameters\":{\"filter\":\"-3\"}}}"
        )!!
        assertTrue(proposalToCapability(listProposal) is Capability.QueryPackages)

        val fileProposal = AIProposalService.parseAndValidateProposal(
            "{\"proposedCapability\":{\"capabilityId\":\"INSPECT_FILE\",\"parameters\":{\"path\":\"/data/x.log\"}}}"
        )!!
        val cap = proposalToCapability(fileProposal)
        assertTrue(cap is Capability.ReadFile)
        assertEquals("/data/x.log", (cap as Capability.ReadFile).path)
    }

    @Test
    fun `proposalToCapability returns null when params missing`() {
        val proposal = AIProposalService.parseAndValidateProposal(
            "{\"proposedCapability\":{\"capabilityId\":\"INSPECT_FILE\",\"parameters\":{}}}"
        )!!
        assertNull(proposalToCapability(proposal))
    }
}
