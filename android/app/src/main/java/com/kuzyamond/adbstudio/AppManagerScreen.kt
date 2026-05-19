package com.kuzyamond.adbstudio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch

enum class AppStatus { WORKING, DISABLED, SLEEPING }
data class AppInfo(val packageName: String, val status: AppStatus)

@Composable
fun AppManagerScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    var packages by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val selectedPackages = remember { mutableStateMapOf<String, Boolean>() }
    
    val expandedSections = remember { mutableStateMapOf(
        "RUNNING" to true,
        "FROZEN" to false,
        "DISABLED" to false
    )}

    val hasSelection = selectedPackages.any { it.value }

    val batchActions = listOf("STOP" to CyberAccent2, "OFF" to CyberWarning, "ON" to CyberAccent2, "DEL" to Color.White)

    val refresh: () -> Unit = {
        scope.launch {
            isLoading = true
            selectedPackages.clear()
            GlobalLog.log("SCANNING_LOCAL_PACKAGES...", "warn", "APPS")
            
            val enabledRes = ShizukuManager.executeCommand("pm list packages -e")
            val disabledRes = ShizukuManager.executeCommand("pm list packages -d")
            
            if (enabledRes.isFailure) {
                GlobalLog.log("ENABLED_SYNC_ERR: ${enabledRes.exceptionOrNull()?.message}", "crit", "APPS")
            }
            if (disabledRes.isFailure) {
                GlobalLog.log("DISABLED_SYNC_ERR: ${disabledRes.exceptionOrNull()?.message}", "crit", "APPS")
            }

            val enabledList = enabledRes.getOrNull()?.split("\n")
                ?.filter { it.startsWith("package:") }
                ?.map { AppInfo(it.removePrefix("package:").trim(), AppStatus.WORKING) } ?: emptyList()
                
            val disabledList = disabledRes.getOrNull()?.split("\n")
                ?.filter { it.startsWith("package:") }
                ?.map { AppInfo(it.removePrefix("package:").trim(), AppStatus.DISABLED) } ?: emptyList()
            
            packages = (enabledList + disabledList).sortedBy { it.packageName }
            GlobalLog.log("SYNC_COMPLETE: FOUND ${packages.size} PACKAGES", if (packages.isNotEmpty()) "ok" else "warn", "APPS")
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Refresh Row
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
                onClick = { refresh() }, 
                modifier = Modifier.size(54.dp).border(1.dp, CyberInfo).background(CyberSurface)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyberInfo, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, null, tint = CyberInfo)
                }
            }
        }

        // Batch Action Bar
        if (hasSelection) {
            val selectedCount = selectedPackages.count { it.value }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).border(1.dp, CyberInfo).background(CyberSurface.copy(alpha = 0.8f)).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("BATCH [$selectedCount]", color = CyberInfo, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    batchActions.forEach { (label, color) ->
                        AppActionButton(label, color, Modifier.width(48.dp)) {
                            scope.launch {
                                val targets = selectedPackages.filter { it.value }.keys.toList()
                                GlobalLog.log("BATCH_${label}_${targets.size}_APPS...", "warn", "APPS")
                                targets.forEach { pkg ->
                                    val cmd = when(label) {
                                        "STOP" -> "am force-stop $pkg"
                                        "OFF" -> "pm disable-user --user 0 $pkg"
                                        "ON" -> "pm enable $pkg"
                                        "DEL" -> "pm uninstall --user 0 $pkg"
                                        else -> ""
                                    }
                                    val res = ShizukuManager.executeCommand(cmd)
                                    if (res.isSuccess) {
                                        GlobalLog.log("OK: $pkg", "ok", "APPS")
                                    } else {
                                        GlobalLog.log("FAIL: $pkg — ${res.exceptionOrNull()?.message}", "crit", "APPS")
                                    }
                                }
                                selectedPackages.clear()
                                refresh()
                            }
                        }
                    }
                    AppActionButton("CLR", Color.Gray, Modifier.width(40.dp)) {
                        selectedPackages.clear()
                    }
                }
            }
        }

        val grouped = mapOf(
            "RUNNING" to packages.filter { it.status == AppStatus.WORKING && it.packageName.contains(filter, true) },
            "FROZEN" to packages.filter { it.status == AppStatus.SLEEPING && it.packageName.contains(filter, true) },
            "DISABLED" to packages.filter { it.status == AppStatus.DISABLED && it.packageName.contains(filter, true) }
        )
        
        val descriptions = mapOf(
            "RUNNING" to "Active background processes and foreground services.",
            "FROZEN" to "Apps currently suspended to save battery and resources.",
            "DISABLED" to "Packages completely deactivated at the system level."
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            grouped.forEach { (groupName, list) ->
                if (list.isNotEmpty() || filter.isEmpty()) {
                    item {
                        val isExpanded = expandedSections[groupName] == true || filter.isNotEmpty()
                        val color = when (groupName) {
                            "RUNNING" -> CyberAccent2
                            "DISABLED" -> CyberWarning
                            else -> CyberAccent2
                        }
                        
                        AppSectionHeader(groupName, list.size, descriptions[groupName] ?: "", color, isExpanded) {
                            expandedSections[groupName] = !isExpanded
                        }
                    }
                    
                    if (expandedSections[groupName] == true || filter.isNotEmpty()) {
                        items(list) { pkg ->
                            AppItem(
                                pkg = pkg,
                                isSelected = selectedPackages[pkg.packageName] == true,
                                onToggle = { selectedPackages[pkg.packageName] = !(selectedPackages[pkg.packageName] == true) },
                                onAction = { action ->
                                    scope.launch {
                                        GlobalLog.log("EXEC_${action}_${pkg.packageName}...", "warn", "APPS")
                                        val cmd = when(action) {
                                            "STOP" -> "am force-stop ${pkg.packageName}"
                                            "OFF" -> "pm disable-user --user 0 ${pkg.packageName}"
                                            "ON" -> "pm enable ${pkg.packageName}"
                                            "DEL" -> "pm uninstall --user 0 ${pkg.packageName}"
                                            else -> ""
                                        }
                                        val res = ShizukuManager.executeCommand(cmd)
                                        if (res.isSuccess) {
                                            GlobalLog.log("SUCCESS", "ok", "APPS")
                                            refresh()
                                        } else {
                                            GlobalLog.log("FAILED: ${res.exceptionOrNull()?.message}", "crit", "APPS")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppSectionHeader(title: String, count: Int, description: String, color: Color, isExpanded: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, CyberBorder)
            .background(CyberSurface.copy(alpha = 0.8f))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$title [$count]",
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = color,
                modifier = Modifier.rotate(if (isExpanded) 90f else 0f)
            )
        }
        if (isExpanded) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                color = Color.Gray,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun AppItem(pkg: AppInfo, isSelected: Boolean = false, onToggle: () -> Unit = {}, onAction: (String) -> Unit) {
    val borderColor = if (isSelected) CyberInfo else CyberBorder
    Column(
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .border(1.dp, borderColor)
            .background(if (isSelected) CyberSurface else CyberBackground)
            .clickable { onToggle() }
            .padding(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isSelected) "[*] " else "[ ] ",
                color = CyberInfo,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = pkg.packageName,
                color = Color(0xFFCBD5E1),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AppActionButton("STOP", CyberAccent2, Modifier.weight(1f)) { onAction("STOP") }
            if (pkg.status == AppStatus.WORKING) {
                AppActionButton("OFF", CyberWarning, Modifier.weight(1f)) { onAction("OFF") }
            } else {
                AppActionButton("ON", CyberAccent2, Modifier.weight(1f)) { onAction("ON") }
            }
            AppActionButton("DEL", Color.White, Modifier.weight(1f)) { onAction("DEL") }
        }
    }
}

@Composable
fun AppActionButton(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.1f), contentColor = color),
        shape = RoundedCornerShape(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
