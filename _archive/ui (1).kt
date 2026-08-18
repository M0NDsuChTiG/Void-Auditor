package com.kuzyamond.voidauditor.network

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun HostDetailDialog(
    host: HostInfo,
    onDismiss: () -> Unit,
    onHostUpdated: (HostInfo) -> Unit
) {
    var currentHost by remember { mutableStateOf(host) }
    var isFullScanning by remember { mutableStateOf(false) }
    var scanProgressText by remember { mutableStateOf("") }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // Гарантированная отмена сокетов при закрытии диалога или смене экрана
    DisposableEffect(Unit) {
        onDispose {
            scanJob?.cancel()
        }
    }

    val handleDismiss = {
        scanJob?.cancel()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = {
            if (!isFullScanning) handleDismiss()
        },
        title = { Text("Хост: ${currentHost.ip}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Имя: ${currentHost.hostname.ifEmpty { "Неизвестно" }}")
                Text("MAC: ${currentHost.mac.ifEmpty { "Н/Д" }}")
                Text("Статус полного скана: ${if (currentHost.fullPortsScanned) "✓ Завершен (1-65535)" else "Только частые порты"}")

                Spacer(modifier = Modifier.height(8.dp))
                Text("Открытые порты (${currentHost.openPorts.size}):", style = MaterialTheme.typography.titleSmall)

                if (currentHost.services.isNotEmpty()) {
                    currentHost.services.forEach { (port, service) ->
                        Text("• Порт $port: $service", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text("Открытых портов не обнаружено", style = MaterialTheme.typography.bodySmall)
                }

                if (isFullScanning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(scanProgressText, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            if (!currentHost.fullPortsScanned) {
                Button(
                    enabled = !isFullScanning,
                    onClick = {
                        isFullScanning = true
                        scanJob = scope.launch {
                            try {
                                val fullResults = NetworkScanner.scanPorts(
                                    ip = currentHost.ip,
                                    ports = NetworkScanner.FULL_PORTS,
                                    isFullScan = true,
                                    onProgress = { scanned, total ->
                                        scanProgressText = "Сканирование: $scanned / $total портов"
                                    }
                                )
                                // Authoritative State Pattern: явная замена (replace) без слияния со старыми портовыми записями
                                val updated = currentHost.copy(
                                    openPorts = fullResults.keys.sorted(),
                                    services = fullResults,
                                    fullPortsScanned = true
                                )
                                currentHost = updated
                                onHostUpdated(updated)
                            } finally {
                                isFullScanning = false
                            }
                        }
                    }
                ) {
                    Text(if (isFullScanning) "Сканирование..." else "Скан всех портов (1-65535)")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = handleDismiss
            ) {
                Text(if (isFullScanning) "Отмена сканирования" else "Закрыть")
            }
        }
    )
}