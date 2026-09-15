package com.kuzyamond.voidauditor.cache

import com.kuzyamond.voidauditor.core.ActorType
import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import com.kuzyamond.voidauditor.core.EvidenceResult
import com.kuzyamond.voidauditor.core.PolicyDecision
import com.kuzyamond.voidauditor.core.ShizukuExecutor
import com.kuzyamond.voidauditor.core.USFPipeline
import com.kuzyamond.voidauditor.core.evidence.CacheDirectoriesEvidence
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CacheScannerTest {

    @Before
    fun setUp() {
        mockkObject(CapabilityExecutor)
        coEvery { CapabilityExecutor.execute(any(), any()) } returns pipelineResult()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `discovers cache dirs directly from parsed evidence`() = runTest {
        val cacheEvidence = CacheDirectoriesEvidence(
            capabilityId = "DiscoverCacheDirectories",
            capturedAt = System.currentTimeMillis(),
            paths = listOf(
                "/data/data/com.example.app/cache",
                "/sdcard/Android/data/com.example.app/cache"
            )
        )
        stubDiscover(
            output = "ignored-by-evidence\n/dir1\n/dir2\n",
            evidence = EvidenceResult.Parsed(
                capabilityId = "DiscoverCacheDirectories",
                evidence = cacheEvidence
            )
        )

        val result = CacheScanner.discoverCacheDirs(CacheCapability.QUICK)

        assertEquals(cacheEvidence.paths, result)
    }

    @Test
    fun `falls back to command output lines when evidence paths are empty`() = runTest {
        stubDiscover(
            output = "/data/data/com.example.app/cache\n/sdcard/Android/data/com.example.app/cache",
            evidence = EvidenceResult.Parsed(
                capabilityId = "DiscoverCacheDirectories",
                evidence = CacheDirectoriesEvidence(
                    capabilityId = "DiscoverCacheDirectories",
                    capturedAt = System.currentTimeMillis(),
                    paths = emptyList()
                )
            )
        )

        val result = CacheScanner.discoverCacheDirs(CacheCapability.QUICK)

        assertEquals(
            listOf(
                "/data/data/com.example.app/cache",
                "/sdcard/Android/data/com.example.app/cache"
            ),
            result
        )
    }

    @Test
    fun `falls back to command output when evidence is null`() = runTest {
        stubDiscover(
            output = "/data/data/com.example.app/cache\n/sdcard/Android/data/com.example.app/cache\n   \n"
        )

        val result = CacheScanner.discoverCacheDirs(CacheCapability.QUICK)

        assertEquals(
            listOf(
                "/data/data/com.example.app/cache",
                "/sdcard/Android/data/com.example.app/cache"
            ),
            result
        )
    }

    @Test
    fun `deduplicates and normalizes legacy path variants preserving first seen order`() = runTest {
        stubDiscover(
            output = listOf(
                "/data/data/com.example.app/cache",
                "/data/user/0/com.example.app/cache",
                "/storage/emulated/0/Android/data/com.example2.app/cache"
            ).joinToString("\n")
        )

        val result = CacheScanner.discoverCacheDirs(CacheCapability.QUICK)

        assertEquals(
            listOf(
                "/data/data/com.example.app/cache",
                "/storage/emulated/0/Android/data/com.example2.app/cache"
            ),
            result
        )
    }

    @Test
    fun `delegates per capability roots and depth to the pipeline`() = runTest {
        val scriptContext = USFPipeline.Context(actor = ActorType.SCRIPT)

        CacheScanner.discoverCacheDirs(CacheCapability.QUICK)
        CacheScanner.discoverCacheDirs(CacheCapability.FULL)
        CacheScanner.discoverCacheDirs(CacheCapability.DEEP)
        CacheScanner.discoverCacheDirs(CacheCapability.SYSTEM_TRIM)

        coVerify(exactly = 1) {
            CapabilityExecutor.execute(
                scriptContext,
                Capability.DiscoverCacheDirectories(listOf("/data/data", "/sdcard/Android/data"), 3)
            )
        }
        coVerify(exactly = 1) {
            CapabilityExecutor.execute(
                scriptContext,
                Capability.DiscoverCacheDirectories(listOf("/data/data", "/sdcard/Android/data"), 4)
            )
        }
        coVerify(exactly = 1) {
            CapabilityExecutor.execute(
                scriptContext,
                Capability.DiscoverCacheDirectories(
                    listOf("/data/data", "/data/user_de/0", "/sdcard/Android/data"),
                    4
                )
            )
        }
        coVerify(exactly = 1) {
            CapabilityExecutor.execute(
                scriptContext,
                Capability.DiscoverCacheDirectories(emptyList(), 0)
            )
        }
    }

    private fun stubDiscover(
        roots: List<String> = listOf("/data/data", "/sdcard/Android/data"),
        maxDepth: Int = 3,
        output: String = "",
        evidence: EvidenceResult? = null
    ) {
        coEvery {
            CapabilityExecutor.execute(
                USFPipeline.Context(actor = ActorType.SCRIPT),
                Capability.DiscoverCacheDirectories(roots, maxDepth)
            )
        } returns pipelineResult(
            output = output,
            evidence = evidence,
            capability = Capability.DiscoverCacheDirectories(roots, maxDepth)
        )
    }

    private fun commandResult(output: String = ""): ShizukuExecutor.CommandResult =
        ShizukuExecutor.CommandResult(
            success = true,
            output = output,
            error = "",
            exitCode = 0,
            executionTimeMs = 0
        )

    private fun pipelineResult(
        output: String = "",
        evidence: EvidenceResult? = null,
        capability: USFPipeline.Capability =
            Capability.DiscoverCacheDirectories(listOf("/data/data", "/sdcard/Android/data"), 3)
    ): USFPipeline.Result =
        USFPipeline.Result(
            commandResult = commandResult(output),
            decision = PolicyDecision.Allowed,
            capability = capability,
            context = USFPipeline.Context(actor = ActorType.SCRIPT),
            evidence = evidence
        )
}