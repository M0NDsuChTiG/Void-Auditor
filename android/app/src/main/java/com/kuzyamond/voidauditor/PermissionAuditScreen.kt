package com.kuzyamond.voidauditor

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuzyamond.voidauditor.core.ActorType
import com.kuzyamond.voidauditor.core.AuditLogger
import com.kuzyamond.voidauditor.core.ShizukuExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.io.OutputStreamWriter
import java.io.FileWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

// =============================================================================
// Модели и реестр опасных разрешений
// =============================================================================

enum class AuditRisk { LOW, MEDIUM, HIGH, CRITICAL }

fun AuditRisk.color(): Color = when (this) {
    AuditRisk.CRITICAL -> Color(0xFFFF2D55)
    AuditRisk.HIGH -> Color(0xFFFF9500)
    AuditRisk.MEDIUM -> Color(0xFFFFCC00)
    AuditRisk.LOW -> CyberAccent
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
 * и весовым вкладом в риск. Веса: 1 — низкая чувствительность (storage/calendar),
 * 2 — средняя (contacts/camera/phone), 3 — высокая (SMS/микрофон/локация/call log/special).
 */
object PermissionRegistry {
    val map: Map<String, Pair<String, Int>> = linkedMapOf(
        // SMS
        "READ_SMS" to ("SMS" to 3),
        "SEND_SMS" to ("SMS" to 3),
        "RECEIVE_SMS" to ("SMS" to 3),
        "RECEIVE_WAP_PUSH" to ("SMS" to 2),
        "RECEIVE_MMS" to ("SMS" to 2),
        // Contacts
        "READ_CONTACTS" to ("CONTACTS" to 2),
        "WRITE_CONTACTS" to ("CONTACTS" to 2),
        // Call log
        "READ_CALL_LOG" to ("CALL_LOG" to 3),
        "WRITE_CALL_LOG" to ("CALL_LOG" to 3),
        // Location
        "ACCESS_FINE_LOCATION" to ("LOCATION" to 3),
        "ACCESS_COARSE_LOCATION" to ("LOCATION" to 2),
        "ACCESS_BACKGROUND_LOCATION" to ("LOCATION" to 3),
        // Microphone
        "RECORD_AUDIO" to ("MIC" to 3),
        // Camera
        "CAMERA" to ("CAMERA" to 2),
        // Phone
        "READ_PHONE_STATE" to ("PHONE" to 2),
        "READ_PHONE_NUMBERS" to ("PHONE" to 2),
        "CALL_PHONE" to ("PHONE" to 2),
        "ANSWER_PHONE_CALLS" to ("PHONE" to 2),
        // Storage
        "READ_EXTERNAL_STORAGE" to ("STORAGE" to 1),
        "WRITE_EXTERNAL_STORAGE" to ("STORAGE" to 1),
        "READ_MEDIA_IMAGES" to ("STORAGE" to 1),
        "READ_MEDIA_VIDEO" to ("STORAGE" to 1),
        "READ_MEDIA_AUDIO" to ("STORAGE" to 1),
        "MANAGE_EXTERNAL_STORAGE" to ("STORAGE" to 3),
        // Sensors
        "BODY_SENSORS" to ("SENSORS" to 1),
        "ACTIVITY_RECOGNITION" to ("SENSORS" to 1),
        // Calendar
        "READ_CALENDAR" to ("CALENDAR" to 1),
        "WRITE_CALENDAR" to ("CALENDAR" to 1),
        // Special access (не runtime, но чувствительные app ops)
        "SYSTEM_ALERT_WINDOW" to ("SPECIAL" to 3),
        "REQUEST_INSTALL_PACKAGES" to ("SPECIAL" to 3),
        "QUERY_ALL_PACKAGES" to ("SPECIAL" to 2),
        "BIND_ACCESSIBILITY_SERVICE" to ("SPECIAL" to 3),
        "PACKAGE_USAGE_STATS" to ("SPECIAL" to 2)
    )

    fun group(name: String): String = map[name]?.first ?: "OTHER"
    fun weight(name: String): Int = map[name]?.second ?: 0
}

/**
 * Парсер вывода `dumpsys package <pkg>`.
 *
 * Реальные форматы на разных устройствах:
 *  - One UI (Samsung): секция `requested permissions:` с голыми именами +
 *    секция `runtime permissions:` / `install permissions:` с `name: granted=true|false`
 *  - AOSP/старые версии: `requested permissions:` с `name: granted=true` прямо в секции
 *
 * Риск считается только по РЕАЛЬНО ВЫДАННЫМ разрешениям (granted=true).
 */
object DumpsysPermissionParser {

    private val PERM_TOKEN = Regex("""android\.permission\.([A-Z_0-9]+)""")
    private val GRANTED_LINE = Regex("""android\.permission\.([A-Z_0-9]+):\s*granted=(true|false)""")
    private val VERSION_NAME = Regex("""versionName=(\S*)""")

    data class Parsed(val requested: Set<String>, val granted: Map<String, Boolean>)

    fun parse(output: String): Parsed {
        val lines = output.lines()

        // 1) requested permissions: — голые имена разрешений (или `name: granted=` на старых AOSP)
        val requested = mutableSetOf<String>()
        val hdrIdx = lines.indexOfFirst { it.trim() == "requested permissions:" }
        if (hdrIdx >= 0) {
            for (j in hdrIdx + 1 until lines.size) {
                val line = lines[j]
                val trimmed = line.trim()
                // Конец секции: следующий заголовок (`* permissions:`) или top-level ключ
                if (trimmed.endsWith("permissions:") && !line.contains("android.permission")) break
                if (trimmed.isEmpty()) continue
                if (!line.startsWith(" ") && !line.startsWith("\t")) break
                PERM_TOKEN.findAll(line).forEach { requested.add(it.groupValues[1]) }
            }
        }

        // 2) granted-статус из runtime/install секций. Последнее вхождение побеждает
        //    (runtime идёт после install, поэтому его значение авторитетнее).
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

// =============================================================================
// Экран
// =============================================================================

@Composable
fun PermissionAuditScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    val context = LocalContext.current
    var includeSystem by remember { mutableStateOf(false) }
    var riskFilter by remember { mutableStateOf<AuditRisk?>(null) }
    var apps by remember { mutableStateOf<List<AppPermissionAudit>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var scanDone by remember { mutableStateOf(0) }
    var scanTotal by remember { mutableStateOf(0) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    fun scan() {
        scope.launch {
            isLoading = true
            apps = emptyList()
            expanded.clear()
            scanDone = 0
            scanTotal = 0

            val scopeLabel = if (includeSystem) "ALL" else "3RD-PARTY"
            GlobalLog.log("PERM_AUDIT: listing packages ($scopeLabel)...", "warn", "PERMS")
            val listRes = ShizukuExecutor.executeCommand(
                if (includeSystem) "pm list packages" else "pm list packages -3"
            )
            val pkgs = listRes.output.lines()
                .mapNotNull { it.removePrefix("package:").trim().takeIf { p -> p.isNotEmpty() } }
            // Для ALL-режима помечаем системные пакеты (SYS-тег), чтобы CRITICAL
            // от com.android.*/gms/samsung не пугал: это легитимный широкий доступ.
            val systemSet: Set<String> = if (includeSystem) {
                ShizukuExecutor.executeCommand("pm list packages -s").output.lines()
                    .mapNotNull { it.removePrefix("package:").trim().takeIf { p -> p.isNotEmpty() } }
                    .toSet()
            } else {
                emptySet()
            }
            scanTotal = pkgs.size
            GlobalLog.log("PERM_AUDIT: ${pkgs.size} packages (${pkgs.count { it in systemSet }} system), collecting dumpsys...", "info", "PERMS")

            val semaphore = Semaphore(4)
            val done = AtomicInteger(0)
            val results = coroutineScope {
                pkgs.map { pkg ->
                    async {
                        semaphore.withPermit {
                            val out = withTimeoutOrNull(15_000) {
                                runCatching { ShizukuManager.executeCommand("dumpsys package $pkg").getOrThrow() }
                                    .getOrElse { "" }
                            } ?: ""
                            val n = done.incrementAndGet()
                            scanDone = n
                            if (n % 20 == 0 || n == scanTotal) {
                                GlobalLog.log("PERM_AUDIT: $n/$scanTotal", "info", "PERMS")
                            }
                            pkg to out
                        }
                    }
                }.awaitAll()
            }

            apps = results
                .filter { it.second.isNotBlank() }
                .map { (pkg, out) -> DumpsysPermissionParser.auditFromDumpsys(pkg, out, pkg in systemSet) }
                .sortedWith(compareByDescending<AppPermissionAudit> { it.score }.thenBy { it.packageName })

            val high = apps.count { it.risk == AuditRisk.HIGH || it.risk == AuditRisk.CRITICAL }
            GlobalLog.log(
                "PERM_AUDIT: DONE — ${apps.size} apps, $high high/critical risk",
                "ok", "PERMS"
            )
            AuditLogger.log(
                actor = ActorType.SYSTEM,
                capability = "perm.audit",
                riskLevel = RiskLevel.LOW,
                decision = "ALLOWED",
                target = "pm list packages + dumpsys package (${apps.size} apps)",
                durationMs = 0,
                details = "Permission audit: ${apps.size} apps, $high high/critical"
            )
            isLoading = false
        }
    }

    fun exportReport() {
        if (apps.isEmpty()) {
            Toast.makeText(context, "Нет данных для экспорта — запустите скан", Toast.LENGTH_SHORT).show()
            return
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "Permission_Audit_$timestamp.txt"

        val report = buildString {
            appendLine("=== VOID AUDITOR — PERMISSION AUDIT ===")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Scope: ${if (includeSystem) "ALL packages" else "Third-party packages"}")
            appendLine("Apps audited: ${apps.size} (${apps.count { it.isSystem }} system)")
            appendLine("=".repeat(72))
            appendLine()
            val high = apps.count { it.risk == AuditRisk.HIGH || it.risk == AuditRisk.CRITICAL }
            val medium = apps.count { it.risk == AuditRisk.MEDIUM }
            appendLine("SUMMARY: CRITICAL/HIGH: $high | MEDIUM: $medium | LOW: ${apps.size - high - medium}")
            appendLine()
            apps.forEach { app ->
                appendLine("[${app.risk.label()} ${app.score}]${if (app.isSystem) " [SYS]" else ""} ${app.packageName}${app.versionName?.let { " ($it)" } ?: ""}")
                if (app.permissions.isEmpty()) {
                    appendLine("    (no granted dangerous permissions)")
                } else {
                    app.permissions.forEach { perm ->
                        appendLine("    - ${perm.name} (${perm.group}, w=${perm.weight})")
                    }
                }
                appendLine()
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/VOID_Auditor_Reports")
                }
                val uri = resolver.insert(
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    values
                ) ?: throw Exception("MEDIASTORE_INSERT_FAILED")
                val outStream: java.io.OutputStream? = resolver.openOutputStream(uri)
                    ?: throw Exception("OUTPUT_STREAM_FAILED")
                outStream.use { stream ->
                    OutputStreamWriter(stream).use { writer -> writer.write(report) }
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(downloadsDir, "VOID_Auditor_Reports")
                if (!appDir.exists()) appDir.mkdirs()
                FileWriter(File(appDir, filename)).use { writer -> writer.write(report) }
            }
            Toast.makeText(
                context,
                "✅ Отчёт сохранён!\nDownloads/VOID_Auditor_Reports/$filename",
                Toast.LENGTH_LONG
            ).show()
            GlobalLog.log("PERM_AUDIT: report saved — $filename", "ok", "PERMS")
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Экспорт не удался: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            GlobalLog.log("PERM_AUDIT: export failed — ${e.message}", "crit", "PERMS")
        }
    }

    val filtered = remember(apps, riskFilter) {
        if (riskFilter == null) apps else apps.filter { it.risk == riskFilter }
    }
    val riskCounts = remember(apps) {
        AuditRisk.values().associateWith { risk -> apps.count { it.risk == risk } }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Row 1: scope toggle + risk filters + actions
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(label = if (includeSystem) "ALL" else "3RD", active = false, color = CyberInfo) {
                includeSystem = !includeSystem
                scan()
            }
            AuditRisk.values().forEach { risk ->
                FilterChip(label = risk.label(), active = riskFilter == risk, color = risk.color()) {
                    riskFilter = if (riskFilter == risk) null else risk
                }
            }
            FilterChip(label = "CLR", active = false, color = Color.Gray) { riskFilter = null }
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = { scan() },
                modifier = Modifier.size(40.dp).border(1.dp, CyberInfo).background(CyberSurface)
                    .semantics { contentDescription = "Refresh permission audit" }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyberInfo, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, null, tint = CyberInfo)
                }
            }
            IconButton(
                onClick = { exportReport() },
                enabled = apps.isNotEmpty() && !isLoading,
                modifier = Modifier.size(40.dp).border(1.dp, CyberAccent2).background(CyberSurface)
                    .semantics { contentDescription = "Export audit report" }
            ) {
                Icon(Icons.Default.Save, null, tint = CyberAccent2)
            }
        }

        // Row 2: stats
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("APPS: ${apps.size}", color = CyberText, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            if (includeSystem) {
                Text("SYS: ${apps.count { it.isSystem }}", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            riskCounts.forEach { (risk, count) ->
                Text("${risk.label()}: $count", color = risk.color(), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
        if (includeSystem && apps.isNotEmpty()) {
            Text(
                "⚠ INCLUDES SYSTEM — HIGH SCORES EXPECTED (com.android.*/gms/samsung)",
                color = Color(0xFFFFCC00),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Row 3: progress
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SCANNING_PERMISSIONS $scanDone/$scanTotal", color = CyberAccent2, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            }
            LinearProgressIndicator(
                progress = if (scanTotal > 0) scanDone.toFloat() / scanTotal else 0f,
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = CyberAccent2,
                trackColor = CyberSurface
            )
        }

        // List
        LazyColumn(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
            if (filtered.isEmpty() && !isLoading) {
                item {
                    Text(
                        if (apps.isEmpty()) "NO_DATA — нажмите ⟳ для сканирования установленных пакетов"
                        else "NO_MATCHES — ни одного приложения с выбранным уровнем риска",
                        color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            items(filtered, key = { it.packageName }) { app ->
                PermissionAppCard(
                    app = app,
                    isExpanded = expanded[app.packageName] == true,
                    onToggle = { expanded[app.packageName] = !(expanded[app.packageName] == true) }
                )
            }
        }
    }
}

@Composable
fun FilterChip(label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.border(1.dp, if (active) color else CyberBorder).clickable(onClick = onClick),
        color = if (active) color.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(2.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                label,
                color = if (active) color else Color(0xFF64748B),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun PermissionAppCard(app: AppPermissionAudit, isExpanded: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .border(1.dp, CyberBorder)
            .background(CyberSurface.copy(alpha = 0.5f))
            .clickable(onClick = onToggle)
            .padding(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                tint = app.risk.color(),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = app.packageName,
                color = Color(0xFFCBD5E1),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (app.isSystem) {
                Text(
                    "[SYS]",
                    color = Color(0xFF64748B),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            Text(
                text = "[${app.risk.label()} ${app.score}]",
                color = app.risk.color(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        if (isExpanded) {
            Spacer(Modifier.height(6.dp))
            app.versionName?.let {
                Text("VERSION: $it", color = Color(0xFF94A3B8), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
            if (app.permissions.isEmpty()) {
                Text(
                    "NO GRANTED DANGEROUS PERMISSIONS",
                    color = CyberAccent, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                app.permissions.forEach { perm ->
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        Text("▸ ", color = perm.color(), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            "[${perm.group}] ${perm.name}",
                            color = perm.color(),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

private fun GrantedPermission.color(): Color = when {
    weight >= 3 -> Color(0xFFFF9500)
    weight == 2 -> Color(0xFFFFCC00)
    else -> CyberAccent2
}
