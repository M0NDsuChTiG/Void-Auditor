package com.kuzyamond.voidauditor.network

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import com.kuzyamond.voidauditor.core.ShizukuExecutor
import io.mockk.coEvery
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAdbTest {

    private fun okResult() = ShizukuExecutor.CommandResult(
        success = true, output = "", error = "",
        exitCode = 0, executionTimeMs = 0L
    )

    @Test
    fun `enableWifiAdb fails when getprop does not reflect the port`() = runTest {
        mockkObject(CapabilityExecutor)
        coEvery { CapabilityExecutor.execute(any<Capability>()) } answers {
            val cap = firstArg<Capability>()
            when {
                cap is Capability.ConfigureAdbTcp && cap.port == 5555 ->
                    ShizukuExecutor.CommandResult(
                        success = true, output = "[setprop] OK\n[stop adbd] OK\n[start adbd] OK\n[getprop] FAIL: exit=-1",
                        error = "VERIFY_FAILED: setprop reported success but port 5555 not active per getprop",
                        exitCode = -1, executionTimeMs = 850L
                    )
                cap is Capability.ReadSystemProp -> okResult().copy(output = "5554")
                else -> okResult()
            }
        }

        val res = NetworkAdb.enableWifiAdb(5555)

        assertTrue(res.isFailure)
        val msg = res.exceptionOrNull()?.message ?: ""
        assertTrue("expected VERIFY_FAILED, got: $msg", msg.contains("VERIFY_FAILED"))
    }

    @Test
    fun `success when setprop got reflected in getprop`() = runTest {
        mockkObject(CapabilityExecutor)
        coEvery { CapabilityExecutor.execute(any<Capability>()) } answers {
            val cap = firstArg<Capability>()
            when {
                cap is Capability.ConfigureAdbTcp && cap.port == 5555 ->
                    ShizukuExecutor.CommandResult(
                        success = true,
                        output = "[setprop] OK\n[stop adbd] OK\n[start adbd] OK\n[getprop] OK",
                        error = "",
                        exitCode = 0, executionTimeMs = 850L
                    )
                cap is Capability.ReadSystemProp -> okResult().copy(output = "5555\n")
                else -> okResult()
            }
        }

        val res = NetworkAdb.enableWifiAdb(5555)

        assertTrue(res.isSuccess)
        assertEquals("ADB over WiFi enabled on port 5555", res.getOrNull())
    }

    @Test
    fun `setprop failure surfaces SETPROP_FAILED message when error is blank`() = runTest {
        mockkObject(CapabilityExecutor)
        coEvery { CapabilityExecutor.execute(any<Capability>()) } answers {
            val cap = firstArg<Capability>()
            when (cap) {
                is Capability.ConfigureAdbTcp ->
                    ShizukuExecutor.CommandResult(
                        success = false,
                        output = "[setprop service.adb.tcp.port 5555] -> FAIL: exit=1",
                        error = "",
                        exitCode = 1, executionTimeMs = 10L
                    )
                is Capability.ReadSystemProp -> okResult()
                else -> okResult()
            }
        }

        val res = NetworkAdb.enableWifiAdb(5555)

        assertTrue(res.isFailure)
        val msg = res.exceptionOrNull()?.message ?: ""
        assertTrue("unexpected message: $msg", msg.contains("EXEC_FAILED (code 1)"))
    }

    @Test
    fun `setprop failure surfaces error text when present`() = runTest {
        mockkObject(CapabilityExecutor)
        coEvery { CapabilityExecutor.execute(any<Capability>()) } answers {
            val cap = firstArg<Capability>()
            when (cap) {
                is Capability.ConfigureAdbTcp ->
                    ShizukuExecutor.CommandResult(
                        success = false,
                        output = "[setprop] -> FAIL: Operation not permitted",
                        error = "Operation not permitted",
                        exitCode = 1, executionTimeMs = 10L
                    )
                is Capability.ReadSystemProp -> okResult()
                else -> okResult()
            }
        }

        val res = NetworkAdb.enableWifiAdb(5555)

        assertTrue(res.isFailure)
        val msg = res.exceptionOrNull()?.message ?: ""
        assertEquals("Operation not permitted", msg)
    }

    @Test
    fun `isWifiAdbEnabled returns true when port matches`() = runTest {
        mockkObject(CapabilityExecutor)
        coEvery { CapabilityExecutor.execute(any<Capability.ReadSystemProp>()) } returns
            okResult().copy(output = "5555")

        assertTrue(NetworkAdb.isWifiAdbEnabled(5555))
    }

    @Test
    fun `isWifiAdbEnabled returns false on failure`() = runTest {
        mockkObject(CapabilityExecutor)
        coEvery { CapabilityExecutor.execute(any<Capability.ReadSystemProp>()) } returns
            ShizukuExecutor.CommandResult(
                success = false, output = "", error = "DENIED",
                exitCode = -1, executionTimeMs = 0L
            )

        assertFalse(NetworkAdb.isWifiAdbEnabled(5555))
    }
}
