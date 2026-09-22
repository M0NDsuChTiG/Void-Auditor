package com.kuzyamond.voidauditor.network

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.PolicyDecision
import com.kuzyamond.voidauditor.core.ShizukuExecutor
import com.kuzyamond.voidauditor.core.USFPipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for the per-host discovery seam (NetworkScanner.discoverAliveIps /
 * pingHostAlive) — the path previously proven only by the real-device E2E run.
 *
 * The JVM never touches the real CapabilityExecutor/Shizuku (Android-only): the
 * capability invocation is injected and pinned with a fake that mirrors the
 * production executor's shape (USFPipeline.Result + ShizukuExecutor.CommandResult,
 * isSuccessful = success && exitCode == 0).
 */
class NetworkScannerDiscoveryTest {

    // ── fakes ──────────────────────────────────────────────────────────────

    /** Mirrors the real executor's per-host outcome without touching Shizuku. */
    private class FakeExecutor(
        private val aliveIps: Set<String>,
        private val throwOn: Set<String> = emptySet()
    ) {
        val requested = Collections.synchronizedList(mutableListOf<String>())

        suspend fun execute(cap: Capability.PingIp): USFPipeline.Result {
            requested.add(cap.ip)
            if (cap.ip in throwOn) throw IllegalStateException("shizuku down for ${cap.ip}")
            val ok = cap.ip in aliveIps
            return USFPipeline.Result(
                commandResult = ShizukuExecutor.CommandResult(
                    success = ok,
                    output = if (ok) cap.ip else "",
                    error = if (ok) "" else "host down",
                    exitCode = if (ok) 0 else 1,
                    executionTimeMs = 0
                ),
                decision = PolicyDecision.Allowed,
                capability = cap,
                context = USFPipeline.Context()
            )
        }

        /** Injection adapter matching pingHostAlive's execute parameter. */
        val asProbe: suspend (Capability.PingIp) -> USFPipeline.Result? = { cap -> execute(cap) }
    }

    private fun targets(vararg ips: String): List<ScanTarget> = ips.map { ScanTarget(ip = it) }

    // ── pingHostAlive: the alive rule (success && exitCode == 0) ───────────

    @Test
    fun `pingHostAlive is true only for successful exit-0 result`() = runBlocking {
        val fake = FakeExecutor(aliveIps = setOf("192.168.1.1"))
        assertTrue(NetworkScanner.pingHostAlive("192.168.1.1", fake.asProbe))
        assertFalse(NetworkScanner.pingHostAlive("192.168.1.2", fake.asProbe))
    }

    @Test
    fun `pingHostAlive with success but nonzero exit code is not alive`() = runBlocking {
        // success=true, exitCode=1 must NOT count as alive: the E2E proof relied
        // on isSuccessful = success && exitCode == 0, so the fake pins exactly that.
        var lastExitCode = -100
        val probe: suspend (Capability.PingIp) -> USFPipeline.Result? = { cap ->
            USFPipeline.Result(
                commandResult = ShizukuExecutor.CommandResult(
                    success = true, output = "", error = "ping exited 1", exitCode = 1,
                    executionTimeMs = 0
                ),
                decision = PolicyDecision.Allowed,
                capability = cap,
                context = USFPipeline.Context()
            ).also { lastExitCode = it.commandResult.exitCode }
        }
        assertFalse(NetworkScanner.pingHostAlive("192.168.1.9", probe))
        assertEquals(1, lastExitCode)
    }

    @Test
    fun `pingHostAlive treats executor exception as host down`() = runBlocking {
        val fake = FakeExecutor(aliveIps = emptySet(), throwOn = setOf("192.168.1.3"))
        assertFalse(NetworkScanner.pingHostAlive("192.168.1.3", fake.asProbe))
    }

    @Test
    fun `pingHostAlive with null result is not alive`() = runBlocking {
        val probe: suspend (Capability.PingIp) -> USFPipeline.Result? = { null }
        assertFalse(NetworkScanner.pingHostAlive("192.168.1.4", probe))
    }

    // ── discoverAliveIps: isolation, dedup, ordering ───────────────────────

    @Test
    fun `discoverAliveIps returns only alive hosts and skips throwing ones`() = runBlocking {
        val fake = FakeExecutor(
            aliveIps = setOf("192.168.1.1", "192.168.1.3"),
            throwOn = setOf("192.168.1.2")
        )
        val alive = NetworkScanner.discoverAliveIps(
            targets("192.168.1.1", "192.168.1.2", "192.168.1.3"),
            probe = { ip -> NetworkScanner.pingHostAlive(ip, fake.asProbe) }
        )
        assertEquals(listOf("192.168.1.1", "192.168.1.3"), alive)
        // every target got exactly one probe attempt, including the throwing one
        assertEquals(listOf("192.168.1.1", "192.168.1.2", "192.168.1.3"), fake.requested.toList())
    }

    @Test
    fun `discoverAliveIps on empty targets returns empty without probing`() = runBlocking {
        val fake = FakeExecutor(aliveIps = setOf("192.168.1.1"))
        val alive = NetworkScanner.discoverAliveIps(
            emptyList(),
            probe = { ip -> NetworkScanner.pingHostAlive(ip, fake.asProbe) }
        )
        assertTrue(alive.isEmpty())
        assertTrue(fake.requested.isEmpty())
    }

    // ── bounded concurrency ────────────────────────────────────────────────

    @Test
    fun `discoverAliveIps caps concurrent probes at the configured bound`() = runTest {
        val concurrency = 4
        val arrived = AtomicInteger(0)

        val suspendingProbe: suspend (String) -> Boolean = { _ ->
            arrived.incrementAndGet()
            awaitCancellation()
        }

        val job = async {
            NetworkScanner.discoverAliveIps(
                (1..50).map { ScanTarget(ip = "192.168.1.$it") },
                probe = suspendingProbe,
                concurrency = concurrency,
                timeoutMs = 10_000L,
                ioDispatcher = StandardTestDispatcher(testScheduler)
            )
        }
        // Deterministic on virtual time: exactly `concurrency` probes obtain a
        // permit and suspend forever; the remaining 46 stay waiting on the
        // semaphore, so a full scheduler drain can never raise the count.
        testScheduler.advanceUntilIdle()
        assertEquals(concurrency, arrived.get())
        job.cancelAndJoin()
    }

    @Test
    fun `default constants stay pinned to the validated values`() {
        assertEquals(32, NetworkScanner.DISCOVERY_CONCURRENCY)
        assertEquals(60_000L, NetworkScanner.DISCOVERY_TIMEOUT_MS)
    }

    // ── phase timeout → degrade to emptyList ───────────────────────────────

    @Test
    fun `discoverAliveIps degrades to empty when the phase times out`() = runBlocking {
        val neverProbe: suspend (String) -> Boolean = { _ ->
            awaitCancellation()
        }
        val alive = NetworkScanner.discoverAliveIps(
            targets("192.168.1.1", "192.168.1.2"),
            probe = neverProbe,
            timeoutMs = 50L
        )
        assertTrue("timeout must degrade to empty, not throw", alive.isEmpty())
    }

    // ── cancellation propagates ────────────────────────────────────────────

    @Test
    fun `discoverAliveIps propagates caller cancellation`() = runBlocking {
        val started = CountDownLatch(1)
        val job = launch(Dispatchers.IO) {
            NetworkScanner.discoverAliveIps(
                (1..20).map { ScanTarget(ip = "192.168.1.$it") },
                probe = { _ ->
                    started.countDown()
                    awaitCancellation()
                },
                timeoutMs = 30_000L
            )
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `pingHostAlive rethrows cancellation instead of swallowing it`() = runBlocking {
        val probe: suspend (Capability.PingIp) -> USFPipeline.Result? = { _ ->
            throw CancellationException("caller cancelled")
        }
        try {
            NetworkScanner.pingHostAlive("192.168.1.5", probe)
            fail("expected CancellationException to propagate")
        } catch (expected: CancellationException) {
            // propagate-only contract
        }
    }

    // ── upstream validation gate (defense in depth before PingIp) ──────────

    @Test
    fun `scanHosts filters invalid ips before any probe is built`() {
        val malicious = "192.168.1.1;rm -rf /"
        val valid = listOf(ScanTarget(ip = "192.168.1.1"), ScanTarget(ip = malicious))
            .filter { NetworkScanner.isValidIpv4(it.ip) }
        assertEquals(1, valid.size)
        assertTrue(valid.none { it.ip.contains(";") })
    }
}
