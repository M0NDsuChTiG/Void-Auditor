package com.kuzyamond.voidauditor

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesScreenCapabilityTest {

    @Test
    fun `ListDirectory has risk score 15`() {
        val cap = Capability.ListDirectory("/sdcard/")
        assertEquals(15, cap.riskScore())
    }

    @Test
    fun `CalculateDiskUsage has risk score 10`() {
        val cap = Capability.CalculateDiskUsage("/sdcard/")
        assertEquals(10, cap.riskScore())
    }

    @Test
    fun `ReadFile has risk score 40`() {
        val cap = Capability.ReadFile("/sdcard/test.txt")
        assertEquals(40, cap.riskScore())
    }

    @Test
    fun `ListDirectory does not require confirmation`() {
        val cap = Capability.ListDirectory("/sdcard/")
        assertFalse(cap.requiresConfirmation())
    }

    @Test
    fun `CalculateDiskUsage does not require confirmation`() {
        val cap = Capability.CalculateDiskUsage("/sdcard/")
        assertFalse(cap.requiresConfirmation())
    }

    @Test
    fun `ReadFile requires confirmation`() {
        val cap = Capability.ReadFile("/sdcard/test.txt")
        assertTrue(cap.requiresConfirmation())
    }

    @Test
    fun `ListDirectory command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.ListDirectory("/data/local/tmp/"))
        assertEquals("ls -l /data/local/tmp/", cmd)
    }

    @Test
    fun `CalculateDiskUsage command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.CalculateDiskUsage("/sdcard/"))
        assertEquals("du -sh /sdcard/", cmd)
    }

    @Test
    fun `ReadFile command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.ReadFile("/sdcard/test.txt"))
        assertEquals("cat /sdcard/test.txt", cmd)
    }

    @Test
    fun `ListDirectory does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.ListDirectory("/"))
        assertFalse("capabilityToCommand must not return null for ListDirectory", cmd.isNullOrEmpty())
    }

    @Test
    fun `CalculateDiskUsage does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.CalculateDiskUsage("/"))
        assertFalse("capabilityToCommand must not return null for CalculateDiskUsage", cmd.isNullOrEmpty())
    }

    @Test
    fun `ReadFile does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.ReadFile("/"))
        assertFalse("capabilityToCommand must not return null for ReadFile", cmd.isNullOrEmpty())
    }

    @Test
    fun `capabilityToCommand returns non-null for all FilesScreen capabilities`() {
        val capabilities = listOf(
            Capability.ListDirectory("/sdcard/"),
            Capability.CalculateDiskUsage("/sdcard/"),
            Capability.ReadFile("/sdcard/test.txt")
        )
        capabilities.forEach { cap ->
            val cmd = CapabilityExecutor.capabilityToCommand(cap)
            assertFalse(
                "capabilityToCommand returned null for ${cap::class.simpleName}",
                cmd.isNullOrEmpty()
            )
        }
    }
}
