package com.kuzyamond.voidauditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class BackupEntry(val packageName: String, var apkPath: String = "", var status: String = "PENDING")
data class RestoreEntry(val fileName: String, val filePath: String, var status: String = "PENDING")

@Composable
fun BackupScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    var mode by remember { mutableStateOf("BACKUP") }
    var packages by remember { mutableStateOf<List<BackupEntry>>(emptyList()) }
    var restoreFiles by remember { mutableStateOf<List<RestoreEntry>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }

    fun loadBackupPackages() {
        scope.launch {
            isLoading = true
            GlobalLog.log("LOADING_USER_PACKAGES...", "warn", "BACKUP")
            val res = ShizukuManager.executeCommand("pm list packages -3")
            val list = res.getOrNull()?.split("\n")
                ?.filter { it.startsWith("package:") }
                ?.map { BackupEntry(it.removePrefix("package:").trim()) }
                ?.sortedBy { it.packageName } ?: emptyList()
            packages = list
            GlobalLog.log("FOUND ${packages.size} USER_PACKAGES", "ok", "BACKUP")
            isLoading = false
        }
    }

    fun loadRestoreFiles() {
        scope.launch {
            isLoading = true
            GlobalLog.log("SCANNING_BACKUP_DIR...", "warn", "RESTORE")
            val dirs = listOf("/sdcard/Download/ADB_Backups/", "/sdcard/Download/")
            var files = mutableListOf<RestoreEntry>()
            dirs.forEach { dir ->
                val res = ShizukuManager.executeCommand("ls \"$dir\"*.apk 2>/dev/null")
                res.getOrNull()?.split("\n")?.filter { it.endsWith(".apk") }?.forEach { path ->
                    val name = path.substringAfterLast("/")
                    if (files.none { it.fileName == name }) {
                        files.add(RestoreEntry(name, path, "PENDING"))
                    }
                }
            }
            restoreFiles = files.sortedBy { it.fileName }
            GlobalLog.log("FOUND ${restoreFiles.size} BACKUP_FILES", "ok", "RESTORE")
            isLoading = false
        }
    }

    fun restoreApk(entry: RestoreEntry) {
        scope.launch {
            entry.status = "INSTALLING"
            restoreFiles = restoreFiles.toList()
            GlobalLog.log("INSTALLING: ${entry.fileName}", "warn", "RESTORE")
            val res = ShizukuManager.executeCommand("pm install -r \"${entry.filePath}\" && echo \"OK\"")
            if (res.isSuccess && res.getOrNull()?.contains("OK") == true) {
                entry.status = "DONE"
                GlobalLog.log("INSTALL_OK: ${entry.fileName}", "ok", "RESTORE")
            } else {
                entry.status = "FAIL"
                GlobalLog.log("INSTALL_FAILED: ${entry.fileName} — ${res.exceptionOrNull()?.message}", "crit", "RESTORE")
            }
            restoreFiles = restoreFiles.toList()
        }
    }

    fun backupPackage(entry: BackupEntry) {
        scope.launch {
            GlobalLog.log("BACKING_UP: ${entry.packageName}", "warn", "BACKUP")
            val pathRes = ShizukuManager.executeCommand("pm path ${entry.packageName}")
            if (pathRes.isFailure) {
                entry.status = "NO_PATH"
                GlobalLog.log("NO_PATH: ${entry.packageName}", "crit", "BACKUP")
                return@launch
            }
            val apkPath = pathRes.getOrNull()?.let {
                val lines = it.split("\n").filter { l -> l.startsWith("package:") }
                if (lines.isNotEmpty()) lines[0].removePrefix("package:").trim() else null
            } ?: run {
                entry.status = "NO_PATH"
                return@launch
            }
            entry.apkPath = apkPath
            val fileName = "${entry.packageName}_${System.currentTimeMillis()}.apk"
            val target = "/sdcard/Download/$fileName"
            val cpRes = ShizukuManager.executeCommand("cp \"$apkPath\" \"$target\" && echo \"OK\"")
            if (cpRes.isSuccess && cpRes.getOrNull()?.contains("OK") == true) {
                entry.status = "DONE"
                GlobalLog.log("SAVED: $target", "ok", "BACKUP")
            } else {
                entry.status = "FAIL"
                GlobalLog.log("CP_FAILED: ${entry.packageName} — ${cpRes.exceptionOrNull()?.message}", "crit", "BACKUP")
            }
            packages = packages.toList()
        }
    }

    fun backupAll() {
        scope.launch {
            isBackingUp = true
            GlobalLog.log("BACKUP_ALL_STARTED...", "warn", "BACKUP")
            val dirCheck = ShizukuManager.executeCommand("mkdir -p /sdcard/Download/ADB_Backups")
            dirCheck.onFailure { GlobalLog.log("DIR_ERR: ${it.message}", "crit", "BACKUP") }
            packages.forEach { entry ->
                if (entry.status != "DONE") {
                    val pathRes = ShizukuManager.executeCommand("pm path ${entry.packageName}")
                    val apkPath = pathRes.getOrNull()?.let {
                        val lines = it.split("\n").filter { l -> l.startsWith("package:") }
                        if (lines.isNotEmpty()) lines[0].removePrefix("package:").trim() else null
                    }
                    if (apkPath != null && apkPath.isNotBlank()) {
                        entry.apkPath = apkPath
                        val target = "/sdcard/Download/ADB_Backups/${entry.packageName}.apk"
                        val cp = ShizukuManager.executeCommand("cp \"$apkPath\" \"$target\" && echo \"OK\"")
                        entry.status = if (cp.isSuccess && cp.getOrNull()?.contains("OK") == true) "DONE" else "FAIL"
                    } else {
                        entry.status = "NO_PATH"
                    }
                }
                packages = packages.toList()
            }
            GlobalLog.log("BACKUP_ALL_COMPLETE!", "ok", "BACKUP")
            isBackingUp = false
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Mode toggle
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { mode = "BACKUP"; loadBackupPackages() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (mode == "BACKUP") CyberInfo else CyberSurface,
                    contentColor = if (mode == "BACKUP") CyberBackground else CyberInfo
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
            ) { Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("BACKUP", fontWeight = FontWeight.Bold, fontSize = 10.sp) }
            Button(
                onClick = { mode = "RESTORE"; loadRestoreFiles() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (mode == "RESTORE") CyberAccent2 else CyberSurface,
                    contentColor = if (mode == "RESTORE") CyberBackground else CyberAccent2
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
            ) { Icon(Icons.Default.Upload, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("RESTORE", fontWeight = FontWeight.Bold, fontSize = 10.sp) }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("FILTER...", color = Color.Gray, fontSize = 11.sp) },
                textStyle = TextStyle(color = CyberInfo, fontSize = 11.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberInfo,
                    unfocusedBorderColor = CyberBorder,
                    focusedContainerColor = CyberBackground,
                    unfocusedContainerColor = CyberBackground
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { if (mode == "BACKUP") loadBackupPackages() else loadRestoreFiles() },
                modifier = Modifier.size(48.dp).border(1.dp, CyberInfo).background(CyberSurface)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CyberInfo, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, null, tint = CyberInfo)
                }
            }
        }

        if (mode == "BACKUP") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { backupAll() },
                    modifier = Modifier.weight(1f),
                    enabled = packages.isNotEmpty() && !isBackingUp,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberAccent2, contentColor = CyberBackground),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                ) {
                    if (isBackingUp) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = CyberBackground, strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("BACKUP_ALL", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
                val doneCount = packages.count { it.status == "DONE" }
                Text("$doneCount/${packages.size}", color = CyberInfo, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterVertically))
            }

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val filtered = packages.filter { it.packageName.contains(filter, true) }
                items(filtered) { entry ->
                    val statusColor = when (entry.status) {
                        "DONE" -> CyberAccent2
                        "FAIL" -> CyberWarning
                        "NO_PATH" -> CyberAccent2
                        else -> Color(0xFF64748B)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().border(1.dp, CyberBorder).background(CyberBackground).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.packageName, color = Color(0xFFCBD5E1), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("STATUS: ${entry.status}", color = statusColor, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                        if (entry.status != "DONE") {
                            IconButton(
                                onClick = { backupPackage(entry) },
                                modifier = Modifier.size(36.dp).border(1.dp, statusColor)
                            ) {
                                Icon(Icons.Default.Download, null, tint = statusColor, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Text("OK", color = CyberAccent2, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val filtered = restoreFiles.filter { it.fileName.contains(filter, true) }
                if (filtered.isEmpty() && !isLoading) {
                    item {
                        Text("NO_BACKUP_FILES_FOUND", color = Color.Gray, fontSize = 10.sp,
                            modifier = Modifier.padding(16.dp))
                    }
                }
                items(filtered) { entry ->
                    val statusColor = when (entry.status) {
                        "DONE" -> CyberAccent2
                        "FAIL" -> CyberWarning
                        "INSTALLING" -> CyberAccent2
                        else -> CyberInfo
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().border(1.dp, CyberBorder).background(CyberBackground).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.fileName, color = Color(0xFFCBD5E1), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(entry.filePath, color = Color(0xFF64748B), fontSize = 7.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                            Text("STATUS: ${entry.status}", color = statusColor, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                        if (entry.status == "PENDING" || entry.status == "FAIL") {
                            Button(
                                onClick = { restoreApk(entry) },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberAccent2.copy(alpha = 0.2f), contentColor = CyberAccent2),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CyberAccent2)
                            ) { Text("INSTALL", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
                        } else if (entry.status == "INSTALLING") {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyberAccent2, strokeWidth = 2.dp)
                        } else {
                            Text("OK", color = CyberAccent2, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}
