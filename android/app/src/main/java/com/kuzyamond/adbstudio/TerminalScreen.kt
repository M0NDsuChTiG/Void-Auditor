package com.kuzyamond.adbstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.kuzyamond.adbstudio.core.ShizukuExecutor

@Composable
fun TerminalScreen(scope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()) {
    var commandText by remember { mutableStateOf("") }

    val commonCommands = listOf(
        "id" to "Текущий UID (проверка прав)",
        "dumpsys deviceidle whitelist +io.element.android.x" to "🚀 Добавить Element X в БЕЛЫЙ СПИСОК (Анти-заморозка)",
        "am force-stop io.element.android.x && am send-trim-memory io.element.android.x COMPLETE" to "🧹 ГЛУБОКАЯ очистка графики Element X",
        "pm disable io.element.android.x/io.element.android.features.lockscreen.impl.unlock.activity.PinUnlockActivity" to "🔓 ОТКЛЮЧИТЬ экран пароля (Emergency)",
        "pm enable io.element.android.x/io.element.android.features.lockscreen.impl.unlock.activity.PinUnlockActivity" to "🔐 ВЕРНУТЬ экран пароля",
        "cmd package compile -m speed-profile io.element.android.x" to "⚡ Патч скорости/графики (Fix Black Screen)",
        "wm size reset && wm density reset" to "🎨 Сбросить настройки экрана"
    )

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CyberCard(title = "LOCAL_SHELL", color = CyberInfo, modifier = Modifier.fillMaxWidth()) {
            Column {
                OutlinedTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    textStyle = TextStyle(
                        color = Color.White, 
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    placeholder = { Text("Enter shell command...", color = Color.Gray, fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val cmd = commandText.trim()
                        if (cmd.isNotBlank()) {
                            scope.launch {
                                GlobalLog.log("TRYING: $cmd", "warn", "SHELL")
                                val res = ShizukuExecutor.executeCommand(cmd)
                                GlobalLog.log(if (res.isSuccessful) "DONE (${res.executionTimeMs}ms)" else "FAILED", if (res.isSuccessful) "ok" else "crit", "SHELL")
                                if (res.output.isNotBlank()) GlobalLog.log(res.output, "info", "SHELL")
                                if (res.error.isNotBlank()) GlobalLog.log(res.error, "crit", "SHELL")
                            }
                            commandText = ""
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberInfo,
                        unfocusedBorderColor = CyberBorder,
                        cursorColor = CyberInfo,
                        focusedContainerColor = CyberBackground,
                        unfocusedContainerColor = CyberBackground
                    )
                )
                
                Spacer(Modifier.height(10.dp))
                
                Button(
                    onClick = {
                        val cmd = commandText.trim()
                        if (cmd.isNotBlank()) {
                            scope.launch {
                                GlobalLog.log("TRYING: $cmd", "warn", "SHELL")
                                val res = ShizukuExecutor.executeCommand(cmd)
                                GlobalLog.log(if (res.isSuccessful) "DONE (${res.executionTimeMs}ms)" else "FAILED", if (res.isSuccessful) "ok" else "crit", "SHELL")
                                if (res.output.isNotBlank()) GlobalLog.log(res.output, "info", "SHELL")
                                if (res.error.isNotBlank()) GlobalLog.log(res.error, "crit", "SHELL")
                            }
                            commandText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberInfo, contentColor = CyberBackground),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                ) {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("EXECUTE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        CyberCard(title = "SHELL_GUIDE", color = Color.Gray) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tap command to fill input:", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(commonCommands) { (cmd, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(1.dp, CyberBorder)
                                .background(CyberSurface.copy(alpha = 0.5f))
                                .clickable { commandText = cmd }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null, tint = CyberInfo, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(cmd, color = CyberInfo, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(desc, color = Color.Gray, fontSize = 8.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
