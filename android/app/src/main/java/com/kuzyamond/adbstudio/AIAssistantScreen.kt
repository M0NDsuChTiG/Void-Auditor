package com.kuzyamond.adbstudio

import android.content.Context
import android.os.Environment
import android.widget.Toast
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
import com.kuzyamond.adbstudio.core.ShizukuExecutor
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

val availableModels = listOf(
    "gemini-2.0-flash-lite" to "FLASH_LITE",
    "gemini-2.0-flash" to "FLASH",
    "gemini-1.5-flash" to "1.5_FLASH"
)

val systemInstruction = """
Ты — сертифицированный Senior Android Security & Digital Forensics эксперт (Red/Blue Team, 10+ лет опыта).
Специализация: Shizuku, ADB, pm/am/dumpsys, SELinux, Accessibility Abuse, Banking Trojan detection, IOC hunting, DevSecOps.

Правила ответа (обязательно соблюдать):
- Начинай строго с **RISK LEVEL**: Low / Medium / High / Critical
- Чётко выделяй **найденные IOC** (подозрительные разрешения, сервисы, пакеты, поведение)
- Для азербайджанских банковских приложений (az.unibank, az.dpc.sima и т.д.) — повышенное внимание
- Всегда давай готовые команды для ADB Studio (SHELL / SCRIPT_EXECUTOR)
- Предлагай автоматизированные скрипты и hardening-рекомендации
- Отвечай на русском, структурировано, в профессиональном кибер-стиле
""".trimIndent()

@Composable
fun AIAssistantScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val prefs = remember { context.getSharedPreferences("adb_studio_ai", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString("gemini_key", "") ?: "") }
    var selectedModel by remember { mutableStateOf(prefs.getString("gemini_model", "gemini-2.0-flash-lite") ?: "gemini-2.0-flash-lite") }
    var showKeyInput by remember { mutableStateOf(apiKey.isEmpty()) }
    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(loadMessages(prefs)) }
    var isProcessing by remember { mutableStateOf(false) }
    var retryCooldown by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        ShizukuExecutor.init()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LaunchedEffect(retryCooldown) {
        if (retryCooldown > 0) { delay(1000); retryCooldown-- }
    }

    fun saveKey(key: String) { prefs.edit().putString("gemini_key", key.trim()).apply() }
    fun saveModel(model: String) { prefs.edit().putString("gemini_model", model).apply() }
    fun saveMessages() {
        val json = JSONArray().apply {
            messages.forEach { put(JSONObject().apply { put("r", it.role); put("t", it.text); it.riskLevel?.let { l -> put("rl", l) } }) }
        }.toString()
        prefs.edit().putString("chat_history", json).apply()
    }
    fun clearChat() { messages = emptyList(); prefs.edit().remove("chat_history").apply() }

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

        // Auto-save audit report to file
        try {
            val dir = File(Environment.getExternalStorageDirectory(), "ADB_Studio_Logs")
            if (!dir.exists()) dir.mkdirs()
            val auditFile = File(dir, "audit_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt")
            FileWriter(auditFile).use { it.write(auditReport) }
            ShizukuExecutor.logListener?.invoke("INFO", "Audit report auto-saved: ${auditFile.name}")
        } catch (e: Exception) {
            ShizukuExecutor.logListener?.invoke("ERROR", "Auto-save audit failed: ${e.message}")
        }

        askGemini(prompt)
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

    // ====================== EXPORT REPORT ======================
    // ====================== EXPORT REPORT (FIXED) ======================
    fun exportCurrentReport() {
        if (messages.isEmpty()) {
            Toast.makeText(context, "Нет данных для сохранения", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "Audit_Report_$timestamp.txt"

        val reportContent = buildString {
            appendLine("ADB Studio Security Audit Report")
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
            // Новое надёжное место — Downloads (работает стабильно)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(downloadsDir, "ADB_Studio_Logs")
            if (!appDir.exists()) appDir.mkdirs()

            val file = File(appDir, filename)
            
            FileWriter(file).use { writer ->
                writer.write(reportContent)
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
    Column(modifier = Modifier.fillMaxSize()) {
        if (showKeyInput) {
            CyberCard(title = "GEMINI_API_SETUP", color = CyberLime, modifier = Modifier.padding(bottom = 10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Gemini API key (get at aistudio.google.com):", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("AIza...", color = Color.Gray, fontSize = 11.sp) },
                        textStyle = TextStyle(color = CyberCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberLime, unfocusedBorderColor = CyberBorder,
                            focusedContainerColor = CyberBackground, unfocusedContainerColor = CyberBackground
                        )
                    )
                    Button(
                        onClick = { saveKey(apiKey); showKeyInput = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberLime, contentColor = CyberBackground),
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
                        modifier = Modifier.height(24.dp).border(1.dp, if (active) CyberLime else CyberBorder).clickable { selectedModel = model; saveModel(model) },
                        color = if (active) CyberLime.copy(alpha = 0.15f) else Color.Transparent, shape = RoundedCornerShape(2.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text(label, color = if (active) CyberLime else Color(0xFF64748B), fontSize = 7.sp, fontWeight = FontWeight.Bold)
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
                    Text("AI_DEVICE_ASSISTANT", color = CyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    Text("Ask about device issues, app optimization, ADB commands.\nExample: \"why isn't Element X getting notifications?\"",
                        color = Color.Gray, fontSize = 10.sp, lineHeight = 16.sp)
                }
            }
            items(messages) { msg ->
                val isUser = msg.role == "user"
                Column(modifier = Modifier.fillMaxWidth().border(1.dp, if (isUser) CyberCyan.copy(alpha = 0.5f) else CyberBorder)
                    .background(if (isUser) CyberSurface else CyberBackground).padding(8.dp).clickable {
                        clipboardManager.setText(AnnotatedString(msg.text))
                    }) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (isUser) "YOU" else "AI", color = if (isUser) CyberCyan else CyberLime, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            if (!isUser && msg.riskLevel != null) {
                                val riskColor = when (msg.riskLevel.lowercase()) {
                                    "critical" -> Color(0xFFEF4444)
                                    "high" -> Color(0xFFF59E0B)
                                    "medium" -> Color(0xFFEAB308)
                                    "low" -> Color(0xFF22C55E)
                                    else -> Color(0xFF64748B)
                                }
                                Surface(
                                    color = riskColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(2.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, riskColor.copy(alpha = 0.5f))
                                ) {
                                    Text(msg.riskLevel.uppercase(), color = riskColor, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                        }
                        Text("COPY", color = Color(0xFF475569), fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(msg.text, color = Color(0xFFCBD5E1), fontSize = 10.sp, lineHeight = 14.sp, fontFamily = FontFamily.Monospace)
                }
            }
            if (isProcessing) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = CyberAmber, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp)); Text("AI_THINKING...", color = CyberAmber, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (retryCooldown > 0) {
                item { Text("⏳ RETRY_IN ${retryCooldown}s", color = CyberAmber, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp)) }
            }
        }

        // Action buttons
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = { scope.launch { performDeviceAudit() } }, enabled = !isProcessing,
                modifier = Modifier.weight(1f).height(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberAmber.copy(alpha = 0.15f), contentColor = CyberAmber),
                shape = RoundedCornerShape(2.dp), contentPadding = PaddingValues(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberAmber.copy(alpha = 0.5f))
            ) { Icon(Icons.Default.Shield, null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("AUDIT", fontSize = 8.sp, fontWeight = FontWeight.Bold) }

            Button(
                onClick = { scope.launch { analyzeLogs() } }, enabled = !isProcessing,
                modifier = Modifier.weight(1f).height(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f), contentColor = CyberCyan),
                shape = RoundedCornerShape(2.dp), contentPadding = PaddingValues(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
            ) { Icon(Icons.Default.Analytics, null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("LOGS", fontSize = 8.sp, fontWeight = FontWeight.Bold) }

            Button(
                onClick = { exportCurrentReport() }, enabled = messages.isNotEmpty(),
                modifier = Modifier.weight(1f).height(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald.copy(alpha = 0.15f), contentColor = CyberEmerald),
                shape = RoundedCornerShape(2.dp), contentPadding = PaddingValues(0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.5f))
            ) { Icon(Icons.Default.Save, null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("EXPORT", fontSize = 8.sp, fontWeight = FontWeight.Bold) }
        }

        // Input
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = inputText, onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("ASK_AI...", color = Color.Gray, fontSize = 11.sp) },
                textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberLime, unfocusedBorderColor = CyberBorder, focusedContainerColor = CyberBackground, unfocusedContainerColor = CyberBackground),
                enabled = !isProcessing && retryCooldown == 0
            )
            IconButton(
                onClick = { scope.launch { askGemini(inputText.trim()) } },
                modifier = Modifier.size(48.dp).border(1.dp, if (retryCooldown > 0) CyberAmber else CyberLime).background(CyberSurface),
                enabled = !isProcessing && apiKey.isNotBlank() && retryCooldown == 0
            ) {
                if (retryCooldown > 0) Text("$retryCooldown", color = CyberAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                else Icon(Icons.Default.Send, null, tint = if (isProcessing) Color.Gray else CyberLime)
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
