package com.kuzyamond.voidauditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionAuditTest {

    // Реальный формат One UI (Samsung, Android 11+): requested (голые имена) +
    // install/runtime секции с `name: granted=true|false`
    private val oneUiDump = """
        Package [com.aefyr.sai.fdroid] (123):
          userId=10086
          versionName=2.2.12 versionCode=123
          installerPackageName=com.android.vending
        requested permissions:
          android.permission.READ_EXTERNAL_STORAGE
          android.permission.WRITE_EXTERNAL_STORAGE
          android.permission.REQUEST_INSTALL_PACKAGES
          android.permission.REQUEST_DELETE_PACKAGES
          android.permission.FOREGROUND_SERVICE
          moe.shizuku.manager.permission.API_V23
        install permissions:
          android.permission.FOREGROUND_SERVICE: granted=true
          android.permission.REQUEST_DELETE_PACKAGES: granted=true
          android.permission.REQUEST_INSTALL_PACKAGES: granted=false
        runtime permissions:
          android.permission.READ_EXTERNAL_STORAGE: granted=true, flags=[ USER_SET|USER_SENSITIVE_WHEN_GRANTED]
          android.permission.WRITE_EXTERNAL_STORAGE: granted=true, flags=[ USER_SET|USER_SENSITIVE_WHEN_GRANTED]
        User 0: ceDataInode=980086 installed=true
    """.trimIndent()

    // Формат AOSP: granted прямо в requested-секции (старые устройства)
    private val aospDump = """
        Package [com.example.spy] (42):
          versionName=1.0
        requested permissions:
          android.permission.READ_SMS: granted=true
          android.permission.ACCESS_FINE_LOCATION: granted=true
          android.permission.CAMERA: granted=false
        User 0: installed=true
    """.trimIndent()

    @Test
    fun `parser collects only android permissions from requested section`() {
        val parsed = DumpsysPermissionParser.parse(oneUiDump)
        assertTrue(parsed.requested.contains("READ_EXTERNAL_STORAGE"))
        assertTrue(parsed.requested.contains("WRITE_EXTERNAL_STORAGE"))
        assertTrue(parsed.requested.contains("REQUEST_INSTALL_PACKAGES"))
        assertTrue(parsed.requested.contains("FOREGROUND_SERVICE"))
        // Не-android разрешение (shizuku API) не должно попадать
        assertFalse(parsed.requested.any { it.contains("shizuku") || it == "API_V23" })
    }

    @Test
    fun `parser reads granted state from install and runtime sections`() {
        val parsed = DumpsysPermissionParser.parse(oneUiDump)
        assertTrue(parsed.granted["READ_EXTERNAL_STORAGE"] == true)
        assertTrue(parsed.granted["WRITE_EXTERNAL_STORAGE"] == true)
        assertTrue(parsed.granted["FOREGROUND_SERVICE"] == true)
        // REQUEST_INSTALL_PACKAGES заявлен, но в install-секции granted=false
        assertTrue(parsed.granted["REQUEST_INSTALL_PACKAGES"] == false)
    }

    @Test
    fun `audit counts only granted dangerous permissions and scores risk`() {
        val audit = DumpsysPermissionParser.auditFromDumpsys("com.aefyr.sai.fdroid", oneUiDump)
        assertEquals("com.aefyr.sai.fdroid", audit.packageName)
        assertEquals("2.2.12", audit.versionName)
        // READ/WRITE_EXTERNAL_STORAGE granted (storage, w=1); REQUEST_INSTALL_PACKAGES — НЕ granted
        assertEquals(listOf("READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE"), audit.permissions.map { it.name })
        assertEquals(2, audit.score)
        assertEquals(AuditRisk.LOW, audit.risk)
    }

    @Test
    fun `aosp format with inline granted is parsed correctly`() {
        val parsed = DumpsysPermissionParser.parse(aospDump)
        assertTrue(parsed.requested.contains("READ_SMS"))
        assertTrue(parsed.granted["READ_SMS"] == true)
        assertTrue(parsed.granted["CAMERA"] == false)

        val audit = DumpsysPermissionParser.auditFromDumpsys("com.example.spy", aospDump)
        // READ_SMS (3) + ACCESS_FINE_LOCATION (3) = 6 → HIGH; CAMERA denied
        assertEquals(6, audit.score)
        assertEquals(AuditRisk.HIGH, audit.risk)
        assertEquals(listOf("ACCESS_FINE_LOCATION", "READ_SMS"), audit.permissions.map { it.name })
    }

    @Test
    fun `registry covers key dangerous permissions`() {
        assertEquals("SMS" to 3, PermissionRegistry.map["READ_SMS"])
        assertEquals("LOCATION" to 3, PermissionRegistry.map["ACCESS_FINE_LOCATION"])
        assertEquals("MIC" to 3, PermissionRegistry.map["RECORD_AUDIO"])
        assertEquals("SPECIAL" to 3, PermissionRegistry.map["BIND_ACCESSIBILITY_SERVICE"])
        assertTrue(PermissionRegistry.map.containsKey("QUERY_ALL_PACKAGES"))
        assertTrue(PermissionRegistry.map.containsKey("MANAGE_EXTERNAL_STORAGE"))
    }

    @Test
    fun `empty or garbage output yields empty audit`() {
        val audit = DumpsysPermissionParser.auditFromDumpsys("com.example.x", "Package not found")
        assertEquals(0, audit.score)
        assertEquals(AuditRisk.LOW, audit.risk)
        assertTrue(audit.permissions.isEmpty())
        assertFalse(audit.isSystem)
    }

    @Test
    fun `system flag is propagated through audit builder`() {
        val sys = DumpsysPermissionParser.auditFromDumpsys("com.android.shell", oneUiDump, isSystem = true)
        assertTrue(sys.isSystem)
        val third = DumpsysPermissionParser.auditFromDumpsys("com.aefyr.sai.fdroid", oneUiDump, isSystem = false)
        assertFalse(third.isSystem)
    }
}
