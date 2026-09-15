package com.kuzyamond.voidauditor.core

import androidx.compose.ui.graphics.Color

// =============================================================================
// Модели и реестр опасных разрешений
// =============================================================================

enum class AuditRisk { LOW, MEDIUM, HIGH, CRITICAL }

fun AuditRisk.color(): Color = when (this) {
    AuditRisk.CRITICAL -> Color(0xFFFF2D55)
    AuditRisk.HIGH -> Color(0xFFFF9500)
    AuditRisk.MEDIUM -> Color(0xFFFFCC00)
    AuditRisk.LOW -> Color(0xFF00FF9D) // CyberAccent
}

fun AuditRisk.label(): String = when (this) {
    AuditRisk.CRITICAL -> "CRITICAL"
    AuditRisk.HIGH -> "HIGH"
    AuditRisk.MEDIUM -> "MEDIUM"
    AuditRisk.LOW -> "LOW"
}

data class GrantedPermission(val name: String, val group: String, val weight: Int)

data class AppPermissionAudit(
    val packageName: String,
    val versionName: String?,
    val permissions: List<GrantedPermission>,
    val isSystem: Boolean = false
) {
    val score: Int get() = permissions.sumOf { it.weight }
    val risk: AuditRisk get() = when {
        score >= 10 -> AuditRisk.CRITICAL
        score >= 6 -> AuditRisk.HIGH
        score >= 3 -> AuditRisk.MEDIUM
        else -> AuditRisk.LOW
    }
}

/**
 * Реестр «опасных» разрешений (dangerous runtime + special app ops) с группой
 * и весовым вкладом в риск.
 */
object PermissionRegistry {
    val map: Map<String, Pair<String, Int>> = linkedMapOf(
        "READ_SMS" to ("SMS" to 3),
        "SEND_SMS" to ("SMS" to 3),
        "RECEIVE_SMS" to ("SMS" to 3),
        "RECEIVE_WAP_PUSH" to ("SMS" to 2),
        "RECEIVE_MMS" to ("SMS" to 2),
        "READ_CONTACTS" to ("CONTACTS" to 2),
        "WRITE_CONTACTS" to ("CONTACTS" to 2),
        "READ_CALL_LOG" to ("CALL_LOG" to 3),
        "WRITE_CALL_LOG" to ("CALL_LOG" to 3),
        "ACCESS_FINE_LOCATION" to ("LOCATION" to 3),
        "ACCESS_COARSE_LOCATION" to ("LOCATION" to 2),
        "ACCESS_BACKGROUND_LOCATION" to ("LOCATION" to 3),
        "RECORD_AUDIO" to ("MIC" to 3),
        "CAMERA" to ("CAMERA" to 2),
        "READ_PHONE_STATE" to ("PHONE" to 2),
        "READ_PHONE_NUMBERS" to ("PHONE" to 2),
        "CALL_PHONE" to ("PHONE" to 2),
        "ANSWER_PHONE_CALLS" to ("PHONE" to 2),
        "READ_EXTERNAL_STORAGE" to ("STORAGE" to 1),
        "WRITE_EXTERNAL_STORAGE" to ("STORAGE" to 1),
        "READ_MEDIA_IMAGES" to ("STORAGE" to 1),
        "READ_MEDIA_VIDEO" to ("STORAGE" to 1),
        "READ_MEDIA_AUDIO" to ("STORAGE" to 1),
        "MANAGE_EXTERNAL_STORAGE" to ("STORAGE" to 3),
        "BODY_SENSORS" to ("SENSORS" to 1),
        "ACTIVITY_RECOGNITION" to ("SENSORS" to 1),
        "READ_CALENDAR" to ("CALENDAR" to 1),
        "WRITE_CALENDAR" to ("CALENDAR" to 1),
        "SYSTEM_ALERT_WINDOW" to ("SPECIAL" to 3),
        "REQUEST_INSTALL_PACKAGES" to ("SPECIAL" to 3),
        "QUERY_ALL_PACKAGES" to ("SPECIAL" to 2),
        "BIND_ACCESSIBILITY_SERVICE" to ("SPECIAL" to 3),
        "PACKAGE_USAGE_STATS" to ("SPECIAL" to 2)
    )

    fun group(name: String): String = map[name]?.first ?: "OTHER"
    fun weight(name: String): Int = map[name]?.second ?: 0
}

object DumpsysPermissionParser {

    private val PERM_TOKEN = Regex("""android\.permission\.([A-Z_0-9]+)""")
    private val GRANTED_LINE = Regex("""android\.permission\.([A-Z_0-9]+):\s*granted=(true|false)""")
    private val VERSION_NAME = Regex("""versionName=(\S*)""")

    data class Parsed(val requested: Set<String>, val granted: Map<String, Boolean>)

    fun parse(output: String): Parsed {
        val lines = output.lines()
        val requested = mutableSetOf<String>()
        val hdrIdx = lines.indexOfFirst { it.trim() == "requested permissions:" }
        if (hdrIdx >= 0) {
            for (j in hdrIdx + 1 until lines.size) {
                val line = lines[j]
                val trimmed = line.trim()
                if (trimmed.endsWith("permissions:") && !line.contains("android.permission")) break
                if (trimmed.isEmpty()) continue
                if (!line.startsWith(" ") && !line.startsWith("\t")) break
                PERM_TOKEN.findAll(line).forEach { requested.add(it.groupValues[1]) }
            }
        }
        val granted = mutableMapOf<String, Boolean>()
        GRANTED_LINE.findAll(output).forEach { m ->
            granted[m.groupValues[1]] = m.groupValues[2] == "true"
        }
        return Parsed(requested, granted)
    }

    fun auditFromDumpsys(packageName: String, output: String, isSystem: Boolean = false): AppPermissionAudit {
        val parsed = parse(output)
        val versionName = VERSION_NAME.find(output)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val permissions = parsed.requested
            .filter { it in PermissionRegistry.map }
            .filter { parsed.granted[it] == true }
            .sorted()
            .map { GrantedPermission(it, PermissionRegistry.group(it), PermissionRegistry.weight(it)) }
        return AppPermissionAudit(packageName, versionName, permissions, isSystem)
    }
}

object PermissionAuditEngine {
    fun buildAudits(
        results: List<Pair<String, String>>,
        systemSet: Set<String>
    ): List<AppPermissionAudit> = results
        .filter { it.second.isNotBlank() }
        .map { (pkg, out) -> DumpsysPermissionParser.auditFromDumpsys(pkg, out, pkg in systemSet) }
        .sortedWith(compareByDescending<AppPermissionAudit> { it.score }.thenBy { it.packageName })
}
