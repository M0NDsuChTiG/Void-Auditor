package com.kuzyamond.voidauditor.network

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isFullScanning) onDismiss() },
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
                        scope.launch {
                            val fullResults = NetworkScanner.scanPorts(
                                ip = currentHost.ip,
                                ports = NetworkScanner.FULL_PORTS,
                                isFullScan = true,
                                onProgress = { scanned, total ->
                                    scanProgressText = "Сканирование: $scanned / $total портов"
                                }
                            )
                            val updated = currentHost.withUpdatedPorts(fullResults, isFull = true)
                            currentHost = updated
                            onHostUpdated(updated)
                            isFullScanning = false
                        }
                    }
                ) {
                    Text(if (isFullScanning) "Сканирование..." else "Скан всех портов (1-65535)")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isFullScanning,
                onClick = onDismiss
            ) {
                Text("Закрыть")
            }
        }
    )
}