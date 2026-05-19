package com.kuzyamond.adbstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuzyamond.adbstudio.core.ShizukuExecutor
import kotlinx.coroutines.launch

@Composable
fun SecurityDashboardScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    var summary by remember { mutableStateOf<SecuritySummary?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    suspend fun runSecurityScan() {
        isScanning = true
        errorMsg = ""
        try {
            val auditCommands = listOf(
                "getprop ro.build.description",
                "getprop ro.product.model",
                "getprop ro.build.version.release",
                "id && getenforce",
                "settings get global adb_wifi_enabled",
                "dumpsys accessibility | head -30",
                "settings get secure install_non_market_apps",
                "pm list packages -3 | grep -E 'bank|finance|gov|proton|unibank|sima|az\\.'",
                "dumpsys deviceidle whitelist",
                "dumpsys device_policy | grep \"admin=\"",
                "pm list packages -3"
            )
            val results = ShizukuExecutor.executeBatch(auditCommands)
            val report = buildString {
                results.forEachIndexed { i, res ->
                    appendLine("[${auditCommands[i].take(50)}]")
                    appendLine(res.fullOutput.ifBlank { "<empty>" })
                    appendLine()
                }
            }
            summary = generateSecuritySummary(report)
        } catch (e: Exception) {
            errorMsg = "Scan error: ${e.localizedMessage ?: e.message}"
        }
        isScanning = false
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        CyberCard(title = "SECURITY_DASHBOARD", color = CyberAccent) {
            Text("Risk Aggregator v1.0 — комплексная оценка безопасности устройства",
                color = CyberText.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }

        if (summary == null && !isScanning && errorMsg.isEmpty()) {
            // Empty state
            CyberCard(title = "NO_DATA", color = CyberBorder) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Icon(Icons.Default.Shield, null, tint = CyberBorder, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Press SCAN to evaluate device security", color = CyberText.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
        }

        if (isScanning) {
            CyberCard(title = "SCANNING", color = CyberAccent2) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyberAccent2, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Running audit commands...", color = CyberAccent2, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (errorMsg.isNotEmpty()) {
            CyberCard(title = "ERROR", color = CyberWarning) {
                Text(errorMsg, color = CyberWarning, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        summary?.let { sec ->
            // Risk level banner
            val levelColor = getRiskColor(sec.level)
            val levelLabel = when(sec.level) {
                RiskLevel.CRITICAL -> "☢ CRITICAL"
                RiskLevel.HIGH -> "⚠ HIGH"
                RiskLevel.MEDIUM -> "⚡ MEDIUM"
                RiskLevel.LOW -> "✓ LOW"
                RiskLevel.UNKNOWN -> "? UNKNOWN"
            }

            CyberCard(title = "OVERALL_RISK_ASSESSMENT", color = levelColor) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Text(levelLabel, color = levelColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    // Score bar
                    Box(modifier = Modifier.fillMaxWidth().height(16.dp).border(1.dp, levelColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp))) {
                        Box(modifier = Modifier.fillMaxWidth(fraction = sec.score / 100f).fillMaxHeight().background(levelColor.copy(alpha = 0.4f)))
                        Text("${sec.score}/100", color = CyberText, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.Center))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${sec.issues.size} issues detected", color = CyberText.copy(alpha = 0.7f), fontSize = 10.sp)
                }
            }

            // Issues list
            if (sec.issues.isNotEmpty()) {
                CyberCard(title = "ISSUES_&_FIXES", color = CyberAccent) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sec.issues.forEach { issue ->
                            val issColor = getRiskColor(issue.severity)
                            val tag = when(issue.severity) {
                                RiskLevel.CRITICAL -> "CRIT"
                                RiskLevel.HIGH -> "HIGH"
                                RiskLevel.MEDIUM -> "MED"
                                RiskLevel.LOW -> "LOW"
                                RiskLevel.UNKNOWN -> "?"
                            }
                            Column(modifier = Modifier.fillMaxWidth().border(1.dp, issColor.copy(alpha = 0.3f)).background(issColor.copy(alpha = 0.05f)).padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = issColor.copy(alpha = 0.2f), shape = RoundedCornerShape(2.dp), border = androidx.compose.foundation.BorderStroke(1.dp, issColor.copy(alpha = 0.5f))) {
                                        Text(tag, color = issColor, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(issue.title, color = CyberText, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("→ ${issue.fix}", color = CyberText.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
                            }
                        }
                    }
                }
            }

            if (sec.issues.isEmpty()) {
                CyberCard(title = "ALL_CLEAR", color = CyberAccent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = CyberAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("No security issues detected. Device is in good shape.", color = CyberAccent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Scan button
        Button(
            onClick = { scope.launch { runSecurityScan() } },
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberAccent, contentColor = CyberBackground),
            shape = RoundedCornerShape(2.dp)
        ) {
            Icon(if (isScanning) Icons.Default.HourglassEmpty else Icons.Default.Security, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isScanning) "SCANNING..." else "REFRESH_SCAN", fontWeight = FontWeight.Bold)
        }
    }
}
