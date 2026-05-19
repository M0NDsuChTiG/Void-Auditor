package com.kuzyamond.adbstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun FilesScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    var filePath by remember { mutableStateOf("/sdcard/") }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        CyberCard(title = "LOCAL_FS_CONTROL", color = CyberAccent2) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = filePath,
                        onValueChange = { filePath = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = CyberAccent2, fontSize = 11.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberAccent2,
                            unfocusedBorderColor = Color(0xFF78350F),
                            focusedContainerColor = CyberBackground,
                            unfocusedContainerColor = CyberBackground
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            scope.launch {
                                GlobalLog.log("CMD: ls -l \"$filePath\"", "warn", "FS")
                                val res = ShizukuManager.executeCommand("ls -l \"$filePath\"")
                                res.onSuccess { 
                                    val out = if (it.isEmpty()) "DIRECTORY_EMPTY_OR_NO_PERM" else it
                                    GlobalLog.log(out, "ok", "FS") 
                                }
                                .onFailure { GlobalLog.log("FS_ERR: ${it.message}", "crit", "FS") }
                            }
                        },
                        modifier = Modifier.border(1.dp, CyberAccent2).background(CyberSurface)
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = CyberAccent2)
                    }
                }

                // Quick Paths
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PathChip("ROOT (/)") { filePath = "/" }
                    PathChip("SDCARD") { filePath = "/sdcard/" }
                    PathChip("DOWNLOADS") { filePath = "/sdcard/Download/" }
                    PathChip("TMP") { filePath = "/data/local/tmp/" }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                GlobalLog.log("CMD: du -sh \"$filePath\"", "warn", "FS")
                                val res = ShizukuManager.executeCommand("du -sh \"$filePath\"")
                                res.onSuccess { GlobalLog.log("SIZE_REPORT: $it", "ok", "FS") }
                                   .onFailure { GlobalLog.log("FS_ERR: ${it.message}", "crit", "FS") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberAccent2, contentColor = CyberBackground),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                    ) {
                        Text("CALCULATE_STORAGE_USE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                GlobalLog.log("CMD: cat \"$filePath\"", "warn", "FS")
                                val res = ShizukuManager.executeCommand("cat \"$filePath\"")
                                res.onSuccess { 
                                    if (it.isEmpty()) {
                                        GlobalLog.log("FILE_EMPTY_OR_NOT_READABLE", "warn", "FS")
                                    } else {
                                        val preview = it.take(1000) + (if (it.length > 1000) "... [TRUNCATED]" else "")
                                        GlobalLog.log("CONTENT_PREVIEW:\n$preview", "ok", "FS")
                                    }
                                }
                                .onFailure { GlobalLog.log("FS_ERR: ${it.message}", "crit", "FS") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberInfo, contentColor = CyberBackground),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                    ) {
                        Text("READ_FILE_CONTENT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        CyberCard(title = "FS_DIAGNOSTICS", color = Color.Gray) {
            Text(
                "Если список файлов пуст, попробуйте путь /sdcard/ или /system/. Некоторые системные папки требуют Root, даже с Shizuku.",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun RowScope.PathChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.weight(1f).height(24.dp).border(1.dp, CyberBorder).background(CyberSurface).cyberClickable(onClick),
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color(0xFF64748B), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}
