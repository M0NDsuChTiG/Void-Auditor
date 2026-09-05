package com.kuzyamond.voidauditor.network

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import com.kuzyamond.voidauditor.core.PolicyDecision
import com.kuzyamond.voidauditor.core.ShizukuExecutor
import com.kuzyamond.voidauditor.core.USFPipeline
import io.mockk.coEvery
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the current NetworkAdb implementation.
 *
 * Current API:
 * - isWifiAdbEnabled(): Boolean — no parameters, checks modern adb_wifi_enabled
 *   setting first, then falls back to legacy service.adb.tcp.port.
 * - openDeveloperSettingsIntent(): Intent — returns an ACTION_APPLICATION_DEVELOPMENT_SETTINGS intent.
 *
 * The old enableWifiAdb() function has been removed because VOID Auditor cannot
 * programmatically enable Wireless Debugging. Tests for that function are intentionally
 * omitted.
 */
class NetworkAdbTest {

    private fun okResult() = ShizukuExecutor.CommandResult(
        success = true, output = "", error = "",
        exitCode = 0, executionTimeMs = 0L
    )

    private fun okPipelineResult(capability: Capability = Capability.ReadSetting("global", "adb_wifi_enabled")) = USFPipeline.Result(
        commandResult = okResult(),
        decision = PolicyDecision.Allowed,
        capability = capability,
        context = USFPipeline.Context()
    )

    // --- isWifiAdbEnabled() ---

    @Test
    fun `isWifiAdbEnabled returns true when modern setting is 1`() = runTest {
        mockkObject(CapabilityExecutor)
        coEvery { CapabilityExecutor.execute(any(), any()) } answers {
            val context = firstArg<USFPipeline.Context>()
            val cap = secondArg<Capability>()
            when (cap) {
                is Capability.ReadSetting ->
                    USFPipeline.Result(
                        commandResult = okResult().copy(output = "1\n"),
                        decision = PolicyDecision.Allowed,
                        capability = cap,
                        context = context
                    )
                else -> okPipelineResult()
            }
        }

        assertTrue(NetworkAdb.isWifiAdbEnabled())
    }

    @Test
    fun `isWifiAdbEnabled returns true when legacy TCP port is set`() = runTest {
        mockkObject(CapabilityExecutor)
        coEvery { CapabilityExecutor.execute(any(), any()) } answers {
            val context = firstArg<USFPipeline.Context>()
            val cap = secondArg<Capability>()
            when (cap) {
                is Capability.ReadSetting ->
                    // Modern setting returns empty or "0"
                    USFPipeline.Result(
                        commandResult = okResult().copy(output = "0\n"),
                        decision = PolicyDecision.Allowed,
                        capability = cap,
                        context = context
                    )
                is Capability.ReadSystemProp ->
                    USFPipeline.Result(
                        commandResult = okResult().copy(output = "5555\n"),
                        decision = PolicyDecision.Allowed,
                        capability = cap,
                        context = context
                    )
                else -> okPipelineResult()
            }
        }

        assertTrue(NetworkAdb.isWifiAdbEnabled())
    }

    @Test
    fun `isWifiAdbEnabled returns false when both modern and legacy are inactive`() = runTest {
        mockkObject(CapabilityExecutor)
        coEvery { CapabilityExecutor.execute(any(), any()) } answers {
            val context = firstArg<USFPipeline.Context>()
            val cap = secondArg<Capability>()
            when (cap) {
                is Capability.ReadSetting ->
                    USFPipeline.Result(
                        commandResult = okResult().copy(output = "0\n"),
                        decision = PolicyDecision.Allowed,
                        capability = cap,
                        context = context
                    )
                is Capability.ReadSystemProp ->
                    USFPipeline.Result(
                        commandResult = okResult().copy(output = "0\n"),
                        decision = PolicyDecision.Allowed,
                        capability = cap,
                        context = context
                    )
                else -> okPipelineResult()
            }
        }

        assertFalse(NetworkAdb.isWifiAdbEnabled())
    }

    @Test
    fun `isWifiAdbEnabled returns false when both queries fail`() = runTest {
        mockkObject(CapabilityExecutor)
        coEvery { CapabilityExecutor.execute(any(), any()) } answers {
            val context = firstArg<USFPipeline.Context>()
            val cap = secondArg<Capability>()
            when (cap) {
                is Capability.ReadSetting ->
                    USFPipeline.Result(
                        commandResult = ShizukuExecutor.CommandResult(
                            success = false, output = "", error = "DENIED",
                            exitCode = -1, executionTimeMs = 0L
                        ),
                        decision = PolicyDecision.Denied("DENIED"),
                        capability = cap,
                        context = context
                    )
                is Capability.ReadSystemProp ->
                    USFPipeline.Result(
                        commandResult = ShizukuExecutor.CommandResult(
                            success = false, output = "", error = "DENIED",
                            exitCode = -1, executionTimeMs = 0L
                        ),
                        decision = PolicyDecision.Denied("DENIED"),
                        capability = cap,
                        context = context
                    )
                else -> okPipelineResult()
            }
        }

        assertFalse(NetworkAdb.isWifiAdbEnabled())
    }

    @Test
    fun `isWifiAdbEnabled returns false when legacy port is 0`() = runTest {
        mockkObject(CapabilityExecutor)
        coEvery { CapabilityExecutor.execute(any(), any()) } answers {
            val context = firstArg<USFPipeline.Context>()
            val cap = secondArg<Capability>()
            when (cap) {
                is Capability.ReadSetting ->
                    USFPipeline.Result(
                        commandResult = okResult().copy(output = ""),
                        decision = PolicyDecision.Allowed,
                        capability = cap,
                        context = context
                    )
                is Capability.ReadSystemProp ->
                    USFPipeline.Result(
                        commandResult = okResult().copy(output = "0\n"),
                        decision = PolicyDecision.Allowed,
                        capability = cap,
                        context = context
                    )
                else -> okPipelineResult()
            }
        }

        assertFalse(NetworkAdb.isWifiAdbEnabled())
    }

    @Test
    fun `isWifiAdbEnabled returns false when legacy port is -1`() = runTest {
        mockkObject(CapabilityExecutor)
        coEvery { CapabilityExecutor.execute(any(), any()) } answers {
            val context = firstArg<USFPipeline.Context>()
            val cap = secondArg<Capability>()
            when (cap) {
                is Capability.ReadSetting ->
                    USFPipeline.Result(
                        commandResult = okResult().copy(output = ""),
                        decision = PolicyDecision.Allowed,
                        capability = cap,
                        context = context
                    )
                is Capability.ReadSystemProp ->
                    USFPipeline.Result(
                        commandResult = okResult().copy(output = "-1\n"),
                        decision = PolicyDecision.Allowed,
                        capability = cap,
                        context = context
                    )
                else -> okPipelineResult()
            }
        }

        assertFalse(NetworkAdb.isWifiAdbEnabled())
    }

// openDeveloperSettingsIntent() tests require Android framework (instrumented tests).
}
