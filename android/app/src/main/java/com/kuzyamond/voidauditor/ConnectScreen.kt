package com.kuzyamond.voidauditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
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
fun ConnectScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5555") }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        CyberCard(title = "REMOTE_CONNECT", color = CyberInfo) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("WIFI_ADB_LINK", color = CyberInfo, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                
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
                        value = port,
                        onValueChange = { port = it },
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
                            val res = ShizukuManager.executeCommand("adb connect $ipAddress:$port")
                            res.onSuccess { GlobalLog.log("LINK_RESULT: $it", "ok", "CONN") }
                               .onFailure { GlobalLog.log("LINK_FAILED: ${it.message}", "crit", "CONN") }
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
                            val res = ShizukuManager.executeCommand("adb devices")
                            res.onSuccess { GlobalLog.log("DEVICES:\n$it", "ok", "CONN") }
                               .onFailure { GlobalLog.log("USB_SCAN_ERR: ${it.message}", "crit", "CONN") }
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
