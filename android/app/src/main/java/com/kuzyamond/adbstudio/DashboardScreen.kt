package com.kuzyamond.adbstudio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        // --- БАЗОВЫЙ АУДИТ ---
        CyberCard(title = "LOCAL_AUDIT", color = CyberAccent) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuditButton("BUILD_PROP", CyberAccent, Modifier.weight(1f)) {
                        scope.launch {
                            GlobalLog.log("FETCHING BUILD_PROP...", "warn", "AUDIT")
                            val res = ShizukuManager.executeCommand("getprop ro.build.type")
                            res.onSuccess { GlobalLog.log("BUILD_TYPE: $it", "ok", "AUDIT") }
                               .onFailure { GlobalLog.log("ERR: ${it.message}", "crit", "AUDIT") }
                        }
                    }
                    AuditButton("HW_MAP", CyberAccent, Modifier.weight(1f)) {
                        scope.launch {
                            GlobalLog.log("MAPPING HARDWARE...", "warn", "AUDIT")
                            val res = ShizukuManager.executeCommand("pm list features")
                            res.onSuccess { 
                                val summary = it.split("\n").take(5).joinToString(", ")
                                GlobalLog.log("FEATURES: $summary...", "ok", "AUDIT") 
                            }
                               .onFailure { GlobalLog.log("ERR: ${it.message}", "crit", "AUDIT") }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuditButton("BATTERY", CyberAccent, Modifier.weight(1f)) {
                        scope.launch {
                            GlobalLog.log("DUMPING BATTERY...", "warn", "AUDIT")
                            val res = ShizukuManager.executeCommand("dumpsys battery")
                            res.onSuccess { GlobalLog.log("BATTERY_STATUS:\n$it", "ok", "AUDIT") }
                               .onFailure { GlobalLog.log("ERR: ${it.message}", "crit", "AUDIT") }
                        }
                    }
                    AuditButton("LOCAL_UID", CyberAccent, Modifier.weight(1f)) {
                        scope.launch {
                            GlobalLog.log("GETTING UID...", "warn", "AUDIT")
                            val res = ShizukuManager.executeCommand("id")
                            res.onSuccess { GlobalLog.log("UID: $it", "ok", "AUDIT") }
                               .onFailure { GlobalLog.log("ERR: ${it.message}", "crit", "AUDIT") }
                        }
                    }
                }
            }
        }

        // --- УМНЫЙ МОНИТОРИНГ (на основе кейса с Bluetooth) ---
        CyberCard(title = "SMART_FORENSICS", color = CyberAccent2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuditButton("BT_LOG", CyberAccent2, Modifier.weight(1f)) {
                        scope.launch {
                            GlobalLog.log("ANALYZING BT_HISTORY...", "warn", "AUDIT")
                            val res = ShizukuManager.executeCommand("dumpsys bluetooth_manager | grep -A 15 \"Enable log:\"")
                            res.onSuccess { GlobalLog.log("BT_ACTIVATION_LOG:\n$it", "ok", "AUDIT") }
                               .onFailure { GlobalLog.log("ERR: ${it.message}", "crit", "AUDIT") }
                        }
                    }
                    AuditButton("DANGEROUS_OPS", CyberAccent2, Modifier.weight(1f)) {
                        scope.launch {
                            GlobalLog.log("CHECKING BT_PERMISSIONS...", "warn", "AUDIT")
                            val res = ShizukuManager.executeCommand("appops query-op BLUETOOTH_SCAN allow")
                            res.onSuccess { GlobalLog.log("APPS_WITH_BT_SCAN:\n$it", "ok", "AUDIT") }
                               .onFailure { GlobalLog.log("ERR: ${it.message}", "crit", "AUDIT") }
                        }
                    }
                }
                AuditButton("GEO_PRECISION_CHECK", CyberAccent2, Modifier.fillMaxWidth()) {
                    scope.launch {
                        GlobalLog.log("CHECKING GOOGLE_LOC_PRECISION...", "warn", "AUDIT")
                        val res = ShizukuManager.executeCommand("settings get secure location_precision_state")
                        res.onSuccess { 
                            val status = if(it == "1") "ENABLED (DANGEROUS)" else "DISABLED (SAFE)"
                            GlobalLog.log("LOC_PRECISION: $status", if(it == "1") "warn" else "ok", "AUDIT") 
                        }
                           .onFailure { GlobalLog.log("ERR: ${it.message}", "crit", "AUDIT") }
                    }
                }
            }
        }

        CyberCard(title = "STANDALONE_MODE", color = CyberInfo) {
            Text(
                "Работает через Shizuku. Компьютер и сервер больше не нужны. Все команды исполняются локально.",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                lineHeight = 16.sp
            )
        }
        
        Button(
            onClick = { ShizukuManager.requestPermission() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CyberInfo, contentColor = CyberBackground),
            shape = RoundedCornerShape(2.dp)
        ) {
            Text("FORCE_AUTHORIZE", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AuditButton(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
