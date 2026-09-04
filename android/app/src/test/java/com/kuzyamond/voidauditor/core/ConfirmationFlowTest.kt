package com.kuzyamond.voidauditor.core

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for the P0 confirmation-flow defect:
 *
 * Previously CapabilityExecutor.requestConfirmation only updated UI state and did
 * NOT suspend the executing coroutine — `confirmed` was checked synchronously before
 * the user could press EXECUTE, so every high-risk operation returned CANCELLED
 * even after the user confirmed.
 *
 * The fix routes confirmation through [ConfirmationManager.awaitConfirmation], which
 * suspends on a CompletableDeferred until the user resolves the request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmationFlowTest {

    @Before
    fun setUp() {
        ConfirmationManager.dismiss()
    }

    @After
    fun tearDown() {
        ConfirmationManager.dismiss()
        unmockkAll()
    }

    // --- Manager-level: awaitConfirmation synchronization contract ---

    @Test
    fun `confirmation request suspends until EXECUTE then resumes with true`() = runTest {
        var result: Boolean? = null
        val job = launch {
            result = ConfirmationManager.awaitConfirmation(Capability.ForceStopPackage("com.example.app"))
        }
        runCurrent()
        assertNotNull("confirmation request must be published while awaiting", ConfirmationManager.currentRequest)

        // Simulate user pressing EXECUTE (ConfirmationDialog confirmButton onClick).
        ConfirmationManager.confirm()

        job.join()
        assertEquals(true, result)
    }

    @Test
    fun `CANCEL resumes with false`() = runTest {
        var result: Boolean? = null
        val job = launch {
            result = ConfirmationManager.awaitConfirmation(Capability.ForceStopPackage("com.example.app"))
        }
        runCurrent()
        assertNotNull(ConfirmationManager.currentRequest)

        ConfirmationManager.cancel()

        job.join()
        assertEquals(false, result)
    }

    @Test
    fun `pending request is cleared after EXECUTE`() = runTest {
        val job = launch {
            ConfirmationManager.awaitConfirmation(Capability.ForceStopPackage("com.example.app"))
        }
        runCurrent()
        assertNotNull(ConfirmationManager.currentRequest)
        ConfirmationManager.confirm()
        job.join()
        assertNull("pending request must be cleared after EXECUTE", ConfirmationManager.currentRequest)
    }

    @Test
    fun `pending request is cleared after CANCEL`() = runTest {
        val job = launch {
            ConfirmationManager.awaitConfirmation(Capability.ForceStopPackage("com.example.app"))
        }
        runCurrent()
        assertNotNull(ConfirmationManager.currentRequest)
        ConfirmationManager.cancel()
        job.join()
        assertNull("pending request must be cleared after CANCEL", ConfirmationManager.currentRequest)
    }

    @Test
    fun `repeated completion cannot resolve the same request twice`() = runTest {
        var result: Boolean? = null
        val job = launch {
            result = ConfirmationManager.awaitConfirmation(Capability.ForceStopPackage("com.example.app"))
        }
        runCurrent()
        assertNotNull(ConfirmationManager.currentRequest)

        // EXECUTE pressed, then a stray duplicate EXECUTE + CANCEL arrive.
        ConfirmationManager.confirm()
        ConfirmationManager.confirm()
        ConfirmationManager.cancel()

        job.join()
        assertEquals("first resolution must win", true, result)
        assertNull(ConfirmationManager.currentRequest)
    }

    @Test
    fun `coroutine cancellation clears pending request`() = runTest {
        val job = launch {
            ConfirmationManager.awaitConfirmation(Capability.ForceStopPackage("com.example.app"))
        }
        runCurrent()
        assertNotNull("request must be published before cancellation", ConfirmationManager.currentRequest)

        job.cancelAndJoin()
        assertNull("pending request must be cleared when awaiting coroutine is cancelled", ConfirmationManager.currentRequest)
    }

    @Test
    fun `new confirmation request cancels the previously pending one`() = runTest {
        var firstResult: Boolean? = null
        var secondResult: Boolean? = null
        val firstJob = launch {
            firstResult = ConfirmationManager.awaitConfirmation(Capability.ForceStopPackage("com.example.app"))
        }
        runCurrent()
        assertNotNull(ConfirmationManager.currentRequest)

        val secondJob = launch {
            secondResult = ConfirmationManager.awaitConfirmation(Capability.ClearAppData("com.example.app"))
        }
        runCurrent()
        firstJob.join()
        assertEquals("superseded request must resolve as false", false, firstResult)

        // Second request is now the active one.
        assertNotNull(ConfirmationManager.currentRequest)
        ConfirmationManager.confirm()
        secondJob.join()
        assertEquals(true, secondResult)
    }

    @Test
    fun `double confirmation waits for both EXECUTE presses`() = runTest {
        var first: Boolean? = null
        var second: Boolean? = null
        val job = launch {
            first = ConfirmationManager.awaitConfirmation(Capability.UninstallPackage("com.example.app"))
            second = if (first == true) {
                ConfirmationManager.awaitConfirmation(Capability.UninstallPackage("com.example.app"))
            } else {
                false
            }
        }
        runCurrent()
        assertNotNull("first confirmation must be requested", ConfirmationManager.currentRequest)
        ConfirmationManager.confirm()
        runCurrent()
        assertEquals("first await must complete after first EXECUTE", true, first)

        // Second confirmation must be requested before proceeding.
        assertNotNull("second confirmation must be requested for double confirmation", ConfirmationManager.currentRequest)
        assertNull("second await must still be suspended", second)

        ConfirmationManager.confirm()
        job.join()
        assertEquals(true, second)
    }

    // --- Executor-level: capability executes ONLY after confirmation ---

    @Test
    fun `capability executes only after EXECUTE`() = runTest {
        mockkObject(ShizukuExecutor)
        coEvery { ShizukuExecutor.executeCommand(any(), any()) } returns okCommandResult()

        var commandResult: ShizukuExecutor.CommandResult? = null
        val job = launch {
            commandResult = CapabilityExecutor.execute(
                USFPipeline.Context(),
                Capability.DisablePackage("com.example.app") // risk 60 → RequireConfirmation
            ).commandResult
        }
        runCurrent()

        coVerify(exactly = 0) { ShizukuExecutor.executeCommand(any(), any()) }
        assertNotNull("DisablePackage (risk 60) must require confirmation", ConfirmationManager.currentRequest)

        ConfirmationManager.confirm()
        job.join()

        assertNotNull(commandResult)
        assertTrue("command must succeed after EXECUTE", commandResult!!.isSuccessful)
        coVerify(exactly = 1) { ShizukuExecutor.executeCommand("pm disable-user --user 0 com.example.app", any()) }
    }

    @Test
    fun `CANCEL prevents capability execution`() = runTest {
        mockkObject(ShizukuExecutor)
        coEvery { ShizukuExecutor.executeCommand(any(), any()) } returns okCommandResult()

        var result: USFPipeline.Result? = null
        val job = launch {
            result = CapabilityExecutor.execute(
                USFPipeline.Context(),
                Capability.DisablePackage("com.example.app")
            )
        }
        runCurrent()
        assertNotNull(ConfirmationManager.currentRequest)

        ConfirmationManager.cancel()
        job.join()

        assertNotNull(result)
        assertEquals("CANCELLED", result!!.commandResult.error)
        assertFalse(result!!.commandResult.isSuccessful)
        coVerify(exactly = 0) { ShizukuExecutor.executeCommand(any(), any()) }
    }

    @Test
    fun `coroutine cancellation while awaiting does not execute capability`() = runTest {
        mockkObject(ShizukuExecutor)
        coEvery { ShizukuExecutor.executeCommand(any(), any()) } returns okCommandResult()

        val job = launch {
            CapabilityExecutor.execute(
                USFPipeline.Context(),
                Capability.DisablePackage("com.example.app")
            )
        }
        runCurrent()
        assertNotNull("confirmation must be requested", ConfirmationManager.currentRequest)

        job.cancelAndJoin()
        assertNull("pending request must be cleared on cancellation", ConfirmationManager.currentRequest)
        coVerify(exactly = 0) { ShizukuExecutor.executeCommand(any(), any()) }
    }

    @Test
    fun `double confirmation executes only after two EXECUTE presses`() = runTest {
        mockkObject(ShizukuExecutor)
        coEvery { ShizukuExecutor.executeCommand(any(), any()) } returns okCommandResult()

        var result: USFPipeline.Result? = null
        val job = launch {
            result = CapabilityExecutor.execute(
                USFPipeline.Context(),
                Capability.UninstallPackage("com.example.app") // risk 85 → RequireDoubleConfirmation
            )
        }
        runCurrent()
        coVerify(exactly = 0) { ShizukuExecutor.executeCommand(any(), any()) }

        // First confirmation.
        assertNotNull("first confirmation required", ConfirmationManager.currentRequest)
        ConfirmationManager.confirm()
        runCurrent()

        // Second confirmation must appear and must be confirmed.
        assertNotNull("second confirmation required for risk-85 capability", ConfirmationManager.currentRequest)
        ConfirmationManager.confirm()
        job.join()

        assertNotNull(result)
        assertTrue(result!!.commandResult.isSuccessful)
        coVerify(exactly = 1) { ShizukuExecutor.executeCommand("pm uninstall com.example.app", any()) }
    }

    @Test
    fun `double confirmation cancelled on second press does not execute`() = runTest {
        mockkObject(ShizukuExecutor)
        coEvery { ShizukuExecutor.executeCommand(any(), any()) } returns okCommandResult()

        var result: USFPipeline.Result? = null
        val job = launch {
            result = CapabilityExecutor.execute(
                USFPipeline.Context(),
                Capability.UninstallPackage("com.example.app")
            )
        }
        runCurrent()
        ConfirmationManager.confirm()
        runCurrent()

        assertNotNull("second confirmation required", ConfirmationManager.currentRequest)
        ConfirmationManager.cancel()
        job.join()

        assertNotNull(result)
        assertEquals("CANCELLED", result!!.commandResult.error)
        coVerify(exactly = 0) { ShizukuExecutor.executeCommand(any(), any()) }
    }

    @Test
    fun `cancellation while waiting propagates CancellationException`() = runTest {
        var thrown: CancellationException? = null
        val job = launch {
            try {
                ConfirmationManager.awaitConfirmation(Capability.ForceStopPackage("com.example.app"))
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }
        runCurrent()
        assertNotNull(ConfirmationManager.currentRequest)
        job.cancelAndJoin()
        assertNotNull("awaitConfirmation must propagate cancellation", thrown)
        assertNull(ConfirmationManager.currentRequest)
    }

    private fun okCommandResult() = ShizukuExecutor.CommandResult(
        success = true, output = "Success", error = "",
        exitCode = 0, executionTimeMs = 10L
    )
}
