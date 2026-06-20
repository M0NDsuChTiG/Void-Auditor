package com.kuzyamond.voidauditor

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.os.Bundle
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import com.kuzyamond.voidauditor.core.AuditLogger
import com.kuzyamond.voidauditor.security.SecurityModule
import com.kuzyamond.voidauditor.core.ConfirmationDialog
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

// --- ТЕМА И ЦВЕТА ---
val CyberBackground = Color(0xFF0A0F0A)     // Очень тёмный зелёный
val CyberSurface   = Color(0xFF121712)
val CyberAccent    = Color(0xFF39FF14)     // Кислотный Lime Green
val CyberAccent2   = Color(0xFF00FF9F)     // Мятный
val CyberText      = Color(0xFFB3FFBD)
val CyberWarning   = Color(0xFFFF2D55)
val CyberInfo      = Color(0xFF00E5FF)
val CyberBorder    = Color(0xFF1E293B) // Оставляем темную границу для контраста

@Composable
fun ADBStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CyberAccent,
            background = CyberBackground,
            surface = CyberSurface,
            error = CyberWarning,
            secondary = CyberAccent2,
            onBackground = CyberText,
            onSurface = CyberText
        ),
        content = content
    )
}

// --- ГЛОБАЛЬНЫЙ ЛОГ С УПРАВЛЕНИЕМ СОСТОЯНИЕМ ---
data class LogEntry(val msg: String, val type: String, val tab: String)

object GlobalLog {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    private val _isExpanded = MutableStateFlow(true)
    val isExpanded: StateFlow<Boolean> = _isExpanded

    private var logsDir: File? = null

    fun init(context: Context) {
        logsDir = SecurityModule.getSecureLogsDir(context)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun log(msg: String, type: String = "info", tab: String = "GLOBAL") {
        val prefix = when(type) {
            "crit" -> "[!]"
            "ok" -> "[+]"
            "warn" -> "[*]"
            else -> ">"
        }
        val time = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val formattedMsg = "[$time] $prefix $msg"
        val newEntry = LogEntry(formattedMsg, type, tab)
        _entries.value = (listOf(newEntry) + _entries.value).take(200)

        GlobalScope.launch(Dispatchers.IO) {
            val dir = logsDir ?: return@launch
            try {
                if (!dir.exists()) dir.mkdirs()
                val file = java.io.File(dir, "${tab}_${date}.log")
                file.appendText("$formattedMsg\n")
            } catch (_: Exception) {}
        }
    }

    fun getRecentLogs(count: Int = 50): String {
        return _entries.value.take(count)
            .joinToString("\n") { it.msg }
    }

    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }

    fun clear(tab: String) {
        _entries.value = _entries.value.filter { it.tab != tab && it.tab != "GLOBAL" }
    }
}

// --- ОСНОВНОЙ ЭКРАН ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GlobalLog.init(this)
        setContent {
            ADBStudioTheme {
                MainLayout()
            }
        }
    }
}

@Composable
fun MainLayout() {
    val context = LocalContext.current
    var isAuthenticated by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var authAttempts by remember { mutableStateOf(0) }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            isAuthenticated = true
        } else {
            authError = "Аутентификация не выполнена"
        }
    }

    if (authAttempts >= 0) {
        LaunchedEffect(authAttempts) {
            val activity = context as? Activity
            if (!isAuthenticated && activity != null && authError == null) {
                if (SecurityModule.canAuthenticate(context)) {
                    val intent = SecurityModule.createCredentialIntent(activity)
                    if (intent != null) {
                        authLauncher.launch(intent)
                    } else {
                        authError = "Устройство не поддерживает аутентификацию"
                    }
                } else {
                    authError = "Не настроен экран блокировки или биометрия"
                }
            }
        }
    }

    if (!isAuthenticated) {
        SecurityGate(authError) { authAttempts++; authError = null }
        return
    }

    var activeTab by remember { mutableStateOf("AUDIT") }
    var showInfoDialog by remember { mutableStateOf(false) }
    val status by ShizukuManager.isAuthorizedFlow().collectAsState(initial = false)
    val allLogs by GlobalLog.entries.collectAsState()
    val isLogExpanded by GlobalLog.isExpanded.collectAsState()
    
    val filteredLogs = remember(allLogs, activeTab) {
        allLogs.filter { it.tab == "GLOBAL" || it.tab == activeTab }
    }

    Box(modifier = Modifier.fillMaxSize().background(CyberBackground)) {
        ScanlineAnimation()

        Column(modifier = Modifier.fillMaxSize()) {
            Header(status, onInfoClick = { showInfoDialog = true })

            LaunchedEffect(Unit) {
                AuditLogger.integrateWithExecutor()
            }

            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).background(Color(0xFF090F1A)).border(1.dp, CyberBorder).horizontalScroll(rememberScrollState())) {
                 NavTab("AUDIT", Icons.Default.Shield, activeTab == "AUDIT") { activeTab = "AUDIT" }
                 NavTab("TRACE", Icons.Default.History, activeTab == "TRACE") { activeTab = "TRACE" }
                 NavTab("APPS", Icons.Default.List, activeTab == "APPS") { activeTab = "APPS" }
                 NavTab("ACTIVITY", Icons.Default.Explore, activeTab == "ACTIVITY") { activeTab = "ACTIVITY" }
                 NavTab("BACKUP", Icons.Default.Download, activeTab == "BACKUP") { activeTab = "BACKUP" }
                 NavTab("CONN", Icons.Default.Wifi, activeTab == "CONN") { activeTab = "CONN" }
                 NavTab("FS", Icons.Default.Folder, activeTab == "FS") { activeTab = "FS" }
                 NavTab("SCRIPTS", Icons.Default.PlayArrow, activeTab == "SCRIPTS") { activeTab = "SCRIPTS" }
                 NavTab("SHELL", Icons.Default.Terminal, activeTab == "SHELL") { activeTab = "SHELL" }
                 NavTab("AI", Icons.Default.SmartToy, activeTab == "AI") { activeTab = "AI" }
            }

            Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                when (activeTab) {
                    "AUDIT" -> DashboardScreen()
                    "TRACE" -> AuditScreen()
                    "APPS" -> AppManagerScreen()
                    "ACTIVITY" -> ActivityLauncherScreen()
                    "BACKUP" -> BackupScreen()
                    "CONN" -> ConnectScreen()
                    "FS" -> FilesScreen()
                    "SCRIPTS" -> ScriptsScreen()
                    "SHELL" -> TerminalScreen()
                    "AI" -> AIAssistantScreen()
                }
            }

            if (activeTab != "APPS") {
                LogStream(filteredLogs.map { it.msg }, activeTab, isLogExpanded)
            }
        }

        if (showInfoDialog) {
            InfoDialog(onDismiss = { showInfoDialog = false })
        }

        ConfirmationDialog(
            cyberBackground = CyberBackground,
            cyberSurface = CyberSurface,
            cyberAccent = CyberAccent,
            cyberWarning = CyberWarning,
            cyberText = CyberText
        )
    }
}

@Composable
fun Header(isOnline: Boolean, onInfoClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).background(CyberSurface).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☣", color = CyberAccent, fontSize = 20.sp, modifier = Modifier.cyberClickable(onInfoClick))
            Spacer(Modifier.width(10.dp))
            Text("VOID AUDITOR", 
                fontWeight = FontWeight.ExtraBold, 
                color = Color.White,
                letterSpacing = 2.sp,
                modifier = Modifier.cyberClickable(onInfoClick)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = if (isOnline) CyberAccent2 else CyberWarning
            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(percent = 50)).background(color))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isOnline) "SYSTEM_ACTIVE" else "SYSTEM_OFFLINE",
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(15.dp))
            Icon(
                Icons.Default.Info, 
                null, 
                tint = CyberAccent, 
                modifier = Modifier.size(20.dp).cyberClickable(onInfoClick)
            )
        }
    }
}

@Composable
fun InfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurface,
        titleContentColor = CyberAccent,
        textContentColor = CyberText,
        title = { Text("> VOID_SYSTEM_INFO", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
        text = {
            Column {
                Text("APP: VOID Auditor — CyberHack Edition", color = CyberAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("VERSION: V12.5 [LIME_SHOCK]", color = CyberAccent2, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(15.dp))
                Text(
                    "Автономный хакерский комплекс для глубокого аудита и управления Android-системами. " +
                    "Использует Shizuku API и нейронные сети для анализа угроз.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Text("DEVELOPER: Gemini CLI [CORE_SYNC]", color = Color.Gray, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("DISMISS", color = CyberAccent, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier.border(1.dp, CyberAccent)
    )
}

@Composable
fun RowScope.NavTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.widthIn(min = 64.dp).padding(vertical = 10.dp).drawBehind {
            if (selected) {
                drawLine(CyberAccent, Offset(0f, size.height), Offset(size.width, size.height), 4f)
            }
        }.cyberClickable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val iconColor = if (selected) CyberAccent else Color(0xFF475569)
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp))
        Text(label, color = iconColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LogStream(entries: List<String>, currentTab: String, isExpanded: Boolean) {
    val height by animateDpAsState(if (isExpanded) 280.dp else 36.dp, label = "logHeight")
    val clipboardManager = LocalClipboardManager.current
    
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).height(height).fillMaxWidth().border(1.dp, CyberAccent).background(CyberBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(CyberSurface).clickable { GlobalLog.setExpanded(!isExpanded) }.padding(8.dp), 
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess, 
                    null, 
                    tint = CyberAccent, 
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("> ${currentTab}_HACK_STREAM", color = CyberAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("[COPY]", color = Color(0xFF64748B), fontSize = 9.sp, modifier = Modifier.cyberClickable { 
                    val logText = entries.joinToString("\n")
                    clipboardManager.setText(AnnotatedString(logText))
                    GlobalLog.log("COPIED_TO_CLIPBOARD", "ok", currentTab)
                })
                Text("[CLEAR]", color = Color(0xFF64748B), fontSize = 9.sp, modifier = Modifier.cyberClickable { 
                    GlobalLog.clear(currentTab)
                })
            }
        }
        if (isExpanded) {
            LazyColumn(modifier = Modifier.padding(10.dp), reverseLayout = true) {
                items(entries) { entry ->
                    Text(entry, color = Color(0xFFCBD5E1), fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
fun ScanlineAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    val yOffset by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(animation = tween(12000, easing = LinearEasing))
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawLine(
            color = CyberAccent.copy(alpha = 0.08f),
            start = Offset(0f, yOffset),
            end = Offset(size.width, yOffset),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
fun CyberCard(modifier: Modifier = Modifier, title: String? = null, color: Color = CyberAccent, content: @Composable () -> Unit) {
    Column(modifier = modifier.border(1.dp, CyberBorder).background(CyberSurface.copy(alpha = 0.4f)).padding(15.dp)) {
        if (title != null) {
            Text("> $title", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 15.dp))
        }
        content()
    }
}

@Composable
fun Modifier.cyberClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}

@Composable
fun SecurityGate(error: String?, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(CyberBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("☣", color = CyberAccent, fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "VOID AUDITOR",
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 2.sp,
                fontSize = 24.sp
            )
            Spacer(Modifier.height(24.dp))
            if (error != null) {
                Text(
                    error,
                    color = CyberWarning,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberAccent,
                        contentColor = CyberBackground
                    )
                ) {
                    Text("RETRY", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "AUTHENTICATION REQUIRED",
                    color = CyberText.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp
                )
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(
                    color = CyberAccent,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
