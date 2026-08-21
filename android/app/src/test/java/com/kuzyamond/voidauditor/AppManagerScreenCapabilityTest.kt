package com.kuzyamond.voidauditor

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppManagerScreenCapabilityTest {

    @Test
    fun `QueryPackages has risk score 15`() {
        val cap = Capability.QueryPackages("e")
        assertEquals(15, cap.riskScore())
    }

    @Test
    fun `ForceStopPackage has risk score 50`() {
        val cap = Capability.ForceStopPackage("com.example.app")
        assertEquals(50, cap.riskScore())
    }

    @Test
    fun `DisablePackage has risk score 60`() {
        val cap = Capability.DisablePackage("com.example.app")
        assertEquals(60, cap.riskScore())
    }

    @Test
    fun `EnablePackage has risk score 30`() {
        val cap = Capability.EnablePackage("com.example.app")
        assertEquals(30, cap.riskScore())
    }

    @Test
    fun `UninstallPackage has risk score 85`() {
        val cap = Capability.UninstallPackage("com.example.app")
        assertEquals(85, cap.riskScore())
    }

    @Test
    fun `QueryPackages does not require confirmation`() {
        val cap = Capability.QueryPackages("e")
        assertFalse(cap.requiresConfirmation())
    }

    @Test
    fun `EnablePackage does not require confirmation`() {
        val cap = Capability.EnablePackage("com.example.app")
        assertFalse(cap.requiresConfirmation())
    }

    @Test
    fun `ForceStopPackage requires confirmation`() {
        val cap = Capability.ForceStopPackage("com.example.app")
        assertTrue(cap.requiresConfirmation())
    }

    @Test
    fun `DisablePackage requires confirmation`() {
        val cap = Capability.DisablePackage("com.example.app")
        assertTrue(cap.requiresConfirmation())
    }

    @Test
    fun `UninstallPackage requires confirmation`() {
        val cap = Capability.UninstallPackage("com.example.app")
        assertTrue(cap.requiresConfirmation())
    }

    @Test
    fun `QueryPackages command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.QueryPackages("e"))
        assertEquals("pm list packages e", cmd)
    }

    @Test
    fun `ForceStopPackage command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.ForceStopPackage("com.example.app"))
        assertEquals("am force-stop com.example.app", cmd)
    }

    @Test
    fun `DisablePackage command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.DisablePackage("com.example.app"))
        assertEquals("pm disable-user --user 0 com.example.app", cmd)
    }

    @Test
    fun `EnablePackage command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.EnablePackage("com.example.app"))
        assertEquals("pm enable com.example.app", cmd)
    }

    @Test
    fun `UninstallPackage command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.UninstallPackage("com.example.app"))
        assertEquals("pm uninstall com.example.app", cmd)
    }

    @Test
    fun `QueryPackages does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.QueryPackages("d"))
        assertFalse("capabilityToCommand must not return null for QueryPackages", cmd.isNullOrEmpty())
    }

    @Test
    fun `ForceStopPackage does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.ForceStopPackage("com.test"))
        assertFalse("capabilityToCommand must not return null for ForceStopPackage", cmd.isNullOrEmpty())
    }

    @Test
    fun `DisablePackage does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.DisablePackage("com.test"))
        assertFalse("capabilityToCommand must not return null for DisablePackage", cmd.isNullOrEmpty())
    }

    @Test
    fun `EnablePackage does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.EnablePackage("com.test"))
        assertFalse("capabilityToCommand must not return null for EnablePackage", cmd.isNullOrEmpty())
    }

    @Test
    fun `UninstallPackage does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.UninstallPackage("com.test"))
        assertFalse("capabilityToCommand must not return null for UninstallPackage", cmd.isNullOrEmpty())
    }

    @Test
    fun `capabilityToCommand returns non-null for all AppManagerScreen capabilities`() {
        val capabilities = listOf(
            Capability.QueryPackages("e"),
            Capability.ForceStopPackage("com.example.app"),
            Capability.DisablePackage("com.example.app"),
            Capability.EnablePackage("com.example.app"),
            Capability.UninstallPackage("com.example.app")
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
