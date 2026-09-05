package com.kuzyamond.voidauditor

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupScreenCapabilityTest {

    // ── riskScore ──────────────────────────────────────────────

    @Test
    fun `ListApkFiles has risk score 15`() {
        val cap = Capability.ListApkFiles("/sdcard/Download/")
        assertEquals(15, cap.riskScore)
    }

    @Test
    fun `InstallApk has risk score 85`() {
        val cap = Capability.InstallApk("/sdcard/Download/app.apk")
        assertEquals(85, cap.riskScore)
    }

    @Test
    fun `GetPackagePath has risk score 15`() {
        val cap = Capability.GetPackagePath("com.example.app")
        assertEquals(15, cap.riskScore)
    }

    @Test
    fun `CopyFile has risk score 50`() {
        val cap = Capability.CopyFile("/source.apk", "/destination.apk")
        assertEquals(50, cap.riskScore)
    }

    @Test
    fun `CreateDirectory has risk score 30`() {
        val cap = Capability.CreateDirectory("/sdcard/Download/ADB_Backups")
        assertEquals(30, cap.riskScore)
    }

    // ── riskScore (confirmation threshold) ────────────────────────

    @Test
    fun `ListApkFiles risk score is 15 (READ tier, no confirmation)`() {
        val cap = Capability.ListApkFiles("/sdcard/Download/")
        assertEquals(15, cap.riskScore)
    }

    @Test
    fun `InstallApk risk score is 85 (ACTION tier, confirmation expected)`() {
        val cap = Capability.InstallApk("/sdcard/Download/app.apk")
        assertEquals(85, cap.riskScore)
    }

    @Test
    fun `GetPackagePath risk score is 15 (READ tier, no confirmation)`() {
        val cap = Capability.GetPackagePath("com.example.app")
        assertEquals(15, cap.riskScore)
    }

    @Test
    fun `CopyFile risk score is 50 (ACTION tier, confirmation expected)`() {
        val cap = Capability.CopyFile("/source.apk", "/destination.apk")
        assertEquals(50, cap.riskScore)
    }

    @Test
    fun `CreateDirectory risk score is 30 (ACTION tier, no confirmation)`() {
        val cap = Capability.CreateDirectory("/sdcard/Download/ADB_Backups")
        assertEquals(30, cap.riskScore)
    }

    // ── capabilityToCommand ────────────────────────────────────

    @Test
    fun `ListApkFiles command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.ListApkFiles("/sdcard/Download/"))
        assertEquals("ls /sdcard/Download/*.apk 2>/dev/null", cmd)
    }

    @Test
    fun `InstallApk command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.InstallApk("/sdcard/Download/app.apk"))
        assertEquals("pm install -r /sdcard/Download/app.apk && echo \"OK\"", cmd)
    }

    @Test
    fun `GetPackagePath command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.GetPackagePath("com.example.app"))
        assertEquals("pm path com.example.app", cmd)
    }

    @Test
    fun `CopyFile command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.CopyFile("/source.apk", "/dest.apk"))
        assertEquals("cp /source.apk /dest.apk && echo \"OK\"", cmd)
    }

    @Test
    fun `CreateDirectory command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.CreateDirectory("/sdcard/Download/ADB_Backups"))
        assertEquals("mkdir -p /sdcard/Download/ADB_Backups", cmd)
    }

    // ── non-null checks ───────────────────────────────────────

    @Test
    fun `ListApkFiles does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.ListApkFiles("/sdcard/Download/"))
        assertFalse("capabilityToCommand must not return null for ListApkFiles", cmd.isNullOrEmpty())
    }

    @Test
    fun `InstallApk does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.InstallApk("/sdcard/app.apk"))
        assertFalse("capabilityToCommand must not return null for InstallApk", cmd.isNullOrEmpty())
    }

    @Test
    fun `GetPackagePath does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.GetPackagePath("com.test"))
        assertFalse("capabilityToCommand must not return null for GetPackagePath", cmd.isNullOrEmpty())
    }

    @Test
    fun `CopyFile does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.CopyFile("/a", "/b"))
        assertFalse("capabilityToCommand must not return null for CopyFile", cmd.isNullOrEmpty())
    }

    @Test
    fun `CreateDirectory does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.CreateDirectory("/tmp"))
        assertFalse("capabilityToCommand must not return null for CreateDirectory", cmd.isNullOrEmpty())
    }

    // ── batch non-null ────────────────────────────────────────

    @Test
    fun `capabilityToCommand returns non-null for all BackupScreen capabilities`() {
        val capabilities = listOf(
            Capability.ListApkFiles("/sdcard/Download/"),
            Capability.InstallApk("/sdcard/Download/app.apk"),
            Capability.GetPackagePath("com.example.app"),
            Capability.CopyFile("/source.apk", "/dest.apk"),
            Capability.CreateDirectory("/sdcard/Download/ADB_Backups")
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
