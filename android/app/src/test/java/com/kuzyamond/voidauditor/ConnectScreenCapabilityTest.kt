package com.kuzyamond.voidauditor

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectScreenCapabilityTest {

    // ── riskScore ──────────────────────────────────────────────

    @Test
    fun `AdbConnect has risk score 50`() {
        val cap = Capability.AdbConnect("192.168.1.100", 5555)
        assertEquals(50, cap.riskScore)
    }

    @Test
    fun `AdbScanDevices has risk score 10`() {
        val cap = Capability.AdbScanDevices
        assertEquals(10, cap.riskScore)
    }

    // ── riskScore (confirmation threshold) ────────────────────────

    @Test
    fun `AdbConnect risk score is 50 (ACTION tier, confirmation expected)`() {
        val cap = Capability.AdbConnect("192.168.1.100", 5555)
        assertEquals(50, cap.riskScore)
    }

    @Test
    fun `AdbScanDevices risk score is 10 (READ tier, no confirmation)`() {
        val cap = Capability.AdbScanDevices
        assertEquals(10, cap.riskScore)
    }

    // ── capabilityToCommand ────────────────────────────────────

    @Test
    fun `AdbConnect command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.AdbConnect("192.168.1.100", 5555))
        assertEquals("adb connect 192.168.1.100:5555", cmd)
    }

    @Test
    fun `AdbConnect command with different ip and port`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.AdbConnect("10.0.0.5", 5037))
        assertEquals("adb connect 10.0.0.5:5037", cmd)
    }

    @Test
    fun `AdbScanDevices command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.AdbScanDevices)
        assertEquals("adb devices", cmd)
    }

    // ── non-null checks ───────────────────────────────────────

    @Test
    fun `AdbConnect does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.AdbConnect("192.168.1.100", 5555))
        assertFalse("capabilityToCommand must not return null for AdbConnect", cmd.isNullOrEmpty())
    }

    @Test
    fun `AdbScanDevices does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.AdbScanDevices)
        assertFalse("capabilityToCommand must not return null for AdbScanDevices", cmd.isNullOrEmpty())
    }

    // ── batch non-null ────────────────────────────────────────

    @Test
    fun `capabilityToCommand returns non-null for all ConnectScreen capabilities`() {
        val capabilities = listOf(
            Capability.AdbConnect("192.168.1.100", 5555),
            Capability.AdbScanDevices
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
