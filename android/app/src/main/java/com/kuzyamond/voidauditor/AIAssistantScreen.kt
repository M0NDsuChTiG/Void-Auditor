package com.kuzyamond.voidauditor

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.kuzyamond.voidauditor.security.SecurityModule
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kuzyamond.voidauditor.core.ActorType
import com.kuzyamond.voidauditor.core.AuditLogger
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import com.kuzyamond.voidauditor.core.PolicyEngine
import com.kuzyamond.voidauditor.core.ShizukuExecutor
import com.kuzyamond.voidauditor.core.ai.AIProposalService
import com.kuzyamond.voidauditor.core.ai.IntentProposal
import com.kuzyamond.voidauditor.core.ai.extractProposalJson
import com.kuzyamond.voidauditor.core.ai.proposalToCapability
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(val role: String, val text: String, val riskLevel: String? = null)

enum class RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN, TIER_1_REVERSIBLE
}

fun parseRiskLevel(text: String): RiskLevel {
    val upper = text.uppercase()
    return when {
        upper.contains("CRITICAL") || upper.contains("КРИТИЧЕСКИЙ") -> RiskLevel.CRITICAL
        upper.contains("HIGH") || upper.contains("ВЫСОКИЙ") -> RiskLevel.HIGH
        upper.contains("MEDIUM") || upper.contains("СРЕДНИЙ") -> RiskLevel.MEDIUM
        upper.contains("LOW") || upper.contains("НИЗКИЙ") -> RiskLevel.LOW
        else -> RiskLevel.UNKNOWN
    }
}

fun getRiskColor(level: RiskLevel): Color {
    return when (level) {
        RiskLevel.CRITICAL -> Color(0xFFFF2D55)   // Красный
        RiskLevel.HIGH     -> Color(0xFFFF9500)   // Оранжевый
        RiskLevel.MEDIUM   -> Color(0xFFFFCC00)   // Жёлтый
        RiskLevel.LOW      -> CyberAccent         // Кислотный лайм
        else               -> CyberText
    }
}

val availableModels = listOf(
    "gemini-2.0-flash-lite" to "FLASH_LITE",
    "gemini-2.0-flash" to "FLASH",
    "gemini-1.5-flash" to "1.5_FLASH"
)

// Реальные имена опасных разрешений в выводе `dumpsys package` (android.permission.X: granted=true)
private val DANGEROUS_PERMISSION_REGEX = Regex(
    "android\\.permission\\.(SEND_SMS|RECEIVE_SMS|READ_SMS|READ_CALL_LOG|WRITE_CALL_LOG|READ_CONTACTS|WRITE_CONTACTS|SYSTEM_ALERT_WINDOW|READ_PHONE_STATE|RECORD_AUDIO|ACCESS_FINE_LOCATION|REQUEST_INSTALL_PACKAGES|QUERY_ALL_PACKAGES)|BIND_ACCESSIBILITY_SERVICE",
    RegexOption.IGNORE_CASE
)

val systemInstruction = """
Ты — сертифицированный Senior Android Security & Digital Forensics эксперт (Red/Blue Team, 10+ лет опыта).
Специализация: Shizuku, ADB, pm/am/dumpsys, SELinux, Accessibility Abuse, Banking Trojan detection, IOC hunting, DevSecOps.

Правила ответа (обязательно соблюдать):
- Начинай строго с **RISK LEVEL**: Low / Medium / High / Critical
- Чётко выделяй **найденные IOC** (подозрительные разрешения, сервисы, пакеты, поведение)
- Для азербайджанских банковских приложений (az.unibank, az.dpc.sima и т.д.) — повышенное внимание
- Всегда давай готовые команды для VOID Auditor (SHELL / SCRIPT_EXECUTOR)
- Предлагай автоматизированные скрипты и hardening-рекомендации
- Отвечай на русском, структурировано, в профессиональном кибер-стиле

=== INTENT PROPOSAL PROTOCOL (ОБЯЗАТЕЛЬНО) ===
Если ты предлагаешь выполнить действие на устройстве (команду, сбор логов, чтение файла, инспекцию) — в КОНЦЕ ответа добавь строго ОДИН машиночитаемый блок в формате:
PROPOSAL_JSON: {"proposedCapability":{"capabilityId":"LIST_PACKAGES","parameters":{"filter":"-3"}},"advisoryText":"Краткое описание действия","confidence":0.9}

Разрешённые capabilityId (строго из списка, другие невозможны):
- LIST_PACKAGES — список установленных пакетов; параметр filter: "all" или "-3"
- COLLECT_LOGS — сбор логов; параметр service: "logcat" или имя сервиса
- ANALYZE_LOG / INSPECT_FILE — чтение файла; параметр path: абсолютный путь
- GENERATE_REPORT — генерация отчёта; параметров нет
НИКОГДА не предлагай произвольные shell-команды в PROPOSAL_JSON — только перечисленные capabilityId. Если действие не входит в список, просто опиши его текстом БЕЗ блока PROPOSAL_JSON.
""".trimIndent()

@Composable
fun AIAssistantScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val prefs = remember { context.getSharedPreferences("void_auditor_ai", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(runCatching { SecurityModule.getApiKey(context) }.getOrNull() ?: "") }
    var selectedModel by remember { mutableStateOf(prefs.getString("gemini_model", "gemini-2.0-flash-lite") ?: "gemini-2.0-flash-lite") }
    var showKeyInput by remember { mutableStateOf(apiKey.isEmpty()) }
    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(loadMessages(prefs)) }
    var isProcessing by remember { mutableStateOf(false) }
    var retryCooldown by remember { mutableStateOf(0) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var pendingProposal by remember { mutableStateOf<IntentProposal?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        ShizukuExecutor.init()
        // Миграция: если ключ лежал в открытых SharedPreferences — переносим в зашифрованные
        if (apiKey.isBlank()) {
            val legacyKey = prefs.getString("gemini_key", null)?.trim()
            if (!legacyKey.isNullOrBlank()) {
                runCatching {
                    SecurityModule.saveApiKey(context, legacyKey)
                    prefs.edit().remove("gemini_key").apply()
                    apiKey = legacyKey
                    showKeyInput = false
                }
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LaunchedEffect(retryCooldown) {
        if (retryCooldown > 0) { delay(1000); retryCooldown-- }
    }

    fun saveKey(key: String) {
        runCatching { SecurityModule.saveApiKey(context, key.trim()) }
            .onSuccess { Toast.makeText(context, "✅ Ключ сохранён", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "❌ Не удалось сохранить ключ: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
    }
    fun saveModel(model: String) { prefs.edit().putString("gemini_model", model).apply() }
    fun saveMessages() {
        val json = JSONArray().apply {
            messages.forEach { put(JSONObject().apply { put("r", it.role); put("t", it.text); it.riskLevel?.let { l -> put("rl", l) } }) }
        }.toString()
        prefs.edit().putString("chat_history", json).apply()
    }
    fun clearChat() { messages = emptyList(); prefs.edit().remove("chat_history").apply() }

    fun approveProposal() {
        val proposal = pendingProposal ?: return
        pendingProposal = null
        val capability = proposalToCapability(proposal)
        if (capability == null) {
            AuditLogger.log(
                actor = ActorType.AI,
                capability = "intent",
                riskLevel = RiskLevel.HIGH,
                decision = "REJECTED",
                target = proposal.proposedCapability.capabilityId.name,
                details = "Unmappable proposal (не входит в whitelist или не хватает параметров)"
            )
            messages = messages + ChatMessage("assistant", "⛔ PROPOSAL_REJECTED: действие не входит в whitelist или не хватает параметров.")
            saveMessages()
            return
        }
        AuditLogger.log(
            actor = ActorType.AI,
            capability = "intent",
            riskLevel = PolicyEngine.severityFromScore(capability.riskScore),
            decision = "APPROVED",
            target = capability.description,
            details = proposal.advisoryText
        )
        scope.launch {
            val result = CapabilityExecutor.execute(capability)
            val outcome = if (result.isSuccessful) {
                "✅ EXECUTED: ${capability.description}\n${result.output.take(500)}"
            } else {
                "❌ NOT_EXECUTED: ${result.error}"
            }
            messages = messages + ChatMessage("assistant", outcome, PolicyEngine.severityFromScore(capability.riskScore).name)
            saveMessages()
        }
    }

    fun rejectProposal() {
        val proposal = pendingProposal ?: return
        pendingProposal = null
        AuditLogger.log(
            actor = ActorType.AI,
            capability = "intent",
            riskLevel = RiskLevel.LOW,
            decision = "REJECTED",
            target = proposal.proposedCapability.capabilityId.name,
            details = "User rejected AI proposal"
        )
        messages = messages + ChatMessage("assistant", "⛔ PROPOSAL_REJECTED_BY_USER.")
        saveMessages()
    }

    fun parseGeminiResponse(body: String): String? {
        return try {
            val obj = JSONObject(body)
            val candidates = obj.optJSONArray("candidates") ?: return null
            val c = candidates.optJSONObject(0) ?: return null
            val content = c.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            parts.optJSONObject(0)?.optString("text", null)
        } catch (_: Exception) { null }
    }

    suspend fun askGemini(prompt: String, extraContext: String = "") {
        if (apiKey.isBlank() || prompt.isBlank()) return
        val fullPrompt = if (extraContext.isNotBlank()) "$prompt\n\n=== CONTEXT ===\n$extraContext" else prompt
        messages = messages + ChatMessage("user", prompt)
        isProcessing = true
        inputText = ""

        val result = withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val body = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", "$systemInstruction\n\nUser query: $fullPrompt") })
                            })
                        })
                    })
                }.toString()

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:generateContent?key=$apiKey")
                conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "POST"; setRequestProperty("Content-Type", "application/json")
                    doOutput = true; connectTimeout = 45000; readTimeout = 90000
                }
                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().readText()
                    parseGeminiResponse(resp) ?: "PARSE_ERROR"
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "{}"
                    if (conn.responseCode == 429) {
                        val sec = Regex("""(\d+)s""").find(err)?.groupValues?.get(1)?.toIntOrNull() ?: 60
                        retryCooldown = sec.coerceIn(5, 120)
                        "QUOTA_EXCEEDED — retry in ${retryCooldown}s"
                    } else {
                        val msg = try { JSONObject(err).optJSONObject("error")?.optString("message", "") ?: err } catch (_: Exception) { err }
                        "API_ERR_${conn.responseCode}: ${msg.take(300)}"
                    }
                }
            } catch (e: Exception) { "NETWORK_ERR: ${e.localizedMessage ?: e.message}" }
            finally { conn?.disconnect() }
        }

        val riskLevel = Regex("""RISK[_\s]LEVEL[:\s]*(Low|Medium|High|Critical)""", RegexOption.IGNORE_CASE)
            .find(result)?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() }
        messages = messages + ChatMessage("assistant", result, riskLevel)
        isProcessing = false
        saveMessages()

        // AI Governance: если Gemini предложила действие — парсим Intent Proposal и ждём подтверждения пользователя
        val proposalJson = extractProposalJson(result)
        val parsedProposal = proposalJson?.let { AIProposalService.parseAndValidateProposal(it) }
        if (parsedProposal != null) {
            pendingProposal = parsedProposal
            ShizukuExecutor.logListener?.invoke("INFO", "AI_PROPOSAL: ${parsedProposal.proposedCapability.capabilityId} awaiting user approval")
        }

        if (retryCooldown > 0) {
            delay(retryCooldown * 1000L + 1000L)
            retryCooldown = 0
            askGemini(prompt, extraContext)
        }
    }

    suspend fun performDeviceAudit() {
        isProcessing = true

        val auditCommands = listOf(
            "getprop ro.build.description",
            "getprop ro.product.model",
            "getprop ro.build.version.release",
            "getprop ro.build.date",
            "dumpsys battery | grep -E 'level|health|status|temperature|plugged|present'",
            "dumpsys accessibility",
            "id && getenforce",
            "pm list packages -3 | grep -E 'bank|finance|gov|proton|unibank|sima|az\\.'",
            "dumpsys deviceidle whitelist",
            "settings get global adb_wifi_enabled",
            "settings get global adb_authorization_timeout"
        )

        val results = ShizukuExecutor.executeBatch(auditCommands)

        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

        val auditReport = buildString {
            appendLine("=== DEVICE SECURITY AUDIT REPORT ===")
            appendLine("Timestamp: $timestamp")
            appendLine("Device Model: ${results.getOrNull(1)?.output ?: "Unknown"}\n")

            results.forEachIndexed { i, res ->
                appendLine("[Command ${i + 1}] ${auditCommands[i].take(65)}")
                appendLine(res.fullOutput.ifBlank { "<empty>" })
                appendLine("\u2500".repeat(60))
            }
        }

        val prompt = """
            FULL DEVICE SECURITY AUDIT
            
            $auditReport
        """.trimIndent()

        // Auto-save audit report to internal secure directory
        try {
            val dir = SecurityModule.getSecureLogsDir(context)
            if (!dir.exists()) dir.mkdirs()
            val auditFile = File(dir, "audit_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt")
            FileWriter(auditFile).use { fw -> fw.write(auditReport) }
            ShizukuExecutor.logListener?.invoke("INFO", "Audit report auto-saved: ${auditFile.name}")
        } catch (e: Exception) {
            ShizukuExecutor.logListener?.invoke("ERROR", "Auto-save audit failed: ${e.message}")
        }

        askGemini(prompt)
        showSaveDialog = true
    }

    suspend fun analyzeLogs() {
        isProcessing = true

        val recentLogs = GlobalLog.getRecentLogs(50)

        askGemini("""
            ANALYZE RECENT LOGS + IOC SEARCH
            
            === RECENT LOGS FROM ADB STUDIO ===
            $recentLogs
        """.trimIndent())
    }

    // ====================== BANKING DEEP SCAN ======================
    suspend fun performBankingDeepScan() {
        isProcessing = true
        messages = messages + ChatMessage("user", "🔴 BANKING DEEP SCAN — Threat Analysis")

        val bankingPackages = listOf(
            "az.unibank.mbanking",
            "az.dpc.sima",
            "az.gov.my",
            "iba.mobilbank",
            "ch.protonmail.android",
            "ch.protonvpn.android"
        )

        val scanReport = buildString {
            appendLine("=== BANKING DEEP SCAN REPORT ===")
            appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Target: Azerbaijani + Proton Banking Apps\n")

            bankingPackages.forEach { pkg ->
                appendLine("[$pkg]")
                val result = ShizukuExecutor.executeCommand("dumpsys package $pkg")
                
                if (result.isSuccessful) {
                    val output = result.output
                    appendLine("• Version: ${output.lines().firstOrNull { it.contains("versionName") }?.substringAfter("versionName=") ?: "N/A"}")
                    appendLine("• Installer: ${output.lines().firstOrNull { it.contains("installerPackageName") }?.substringAfter("installerPackageName=") ?: "N/A"}")
                    
                    // Опасные разрешения: ищем реальные имена в выводе dumpsys (android.permission.X: granted=true)
                    val dangerousPermissions = DANGEROUS_PERMISSION_REGEX.findAll(output)
                        .map { it.value }
                        .distinct()
                        .toList()
                    appendLine("• Dangerous Permissions: ${if (dangerousPermissions.isNotEmpty()) "DETECTED ⚠ ${dangerousPermissions.joinToString(", ")}" else "None"}")
                    
                    // Exported activities
                    if (output.contains("exported=true")) {
                        appendLine("• Exported Activities: FOUND (Possible attack surface)")
                    }
                } else {
                    appendLine("• Package not found or access denied")
                }
                appendLine("─".repeat(60))
            }
        }

        val prompt = """
            BANKING DEEP SCAN — Threat Analysis
            
            $scanReport
        """.trimIndent()

        askGemini(prompt)
        showSaveDialog = true
    }

    // ====================== EXPORT REPORT ======================
    fun exportCurrentReport() {
        if (messages.isEmpty()) {
            Toast.makeText(context, "Нет данных для сохранения", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "Audit_Report_$timestamp.txt"

        val reportContent = buildString {
            appendLine("VOID Auditor Security Audit Report")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Device: SM-A115F")
            appendLine("=".repeat(80))
            appendLine()

            messages.forEach { msg ->
                val role = if (msg.role == "user") "USER QUERY" else "AI RESPONSE"
                appendLine("[$role]")
                appendLine(msg.text)
                appendLine("-".repeat(80))
                appendLine()
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped storage: пишем в Downloads через MediaStore (разрешения не нужны)
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ADB_Studio_Logs")
                }
                val uri = resolver.insert(
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    values
                ) ?: throw Exception("MEDIASTORE_INSERT_FAILED")
                resolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream).use { writer -> writer.write(reportContent) }
                } ?: throw Exception("OUTPUT_STREAM_FAILED")
            } else {
                // API 26-28: прямое обращение к Downloads ещё работает
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(downloadsDir, "ADB_Studio_Logs")
                if (!appDir.exists()) appDir.mkdirs()
                FileWriter(File(appDir, filename)).use { writer -> writer.write(reportContent) }
            }

            Toast.makeText(
                context,
                "✅ Отчёт сохранён!\nDownloads/ADB_Studio_Logs/$filename",
                Toast.LENGTH_LONG
            ).show()

            ShizukuExecutor.logListener?.invoke("SUCCESS", "Report saved: $filename")

        } catch (e: Exception) {
            Toast.makeText(
                context,
                "❌ Ошибка сохранения: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
            ShizukuExecutor.logListener?.invoke("ERROR", "Export failed: ${e.message}")
        }
    }

    // ====================== UI ======================
    // ====================== AI PROPOSAL REVIEW (GOVERNANCE) ======================
    val activeProposal = pendingProposal
    if (activeProposal != null) {
        val proposalCapability = proposalToCapability(activeProposal)
        val proposalRisk = proposalCapability?.riskScore ?: 0
        val proposalRiskLabel = PolicyEngine.severityLabel(proposalRisk)
        val proposalRiskColor = when (proposalRiskLabel) {
            "CRITICAL" -> Color(0xFFFF2D55)
            "HIGH" -> Color(0xFFFF9500)
            "MEDIUM" -> Color(0xFFFFCC00)
            else -> CyberAccent
        }
        AlertDialog(
            onDismissRequest = { rejectProposal() },
            containerColor = CyberSurface,
            titleContentColor = CyberAccent,
            textContentColor = CyberText,
            title = { Text("> AI_PROPOSAL_REVIEW", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
            text = {
                Column {
                    Text(
                        "RISK: $proposalRiskLabel (${proposalRisk}/100)",
                        color = proposalRiskColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        activeProposal.advisoryText.ifBlank { "AI предлагает выполнить действие на устройстве." },
                        color = CyberText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "CAPABILITY: ${proposalCapability?.description ?: "UNMAPPED / NOT IN WHITELIST"}",
                        color = CyberInfo,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    activeProposal.confidence?.let {
                        Spacer(Modifier.height(6.dp))
                        Text("CONFIDENCE: ${(it * 100).toInt()}%", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { approveProposal() },
                    enabled = proposalCapability != null,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberAccent, contentColor = CyberBackground),
                    shape = RoundedCornerShape(2.dp)
                ) { Text("APPROVE & EXECUTE", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { rejectProposal() }) {
                    Text("REJECT", color = CyberWarning, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.border(1.dp, proposalRiskColor)
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = CyberSurface,
            titleContentColor = CyberAccent,
            textContentColor = CyberText,
            title = { Text("СОХРАНИТЬ ОТЧЕТ?", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
            text = { Text("Аудит успешно завершен. Хотите экспортировать полный отчет в файл?", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = { 
                        exportCurrentReport()
                        showSaveDialog = false 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberAccent, contentColor = CyberBackground),
                    shape = RoundedCornerShape(2.dp)
                ) { Text("ДА", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("НЕТ", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.border(1.dp, CyberAccent)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (showKeyInput) {
            CyberCard(title = "GEMINI_API_SETUP", color = CyberAccent, modifier = Modifier.padding(bottom = 10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Gemini API key (get at aistudio.google.com):", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("AIza...", color = Color.Gray, fontSize = 11.sp) },
                        textStyle = TextStyle(color = CyberInfo, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberAccent, unfocusedBorderColor = CyberBorder,
                            focusedContainerColor = CyberBackground, unfocusedContainerColor = CyberBackground
                        )
                    )
                    Button(
                        onClick = { saveKey(apiKey); showKeyInput = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberAccent, contentColor = CyberBackground),
                        shape = RoundedCornerShape(2.dp)
                    ) { Text("SAVE_KEY", fontWeight = FontWeight.Bold) }
                }
            }
            return
        }

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                availableModels.forEach { (model, label) ->
                    val active = selectedModel == model
                    Surface(
                        modifier = Modifier.height(24.dp).border(1.dp, if (active) CyberAccent else CyberBorder).clickable { selectedModel = model; saveModel(model) },
                        color = if (active) CyberAccent.copy(alpha = 0.15f) else Color.Transparent, shape = RoundedCornerShape(2.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text(label, color = if (active) CyberAccent else Color(0xFF64748B), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { clearChat() }) { Text("CLR", color = Color(0xFF64748B), fontSize = 9.sp) }
                TextButton(onClick = { showKeyInput = true }) { Text("KEY", color = Color(0xFF64748B), fontSize = 9.sp) }
            }
        }

        // Messages
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, CyberBorder),
            state = listState, contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text("AI_DEVICE_ASSISTANT", color = CyberInfo, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    Text("Ask about device issues, app optimization, ADB commands.\nExample: \"why isn't Element X getting notifications?\"",
                        color = Color.Gray, fontSize = 10.sp, lineHeight = 16.sp)
                }
            }
            items(messages) { msg ->
                val isUser = msg.role == "user"
                val riskLevel = if (!isUser) parseRiskLevel(msg.text) else RiskLevel.UNKNOWN
                val bgColor = if (isUser) CyberSurface else CyberBackground
                val accentColor = if (isUser) CyberInfo else getRiskColor(riskLevel)

                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        clipboardManager.setText(AnnotatedString(msg.text))
                    },
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isUser) "YOU" else "VOID AI",
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (!isUser && riskLevel != RiskLevel.UNKNOWN) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = when(riskLevel) {
                                            RiskLevel.CRITICAL -> "☢ CRITICAL"
                                            RiskLevel.HIGH -> "⚠ HIGH"
                                            RiskLevel.MEDIUM -> "⚡ MEDIUM"
                                            else -> "✓ LOW"
                                        },
                                        color = accentColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Text("COPY", color = Color(0xFF475569), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = msg.text,
                            color = CyberText,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            if (isProcessing) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = CyberAccent2, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text("AI_THINKING...", color = CyberAccent2, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (retryCooldown > 0) {
                item { Text("⏳ RETRY_IN ${retryCooldown}s", color = CyberAccent2, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp)) }
            }
        }

        // Action buttons
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = { scope.launch { performDeviceAudit() } }, enabled = !isProcessing,
                modifier = Modifier.weight(1f).height(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberAccent2.copy(alpha = 0.15f), contentColor = CyberAccent2),
                shape = RoundedCornerShape(2.dp), contentPadding = PaddingValues(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberAccent2.copy(alpha = 0.5f))
            ) { Icon(Icons.Default.Shield, null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("AUDIT", fontSize = 8.sp, fontWeight = FontWeight.Bold) }

            Button(
                onClick = { scope.launch { performBankingDeepScan() } }, enabled = !isProcessing,
                modifier = Modifier.weight(1f).height(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberWarning.copy(alpha = 0.15f), contentColor = CyberWarning),
                shape = RoundedCornerShape(2.dp), contentPadding = PaddingValues(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberWarning.copy(alpha = 0.5f))
            ) { Icon(Icons.Default.Security, null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("BANK SCAN", fontSize = 8.sp, fontWeight = FontWeight.Bold) }

            Button(
                onClick = { scope.launch { analyzeLogs() } }, enabled = !isProcessing,
                modifier = Modifier.weight(1f).height(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberInfo.copy(alpha = 0.15f), contentColor = CyberInfo),
                shape = RoundedCornerShape(2.dp), contentPadding = PaddingValues(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberInfo.copy(alpha = 0.5f))
            ) { Icon(Icons.Default.Analytics, null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("LOGS", fontSize = 8.sp, fontWeight = FontWeight.Bold) }

            Button(
                onClick = { exportCurrentReport() }, enabled = messages.isNotEmpty(),
                modifier = Modifier.weight(1f).height(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberAccent2.copy(alpha = 0.15f), contentColor = CyberAccent2),
                shape = RoundedCornerShape(2.dp), contentPadding = PaddingValues(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberAccent2.copy(alpha = 0.5f))
            ) { Icon(Icons.Default.Save, null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("EXPORT", fontSize = 8.sp, fontWeight = FontWeight.Bold) }
        }

        // Input
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = inputText, onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("ASK_AI...", color = Color.Gray, fontSize = 11.sp) },
                textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberAccent, unfocusedBorderColor = CyberBorder, focusedContainerColor = CyberBackground, unfocusedContainerColor = CyberBackground),
                enabled = !isProcessing && retryCooldown == 0
            )
            IconButton(
                onClick = { scope.launch { askGemini(inputText.trim()) } },
                modifier = Modifier.size(48.dp).border(1.dp, if (retryCooldown > 0) CyberAccent2 else CyberAccent).background(CyberSurface),
                enabled = !isProcessing && apiKey.isNotBlank() && retryCooldown == 0
            ) {
                if (retryCooldown > 0) Text("$retryCooldown", color = CyberAccent2, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                else Icon(Icons.Default.Send, null, tint = if (isProcessing) Color.Gray else CyberAccent)
            }
        }
    }
}

fun loadMessages(prefs: android.content.SharedPreferences): List<ChatMessage> {
    val raw = prefs.getString("chat_history", "") ?: ""
    if (raw.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)
            ChatMessage(obj.getString("r"), obj.getString("t"), obj.optString("rl", null))
        }
    } catch (_: Exception) { emptyList() }
}
