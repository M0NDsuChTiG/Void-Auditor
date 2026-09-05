package com.kuzyamond.voidauditor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import com.kuzyamond.voidauditor.network.NetworkAdb
import com.kuzyamond.voidauditor.network.NetworkDashboardScreen
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("CONNECT", "NET_SCAN")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = CyberBackground,
            contentColor = CyberInfo
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                    selectedContentColor = CyberInfo,
                    unselectedContentColor = Color.Gray
                )
            }
        }

        when (tabIndex) {
            0 -> ConnectTabContent(scope)
            1 -> NetworkDashboardScreen()
        }
    }
}

@Composable
private fun ConnectTabContent(scope: kotlinx.coroutines.CoroutineScope) {
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(5555) }
    var wifiAdbStatus by remember { mutableStateOf("UNKNOWN") }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        wifiAdbStatus = if (NetworkAdb.isWifiAdbEnabled()) "ENABLED" else "DISABLED"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        CyberCard(title = "REMOTE_CONNECT", color = CyberInfo) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("WIRELESS_DEBUGGING", color = CyberInfo, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "STATUS: $wifiAdbStatus",
                            color = if (wifiAdbStatus == "ENABLED") Color(0xFF4ADE80) else CyberWarning,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "[CHECK]",
                            color = CyberInfo,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .cyberClickable {
                                    scope.launch {
                                        GlobalLog.log("CHECKING_WIRELESS_DEBUGGING...", "warn", "CONN")
                                        val enabled = NetworkAdb.isWifiAdbEnabled()
                                        wifiAdbStatus = if (enabled) "ENABLED" else "DISABLED"
                                        GlobalLog.log("WIRELESS_DEBUG_STATUS: $wifiAdbStatus", "ok", "CONN")
                                    }
                                }
                        )
                        Text(
                            "[COPY]",
                            color = CyberInfo,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .cyberClickable {
                                    val cmd = "adb connect $ipAddress:$port"
                                    clipboardManager.setText(AnnotatedString(cmd))
                                    GlobalLog.log("COPIED: $cmd", "ok", "CONN")
                                }
                        )
                    }
                }

                Text(
                    "VOID Auditor cannot enable Wireless Debugging automatically.\n" +
                    "Open Developer Options → Wireless Debugging to enable it.",
                    color = Color(0xFF94A3B8), fontSize = 10.sp, lineHeight = 14.sp
                )

                Button(
                    onClick = {
                        try {
                            context.startActivity(NetworkAdb.openDeveloperSettingsIntent())
                            GlobalLog.log("OPENED_DEVELOPER_SETTINGS", "ok", "CONN")
                        } catch (e: Exception) {
                            GlobalLog.log("OPEN_SETTINGS_FAILED: ${e.message}", "crit", "CONN")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberInfo,
                        contentColor = CyberBackground
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                ) {
                    Text("OPEN DEVELOPER OPTIONS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        modifier = Modifier.weight(2f),
                        placeholder = { Text("DEVICE_IP", color = Color.Gray, fontSize = 11.sp) },
                        textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberInfo,
                            unfocusedBorderColor = CyberBorder
                        )
                    )
                    OutlinedTextField(
                        value = port.toString(),
                        onValueChange = { port = it.toIntOrNull() ?: 5555 },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("PORT", color = Color.Gray, fontSize = 11.sp) },
                        textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberInfo,
                            unfocusedBorderColor = CyberBorder
                        )
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            GlobalLog.log("CONNECTING_TO: $ipAddress:$port", "warn", "CONN")
                            val result = CapabilityExecutor.execute(Capability.AdbConnect(ipAddress, port))
                            if (result.isSuccessful) {
                                GlobalLog.log("LINK_RESULT: ${result.output}", "ok", "CONN")
                            } else {
                                GlobalLog.log("LINK_FAILED: ${result.error}", "crit", "CONN")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberInfo, contentColor = CyberBackground),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                ) {
                    Icon(Icons.Default.Wifi, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("CONNECT_WIFI", fontWeight = FontWeight.Bold)
                }
            }
        }

        CyberCard(title = "USB_DEBUG", color = CyberAccent2) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DETECT_LOCAL_DEVICES_VIA_USB", color = Color(0xFF94A3B8), fontSize = 10.sp)

                Button(
                    onClick = {
                        scope.launch {
                            GlobalLog.log("SCANNING_USB_DEVICES...", "warn", "CONN")
                            val result = CapabilityExecutor.execute(Capability.AdbScanDevices)
                            if (result.isSuccessful) {
                                GlobalLog.log("DEVICES:\n${result.output}", "ok", "CONN")
                            } else {
                                GlobalLog.log("USB_SCAN_ERR: ${result.error}", "crit", "CONN")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberAccent2, contentColor = CyberBackground),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                ) {
                    Icon(Icons.Default.Usb, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SCAN_USB", fontWeight = FontWeight.Bold)
                }
            }
        }

        CyberCard(title = "CONNECTION_HELP", color = Color.Gray) {
            Text(
                "Убедитесь, что на удаленном устройстве включена 'Отладка по WiFi' (Wireless Debugging). Для USB используйте OTG-кабель.",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}
