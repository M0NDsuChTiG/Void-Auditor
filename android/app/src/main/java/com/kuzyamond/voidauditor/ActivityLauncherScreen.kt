package com.kuzyamond.voidauditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
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

@Composable
fun ActivityLauncherScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    var packages by remember { mutableStateOf<List<String>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var activities by remember { mutableStateOf<List<String>>(emptyList()) }
    var isActivitiesLoading by remember { mutableStateOf(false) }

    fun loadPackages() {
        scope.launch {
            isLoading = true
            GlobalLog.log("LOADING_PACKAGES_FOR_ACTIVITIES...", "warn", "ACTIVITY")
            val res = ShizukuManager.executeCommand("pm list packages -3")
            val list = res.getOrNull()?.split("\n")
                ?.filter { it.startsWith("package:") }
                ?.map { it.removePrefix("package:").trim() }
                ?.sorted() ?: emptyList()
            packages = list
            GlobalLog.log("FOUND ${packages.size} USER_PACKAGES", "ok", "ACTIVITY")
            isLoading = false
        }
    }

    fun loadActivities(pkg: String) {
        scope.launch {
            isActivitiesLoading = true
            selectedPackage = pkg
            activities = emptyList()
            GlobalLog.log("FETCHING_ACTIVITIES_FOR: $pkg", "warn", "ACTIVITY")
            val res = ShizukuManager.executeCommand("dumpsys package $pkg | grep -oE '$pkg/[A-Za-z0-9_.$]+' | sort -u | head -80")
            val list = res.getOrNull()?.split("\n")
                ?.filter { it.isNotBlank() }
                ?.map { it.removePrefix("$pkg/") }
                ?.filter { it.isNotBlank() } ?: emptyList()
            if (list.isEmpty()) {
                GlobalLog.log("NO_ACTIVITIES_FOUND (try user-installed packages)", "warn", "ACTIVITY")
            } else {
                GlobalLog.log("FOUND ${list.size} COMPONENTS", "ok", "ACTIVITY")
            }
            activities = list
            isActivitiesLoading = false
        }
    }

    fun launchActivity(pkg: String, activity: String) {
        scope.launch {
            GlobalLog.log("LAUNCHING: $pkg/$activity", "warn", "ACTIVITY")
            val res = ShizukuManager.executeCommand("am start -n $pkg/$activity")
            res.onSuccess { GlobalLog.log("LAUNCH_OK: $it", "ok", "ACTIVITY") }
               .onFailure { GlobalLog.log("LAUNCH_FAILED: ${it.message}", "crit", "ACTIVITY") }
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("FILTER_PACKAGES...", color = Color.Gray, fontSize = 11.sp) },
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
                onClick = { loadPackages() },
                modifier = Modifier.size(48.dp).border(1.dp, CyberInfo).background(CyberSurface)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CyberInfo, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, null, tint = CyberInfo)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Package list
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, CyberBorder)
            ) {
                val filtered = packages.filter { it.contains(filter, true) }
                item {
                    Text("PACKAGES [${filtered.size}]", color = CyberInfo, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp))
                }
                items(filtered) { pkg ->
                    val isSelected = selectedPackage == pkg
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) CyberSurface else CyberBackground)
                            .clickable { loadActivities(pkg) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSelected) ">" else " ",
                            color = CyberInfo,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = pkg,
                            color = if (isSelected) CyberInfo else Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            // Activity list
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, CyberBorder)
            ) {
                item {
                    Text("ACTIVITIES", color = CyberAccent2, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, modifier = Modifier.padding(8.dp))
                }
                if (selectedPackage == null) {
                    item {
                        Text("SELECT_PACKAGE", color = Color.Gray, fontSize = 9.sp,
                            modifier = Modifier.padding(8.dp))
                    }
                } else if (isActivitiesLoading) {
                    item {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(16.dp).size(18.dp),
                            color = CyberAccent2, strokeWidth = 2.dp
                        )
                    }
                } else if (activities.isEmpty()) {
                    item {
                        Text("NO_COMPONENTS", color = Color.Gray, fontSize = 9.sp,
                            modifier = Modifier.padding(8.dp))
                    }
                } else {
                    items(activities) { activity ->
                        val shortName = if (activity.length > 45) activity.takeLast(45) else activity
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { launchActivity(selectedPackage!!, activity) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = CyberAccent2, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = shortName,
                                color = Color(0xFFCBD5E1),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
