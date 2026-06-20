package com.kuzyamond.voidauditor

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.kuzyamond.voidauditor.core.ShizukuExecutor

private val D = '$'

private fun sh(raw: String): String = raw.replace("{D}", "$D")

data class ScriptPreset(val label: String, val content: String, val category: String)

@Composable
fun ScriptsScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("void_auditor_scripts", Context.MODE_PRIVATE) }

    var scriptContent by remember { mutableStateOf(prefs.getString("last_script", "#!/bin/bash\necho \"WAITING_FOR_SCRIPT...\"") ?: "") }
    var scriptType by remember { mutableStateOf(prefs.getString("last_type", "BASH") ?: "BASH") }

    val presets = remember {
        listOf(
            ScriptPreset("FULL_SECURITY_AUDIT_V3", sh("""#!/bin/bash
echo "==========================================="
echo "   FULL ANDROID SECURITY AUDIT V3"
echo "   Date: {D}(date)"
echo "==========================================="
echo "[1] DEVICE INFO"
getprop ro.product.model
getprop ro.build.version.release
getprop ro.product.manufacturer
echo ""
echo "[2] TOTAL PACKAGES: {D}(pm list packages | wc -l)"
echo ""
echo "[3] UNKNOWN SOURCES"
settings get secure install_non_market_apps
echo ""
echo "[4] ACCESSIBILITY SERVICES"
settings get secure enabled_accessibility_services
echo ""
echo "[5] CRITICAL PERMISSIONS"
for perm in READ_CONTACTS READ_SMS RECORD_AUDIO CAMERA; do
  echo "--- {D}perm ---"
  dumpsys package | grep -E "Package \[|android.permission.{D}perm: granted=true" | grep -B 1 "granted=true" | grep "Package \[" | sed 's/Package \[//;s/\]//' | head -n 5
done
echo ""
echo "[6] TOP PROCESSES"
top -n 1 -b -m 10 | head -n 15
echo ""
echo "[7] ESTABLISHED CONNECTIONS"
netstat -anp | grep ESTABLISHED | head -n 10
echo ""
echo "[8] DEVICE ADMINISTRATORS"
dumpsys device_policy | grep "admin="
echo ""
echo "[9] DEVELOPER SETTINGS"
settings get global development_settings_enabled
settings get global adb_enabled
echo "==========================================="
echo "   AUDIT COMPLETE"
echo "==========================================="""), "AUDIT"),

            ScriptPreset("DEEP_APP_AUDIT_V2", sh("""#!/bin/bash
echo "==========================================="
echo "   DEEP FULL APP AUDIT V2"
echo "   Date: {D}(date)"
echo "==========================================="
echo "[1] DEVICE"
getprop ro.build.version.release
getprop ro.build.version.sdk
echo "Total: {D}(pm list packages | wc -l)"
echo ""
echo "[2] GALLERY/MEDIA APPS"
pm list packages | grep -E "gallery|photo|image|media|pict"
echo ""
echo "[3] DANGEROUS PERMISSIONS"
for perm in READ_CONTACTS WRITE_CONTACTS READ_SMS RECORD_AUDIO CAMERA ACCESS_FINE_LOCATION READ_PHONE_STATE SYSTEM_ALERT_WINDOW; do
  echo "--- {D}perm ---"
  dumpsys package | grep -E "android.permission.{D}perm" -B 8 | grep "Package"
done
echo ""
echo "[4] BOOT COMPLETED"
dumpsys package | grep -E "BOOT_COMPLETED|QUICKBOOT_POWERON" -B 6
echo ""
echo "[5] OVERLAY PERMISSIONS"
dumpsys package | grep -E "android.permission.SYSTEM_ALERT_WINDOW" -B 10 | grep "Package"
echo ""
echo "[6] ACCESSIBILITY"
settings get secure enabled_accessibility_services
echo ""
echo "[7] SIDELOADED APPS"
pm list packages -f | grep "/data/app/"
echo ""
echo "[8] TOP PROCESSES"
top -n 1 -b -m 25 | head -n 35
echo ""
echo "[9] NETWORK"
netstat -anp | grep ESTABLISHED
echo "==========================================="
echo "   AUDIT COMPLETE"
echo "==========================================="""), "AUDIT"),

            ScriptPreset("CLEANER_ANALYZER", sh("""#!/bin/bash
echo "==========================================="
echo "   ANDROID CLEANER ANALYZER"
echo "   Date: {D}(date)"
echo "   ONLY ANALYSIS - NOTHING DELETED"
echo "==========================================="
echo "[1] SIDELOADED APPS"
pm list packages -f | grep "/data/app/" | cut -d= -f2 | sort
echo ""
echo "[2] DANGEROUS PERMS CHECK"
for perm in READ_CONTACTS READ_SMS RECORD_AUDIO CAMERA SYSTEM_ALERT_WINDOW READ_PHONE_STATE; do
  echo "--- {D}perm ---"
  dumpsys package | grep -E "android.permission.{D}perm" -B 8 | grep "Package"
done
echo ""
echo "[3] ACCESSIBILITY (HIGH RISK)"
settings get secure enabled_accessibility_services
echo ""
echo "[4] DRAW OVERLAY"
dumpsys package | grep -E "android.permission.SYSTEM_ALERT_WINDOW" -B 10 | grep "Package"
echo ""
echo "[5] BOOT_AUTOSTART"
dumpsys package | grep -E "BOOT_COMPLETED" -B 6
echo ""
echo "[6] RECOMMENDATIONS"
echo "  HIGH: AppManager (Accessibility ON)"
echo "  HIGH: SAI (APK installer)"
echo "  CHECK: VirusTotal, Downgrader, FakeTraveler, Toolbox"
echo "==========================================="
echo "   ANALYSIS COMPLETE"
echo "==========================================="""), "AUDIT"),

            ScriptPreset("ANDROID_LOCKDOWN", sh("""#!/bin/bash
echo "==========================================="
echo "   ANDROID LOCKDOWN"
echo "==========================================="
PKG="io.github.muntashirakon.AppManager"
SVC="{D}PKG/io.github.muntashirakon.AppManager.accessibility.NoRootAccessibilityService"
CURRENT={D}(settings get secure enabled_accessibility_services)
echo "[STATUS]"
echo "Unknown sources: {D}(settings get secure install_non_market_apps)"
echo "Accessibility: {D}(echo {D}CURRENT | grep -q {D}SVC && echo ENABLED || echo DISABLED)"
echo ""
echo "[1] Disable Accessibility (App Manager)"
settings put secure enabled_accessibility_services ""
echo "  -> Done"
echo ""
echo "[2] Block unknown sources"
settings put secure install_non_market_apps 0
echo "  -> Done"
echo ""
echo "[3] Revoke dangerous perms from App Manager"
pm revoke {D}PKG android.permission.READ_CONTACTS 2>/dev/null || true
pm revoke {D}PKG android.permission.READ_SMS 2>/dev/null || true
pm revoke {D}PKG android.permission.RECORD_AUDIO 2>/dev/null || true
pm revoke {D}PKG android.permission.CAMERA 2>/dev/null || true
pm revoke {D}PKG android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
echo "  -> Done"
echo ""
echo "[4] Enable Accessibility (App Manager)"
settings put secure enabled_accessibility_services "{D}SVC"
echo "  -> Done"
echo "==========================================="
echo "   LOCKDOWN COMPLETE"
echo "==========================================="""), "LOCKDOWN"),

            ScriptPreset("ULTIMATE_CONTROL_V4", sh("""#!/bin/bash
echo "==========================================="
echo "   ULTIMATE CONTROL V4 - STORAGE ANALYSIS"
echo "==========================================="
echo "[STORAGE USAGE]"
echo "--- /sdcard/Android/data ---"
du -sh /sdcard/Android/data/* 2>/dev/null | sort -rh | head -n 15
echo ""
echo "--- /sdcard/Android/obb ---"
du -sh /sdcard/Android/obb/* 2>/dev/null | sort -rh | head -n 10
echo ""
echo "[TOP PROCESSES]"
top -n 1 -b -m 15 | head -n 20
echo ""
echo "[ALL PACKAGES]"
pm list packages -s | sort
echo ""
echo "[DISABLED PACKAGES]"
pm list packages -d
echo ""
echo "[SUSPICIOUS PACKAGES CHECK]"
for pkg in com.funnycat.virustotal tech.lolli.toolbox cl.coders.faketraveler com.garyodernichts.downgrader; do
  pm list packages 2>/dev/null | grep -q "{D}pkg" && echo "  FOUND: {D}pkg" || echo "  clean: {D}pkg"
done
echo "==========================================="""), "CONTROL"),

            ScriptPreset("CLEAN_ALL_CACHE", sh("""#!/bin/bash
echo "Cleaning all app caches..."
for pkg in {D}(pm list packages | cut -d: -f2); do
  pm clear --cache-only "{D}pkg" 2>/dev/null || true
done
echo "Cache cleanup complete"""), "CLEANUP"),

            ScriptPreset("FREEZE_BLOATWARE", sh("""#!/bin/bash
BLOAT=(
  com.funnycat.virustotal
  tech.lolli.toolbox
  cl.coders.faketraveler
  com.garyodernichts.downgrader
  com.src.android.app.camera.sticker
  com.sec.android.app.DataCreate
)
echo "Freezing bloatware/suspicious apps..."
for pkg in "{D}BLOAT[@]"; do
  if pm list packages | grep -q "{D}pkg"; then
    pm disable-user --user 0 "{D}pkg" && echo "  FROZEN: {D}pkg" || echo "  FAIL: {D}pkg"
  else
    echo "  not found: {D}pkg"
  fi
done
echo "Done"""), "CLEANUP"),

            ScriptPreset("NETWORK_SCAN", sh("""#!/bin/bash
echo "==========================================="
echo "   NETWORK SCAN"
echo "==========================================="
echo "[LISTENING PORTS]"
netstat -anp | grep LISTEN | head -n 20
echo ""
echo "[ESTABLISHED CONNECTIONS]"
netstat -anp | grep ESTABLISHED | head -n 20
echo ""
echo "[ADB STATUS]"
settings get global adb_wifi_enabled
settings get global adb_authorization_timeout
echo ""
echo "[WIFI INFO]"
dumpsys wifi | grep -E "mNetworkInfo|mWifiInfo|SSID" | head -n 5
echo ""
echo "[BLUETOOTH SCAN]"
appops query-op BLUETOOTH_SCAN allow 2>/dev/null || echo "BLUETOOTH_SCAN: check blocked"
echo "==========================================="""), "NETWORK"),
        )
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CyberCard(title = "SCRIPT_EXECUTOR", color = CyberAccent) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { scriptType = "BASH" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (scriptType == "BASH") CyberAccent else CyberSurface,
                            contentColor = if (scriptType == "BASH") CyberBackground else CyberAccent
                        ),
                        shape = RoundedCornerShape(2.dp)
                    ) { Text("BASH", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { scriptType = "PYTHON" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (scriptType == "PYTHON") CyberAccent else CyberSurface,
                            contentColor = if (scriptType == "PYTHON") CyberBackground else CyberAccent
                        ),
                        shape = RoundedCornerShape(2.dp)
                    ) { Text("PYTHON", fontWeight = FontWeight.Bold) }
                }

                OutlinedTextField(
                    value = scriptContent,
                    onValueChange = { scriptContent = it },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberAccent, unfocusedBorderColor = CyberBorder,
                        focusedContainerColor = CyberBackground, unfocusedContainerColor = CyberBackground
                    )
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                GlobalLog.log("RUNNING_${scriptType}_SCRIPT...", "warn", "SCRIPTS")
                                val cmd = if (scriptType == "BASH") "sh -c \"${scriptContent.replace("\"", "\\\"")}\""
                                else "python3 -c \"${scriptContent.replace("\"", "\\\"")}\""
                                val res = ShizukuExecutor.executeCommand(cmd)
                                if (res.isSuccessful) GlobalLog.log("OUTPUT:\n${res.output}", "ok", "SCRIPTS")
                                else GlobalLog.log("SCRIPT_ERR: ${res.error}", "crit", "SCRIPTS")
                            }
                        },
                        modifier = Modifier.weight(2f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberAccent, contentColor = CyberBackground),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("EXECUTE", fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = {
                            prefs.edit().putString("last_script", scriptContent).putString("last_type", scriptType).apply()
                            GlobalLog.log("SCRIPT_SAVED", "ok", "SCRIPTS")
                        },
                        modifier = Modifier.size(44.dp).border(1.dp, CyberAccent).background(CyberSurface)
                    ) { Icon(Icons.Default.Save, null, tint = CyberAccent) }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val ext = if (scriptType == "BASH") "sh" else "py"
                                val path = "/sdcard/Download/script_${System.currentTimeMillis()}.$ext"
                                val res = ShizukuExecutor.executeCommand("echo '${scriptContent.replace("'", "'\\''")}' > \"$path\"")
                                if (res.isSuccessful) GlobalLog.log("EXPORTED: $path", "ok", "SCRIPTS")
                                else GlobalLog.log("EXPORT_ERR: ${res.error}", "crit", "SCRIPTS")
                            }
                        },
                        modifier = Modifier.size(44.dp).border(1.dp, CyberInfo).background(CyberSurface)
                    ) { Icon(Icons.Default.Share, null, tint = CyberInfo) }
                }
            }
        }

        val categories = presets.groupBy { it.category }
        val categoryOrder = listOf("AUDIT", "LOCKDOWN", "CONTROL", "CLEANUP", "NETWORK")
        val categoryColors = mapOf(
            "AUDIT" to CyberAccent2,
            "LOCKDOWN" to CyberWarning,
            "CONTROL" to CyberInfo,
            "CLEANUP" to CyberAccent2,
            "NETWORK" to CyberAccent
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categoryOrder.forEach { cat ->
                val items = categories[cat] ?: return@forEach
                item {
                    Text("> ${cat}_SCRIPTS", color = categoryColors[cat] ?: CyberInfo,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
                items(items) { preset ->
                    QuickScriptButton(preset.label, color = categoryColors[cat] ?: CyberInfo) {
                        scriptType = "BASH"
                        scriptContent = preset.content
                    }
                }
            }
        }
    }
}

@Composable
fun QuickScriptButton(label: String, color: Color = CyberInfo, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
