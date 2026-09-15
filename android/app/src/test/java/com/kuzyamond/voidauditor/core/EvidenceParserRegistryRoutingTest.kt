package com.kuzyamond.voidauditor.core

import com.kuzyamond.voidauditor.core.evidence.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression tests for EvidenceParserRegistry routing.
 *
 * The registry map is keyed by capability *class simple name* (e.g. "PingSweep"),
 * while capability.description is a human label (e.g. "Ping sweep: 2 hosts").
 * Lookup must therefore use capability::class.simpleName — a lookup by
 * description silently returned null evidence for every capability, which
 * broke NetworkScanner host discovery and CacheScanner typed evidence.
 */
class EvidenceParserRegistryRoutingTest {

    private val parser = DefaultEvidenceParser()

    private fun okResult(output: String) = ShizukuExecutor.CommandResult(
        success = true,
        output = output,
        error = "",
        exitCode = 0,
        executionTimeMs = 10
    )

    @Test
    fun pingSweepRoutesToPingSweepParserAndProducesPingSweepEvidence() {
        val cap = Capability.PingSweep(listOf("13.13.213.111", "13.13.213.117"))
        val result = okResult("13.13.213.111\n13.13.213.117\n")

        val parsed = parser.parse(cap, result) as? EvidenceResult.Parsed
        val evidence = parsed?.evidence

        assertNotNull("PingSweep evidence must not be null (registry must resolve by class simple name)", evidence)
        assertEquals("PingSweep", (evidence as? PingSweepEvidence)?.capabilityId)
        assertEquals(listOf("13.13.213.111", "13.13.213.117"), (evidence as PingSweepEvidence).aliveHosts)
    }

    @Test
    fun discoverCacheDirectoriesRoutesToCacheDirectoriesParserAndProducesCacheDirectoriesEvidence() {
        val cap = Capability.DiscoverCacheDirectories(listOf("/data/data", "/sdcard/Android/data"), 4)
        val result = okResult("/data/data/com.example.a/cache\n/sdcard/Android/data/com.example.b/cache\n")

        val parsed = parser.parse(cap, result) as? EvidenceResult.Parsed
        val evidence = parsed?.evidence

        assertNotNull("DiscoverCacheDirectories evidence must not be null", evidence)
        assertEquals("DiscoverCacheDirectories", (evidence as? CacheDirectoriesEvidence)?.capabilityId)
        assertEquals(
            listOf("/data/data/com.example.a/cache", "/sdcard/Android/data/com.example.b/cache"),
            (evidence as CacheDirectoriesEvidence).paths
        )
    }

    @Test
    fun unregisteredCapabilityYieldsNullEvidence() {
        val cap = Capability.ExecuteArbitraryShell("echo hi")
        assertNull(parser.parse(cap, okResult("hi")))
    }

    @Test
    fun failedCommandYieldsNullEvidenceRegardlessOfRouting() {
        val cap = Capability.PingSweep(listOf("13.13.213.111"))
        val failed = ShizukuExecutor.CommandResult(
            success = false,
            output = "",
            error = "CODE_1: boom",
            exitCode = 1,
            executionTimeMs = 10
        )
        assertNull(parser.parse(cap, failed))
    }
}
