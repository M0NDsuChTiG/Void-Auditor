package com.kuzyamond.voidauditor.activity

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityLauncherCapabilityTest {

    @Test
    fun `DumpPackageActivities has risk score 25`() {
        val cap = Capability.DumpPackageActivities("com.example.app")
        assertEquals(25, cap.riskScore())
    }

    @Test
    fun `LaunchActivity has risk score 45`() {
        val cap = Capability.LaunchActivity("com.example.app/.MainActivity")
        assertEquals(45, cap.riskScore())
    }

    @Test
    fun `DumpPackageActivities requires confirmation`() {
        val cap = Capability.DumpPackageActivities("com.example.app")
        assertTrue(cap.requiresConfirmation())
    }

    @Test
    fun `LaunchActivity requires confirmation`() {
        val cap = Capability.LaunchActivity("com.example.app/.MainActivity")
        assertTrue(cap.requiresConfirmation())
    }

    @Test
    fun `QueryPackages command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.QueryPackages("-3"))
        assertEquals("pm list packages -3", cmd)
    }

    @Test
    fun `DumpPackageActivities command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(
            Capability.DumpPackageActivities("com.example.app")
        )
        assertEquals(
            "dumpsys package com.example.app | grep -oE 'com.example.app/[A-Za-z0-9_.\$]+' | sort -u | head -80",
            cmd
        )
    }

    @Test
    fun `LaunchActivity command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(
            Capability.LaunchActivity("com.example.app/.MainActivity")
        )
        assertEquals("am start -n com.example.app/.MainActivity", cmd)
    }

    @Test
    fun `DumpPackageActivities does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(
            Capability.DumpPackageActivities("com.test")
        )
        assertFalse("capabilityToCommand must not return null for DumpPackageActivities", cmd.isNullOrEmpty())
    }

    @Test
    fun `LaunchActivity does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(
            Capability.LaunchActivity("com.test/.Act")
        )
        assertFalse("capabilityToCommand must not return null for LaunchActivity", cmd.isNullOrEmpty())
    }

    @Test
    fun `capabilityToCommand returns non-null for all ActivityLauncher capabilities`() {
        val capabilities = listOf(
            Capability.QueryPackages("-3"),
            Capability.DumpPackageActivities("com.example"),
            Capability.LaunchActivity("com.example/.Main")
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
