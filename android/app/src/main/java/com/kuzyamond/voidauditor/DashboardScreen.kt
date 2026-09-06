package com.kuzyamond.voidauditor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import com.kuzyamond.voidauditor.core.PolicyEngine
import com.kuzyamond.voidauditor.core.USFPipeline
import com.kuzyamond.voidauditor.core.EvidenceResult
import com.kuzyamond.voidauditor.core.evidence.FeaturesEvidence
import com.kuzyamond.voidauditor.core.evidence.UserIdentityEvidence
import com.kuzyamond.voidauditor.core.evidence.SettingEvidence
import com.kuzyamond.voidauditor.core.evidence.DangerousPermissionsEvidence
import com.kuzyamond.voidauditor.core.evidence.ServiceDumpEvidence
import com.kuzyamond.voidauditor.core.evidence.AppOpsEvidence
import com.kuzyamond.voidauditor.core.evidence.SystemPropEvidence
import com.kuzyamond.voidauditor.core.evidence.ServiceStateEvidence
import com.kuzyamond.voidauditor.core.Capability.RemediationIntent
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    var auditSummary by remember { mutableStateOf<USFPipeline.AuditSummary?>(null) }
    var scanning by remember { mutableStateOf(false) }

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
                            val result = CapabilityExecutor.execute(USFPipeline.Context(), Capability.ReadSystemProp("ro.build.type"))
                            if (result.commandResult.isSuccessful) {
                                val value = (result.evidence as? EvidenceResult.Parsed)?.evidence
                                    ?.let { (it as? SystemPropEvidence)?.value }
                                    ?: result.commandResult.output
                                GlobalLog.log("BUILD_TYPE: $value", "ok", "AUDIT")
                            } else GlobalLog.log("ERR: ${result.commandResult.error}", "crit", "AUDIT")
                        }
                    }
                    AuditButton("HW_MAP", CyberAccent, Modifier.weight(1f)) {
                        scope.launch {
                            GlobalLog.log("MAPPING HARDWARE...", "warn", "AUDIT")
                            val result = CapabilityExecutor.execute(USFPipeline.Context(), Capability.ReadSystemFeatures)
                            if (result.commandResult.isSuccessful) {
                                val features = (result.evidence as? EvidenceResult.Parsed)?.evidence
                                    ?.let { (it as? FeaturesEvidence)?.features }
                                    ?: result.commandResult.output.split("\n").take(5)
                                val summary = features.joinToString(", ")
                                GlobalLog.log("FEATURES: $summary...", "ok", "AUDIT")
                            } else GlobalLog.log("ERR: ${result.commandResult.error}", "crit", "AUDIT")
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuditButton("BATTERY", CyberAccent, Modifier.weight(1f)) {
                        scope.launch {
                            GlobalLog.log("DUMPING BATTERY...", "warn", "AUDIT")
                            val result = CapabilityExecutor.execute(USFPipeline.Context(), Capability.DumpService("battery"))
                            if (result.commandResult.isSuccessful) {
                                val evidence = (result.evidence as? EvidenceResult.Parsed)?.evidence as? ServiceDumpEvidence
                                GlobalLog.log("BATTERY_STATUS:\n${evidence?.output ?: result.commandResult.output}", "ok", "AUDIT")
                            } else GlobalLog.log("ERR: ${result.commandResult.error}", "crit", "AUDIT")
                        }
                    }
                    AuditButton("LOCAL_UID", CyberAccent, Modifier.weight(1f)) {
                        scope.launch {
                            GlobalLog.log("GETTING UID...", "warn", "AUDIT")
                            val result = CapabilityExecutor.execute(USFPipeline.Context(), Capability.ReadUserIdentity)
                            if (result.commandResult.isSuccessful) {
                                val uid = (result.evidence as? EvidenceResult.Parsed)?.evidence
                                    ?.let { (it as? UserIdentityEvidence)?.uid }
                                    ?: result.commandResult.output
                                GlobalLog.log("UID: $uid", "ok", "AUDIT")
                            } else GlobalLog.log("ERR: ${result.commandResult.error}", "crit", "AUDIT")
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
                            val result = CapabilityExecutor.execute(USFPipeline.Context(), Capability.ReadServiceState("bluetooth_manager"))
                            if (result.commandResult.isSuccessful) {
                                val evidence = (result.evidence as? EvidenceResult.Parsed)?.evidence as? ServiceStateEvidence
                                GlobalLog.log("BT_ACTIVATION_LOG:\n${evidence?.output ?: result.commandResult.output}", "ok", "AUDIT")
                            } else GlobalLog.log("ERR: ${result.commandResult.error}", "crit", "AUDIT")
                        }
                    }
                    AuditButton("DANGEROUS_OPS", CyberAccent2, Modifier.weight(1f)) {
                        scope.launch {
                            GlobalLog.log("CHECKING BT_PERMISSIONS...", "warn", "AUDIT")
                            val result = CapabilityExecutor.execute(USFPipeline.Context(), Capability.ReadAppOps("BLUETOOTH_SCAN"))
                            if (result.commandResult.isSuccessful) {
                                val evidence = (result.evidence as? EvidenceResult.Parsed)?.evidence as? AppOpsEvidence
                                GlobalLog.log("APPS_WITH_BT_SCAN:\n${evidence?.output ?: result.commandResult.output}", "ok", "AUDIT")
                            } else GlobalLog.log("ERR: ${result.commandResult.error}", "crit", "AUDIT")
                        }
                    }
                }
                AuditButton("GEO_PRECISION_CHECK", CyberAccent2, Modifier.fillMaxWidth()) {
                    scope.launch {
                        GlobalLog.log("CHECKING GOOGLE_LOC_PRECISION...", "warn", "AUDIT")
                        val result = CapabilityExecutor.execute(USFPipeline.Context(), Capability.ReadSetting("secure", "location_precision_state"))
                        if (result.commandResult.isSuccessful) {
                            val value = (result.evidence as? EvidenceResult.Parsed)?.evidence
                                ?.let { (it as? SettingEvidence)?.value }
                                ?: result.commandResult.output
                            val status = if (value == "1") "ENABLED (DANGEROUS)" else "DISABLED (SAFE)"
                            GlobalLog.log("LOC_PRECISION: $status", if (value == "1") "warn" else "ok", "AUDIT")
                        } else GlobalLog.log("ERR: ${result.commandResult.error}", "crit", "AUDIT")
                    }
                }
            }
        }

        // --- FULL AUDIT SCAN ---
        CyberCard(title = "GOVERNANCE_AUDIT", color = Color(0xFFFF9500)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (auditSummary == null) {
                    AuditButton(
                        if (scanning) "SCANNING..." else "RUN_ZERO_TRUST_AUDIT",
                        if (scanning) Color.Gray else Color(0xFFFF9500),
                        Modifier.fillMaxWidth(),
                        enabled = !scanning
                    ) {
                        scope.launch {
                            scanning = true
                            GlobalLog.log("STARTING ZERO_TRUST_AUDIT...", "warn", "GOV")
                            CapabilityExecutor.resetAudit()

                            val checks = listOf(
                                Capability.ReadSystemProp("ro.build.type"),
                                Capability.ReadSystemProp("ro.debuggable"),
                                Capability.ReadSystemProp("ro.secure"),
                                Capability.ReadDangerousPermissions,
                                Capability.DumpService("battery"),
                                Capability.ReadSetting("global", "airplane_mode_on"),
                                Capability.ReadSetting("secure", "location_precision_state")
                            )

                            for (check in checks) {
                                CapabilityExecutor.execute(USFPipeline.Context(), check)
                            }

                            auditSummary = CapabilityExecutor.getSummary()
                            scanning = false

                            GlobalLog.log(
                                "AUDIT_DONE: ${auditSummary!!.passed}/${auditSummary!!.total} passed, " +
                                "${auditSummary!!.issues.size} issues",
                                if (auditSummary!!.issues.isEmpty()) "ok" else "crit",
                                "GOV"
                            )
                        }
                    }
                }

                // --- Auto-Summary + Quick Fix ---
                if (auditSummary != null) {
                    val summary = auditSummary!!

                    CyberCard(
                        title = "AUDIT_SUMMARY",
                        color = if (summary.issues.isEmpty()) CyberAccent else CyberWarning
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "PASSED: ${summary.passed}/${summary.total}",
                                color = CyberAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "FAILED: ${summary.failed}",
                                color = if (summary.failed > 0) CyberWarning else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "BLOCKED: ${summary.blocked}",
                                color = if (summary.blocked > 0) CyberWarning else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (summary.issues.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "ISSUES (${summary.issues.size}):",
                                    color = CyberWarning,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                summary.issues.forEach { issue ->
                                    Text(
                                        "[${issue.severity}] ${issue.description}",
                                        color = when (issue.severity) {
                                            "CRITICAL" -> Color(0xFFFF2D55)
                                            "HIGH" -> Color(0xFFFF9500)
                                            else -> Color(0xFFFFCC00)
                                        },
                                        fontSize = 9.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }

                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            GlobalLog.log("APPLYING QUICK_FIXES...", "warn", "GOV")
                                            val quickFixes = summary.issues.filter { it.fixCommand != null }
                                            quickFixes.forEach { issue ->
                                                GlobalLog.log(
                                                    "FIX: ${issue.fixCommand}",
                                                    "ok", "GOV"
                                                )
                                                val result = CapabilityExecutor.execute(USFPipeline.Context(), RemediationIntent.DisableService)
if (result.commandResult.isSuccessful) {
                                                        GlobalLog.log("FIX_OK: ${issue.fixCommand}", "ok", "GOV")
                                                    } else {
                                                        GlobalLog.log("FIX_FAIL: ${result.commandResult.error}", "crit", "GOV")
                                                    }
                                            }
                                            auditSummary = null
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyberWarning,
                                        contentColor = CyberBackground
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(2.dp)
                                ) {
                                    Text(
                                        "QUICK FIX RECOMMENDED (${summary.issues.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Button(
                                onClick = { auditSummary = null },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E293B),
                                    contentColor = CyberAccent
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Text("RESET", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
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
fun AuditButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
