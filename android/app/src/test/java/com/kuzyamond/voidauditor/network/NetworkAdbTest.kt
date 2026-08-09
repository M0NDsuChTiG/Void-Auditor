package com.kuzyamond.voidauditor.network

import com.kuzyamond.voidauditor.core.ShizukuExecutor
import io.mockk.coEvery
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAdbTest {

    private fun okResult() = ShizukuExecutor.CommandResult(
        success = true, output = "", error = "",
        exitCode = 0, executionTimeMs = 0L
    )

    @Test
    fun `enableWifiAdb fails when getprop does not reflect the port`() = runTest {
        mockkObject(ShizukuExecutor)
        coEvery { ShizukuExecutor.executeCommand(any(), any()) } returns okResult()
        coEvery { ShizukuExecutor.executeCommand("getprop service.adb.tcp.port", any()) } returns
            ShizukuExecutor.CommandResult(
                success = true, output = "5554", error = "",
                exitCode = 0, executionTimeMs = 0L
            )

        val res = NetworkAdb.enableWifiAdb(5555)

        assertTrue(res.isFailure)
        val msg = res.exceptionOrNull()?.message ?: ""
        assertTrue("expected VERIFY_FAILED, got: $msg", msg.contains("VERIFY_FAILED"))
    }

    @Test
    fun `success when setprop got reflected in getprop`() = runTest {
        mockkObject(ShizukuExecutor)
        coEvery { ShizukuExecutor.executeCommand(any(), any()) } returns okResult()
        coEvery { ShizukuExecutor.executeCommand("getprop service.adb.tcp.port", any()) } returns
            ShizukuExecutor.CommandResult(
                success = true, output = "5555\n", error = "",
                exitCode = 0, executionTimeMs = 0L
            )

        val res = NetworkAdb.enableWifiAdb(5555)

        assertTrue(res.isSuccess)
    }

    @Test
    fun `setprop failure surfaces SETPROP_FAILED message when shizuku error is blank`() = runTest {
        mockkObject(ShizukuExecutor)
        coEvery { ShizukuExecutor.executeCommand(any(), any()) } returns
            ShizukuExecutor.CommandResult(
                success = false, output = "", error = "",
                exitCode = 1, executionTimeMs = 0L
            )

        val res = NetworkAdb.enableWifiAdb(5555)

        assertTrue(res.isFailure)
        val msg = res.exceptionOrNull()?.message ?: ""
        assertTrue("unexpected message: $msg", msg.contains("SETPROP_FAILED (code 1)"))
    }

    @Test
    fun `setprop failure surfaces shizuku error text when present`() = runTest {
        mockkObject(ShizukuExecutor)
        coEvery { ShizukuExecutor.executeCommand(any(), any()) } returns
            ShizukuExecutor.CommandResult(
                success = false, output = "", error = "Operation not permitted",
                exitCode = 1, executionTimeMs = 0L
            )

        val res = NetworkAdb.enableWifiAdb(5555)

        assertTrue(res.isFailure)
        val msg = res.exceptionOrNull()?.message ?: ""
        assertEquals("Operation not permitted", msg)
    }
}