package com.kuzyamond.voidauditor.network

import com.kuzyamond.voidauditor.core.NetScanStorage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val modeColors = mapOf(
    NetworkMode.LOCAL_LAB to Color(0xFF4CAF50),
    NetworkMode.CGNAT to Color(0xFFFF9800),
    NetworkMode.PUBLIC_IP to Color(0xFFF44336),
    NetworkMode.TETHERING to Color(0xFF2196F3),
    NetworkMode.OFFLINE to Color(0xFF9E9E9E),
    NetworkMode.UNKNOWN to Color(0xFF9E9E9E)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDashboardScreen(
    onNavigateBack: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val detector = remember { NetworkProfileDetector }
    val scanner = remember { NetworkScanner }

    var profile by NetScanStorage::profile
    var scanResult by NetScanStorage::scanResult
    var isScanning by NetScanStorage::isScanning
    var scanProgress by NetScanStorage::scanProgress
    var progressText by NetScanStorage::progressText
    var error by NetScanStorage::error

    LaunchedEffect(Unit) {
        profile = detector.detectProfile()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            profile?.let { p ->
                item {
                    ProfileCard(profile = p)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Subnet Scan", style = MaterialTheme.typography.titleMedium)
                    FilledTonalIconButton(
                        onClick = {
                            if (!isScanning) {
                                scope.launch {
                                    isScanning = true
                                    scanResult = null
                                    error = null
                                    try {
                                        val ip = profile?.localIp ?: "192.168.1.1"
                                        val targets = scanner.generateTargets(ip, profile?.subnetMask ?: 24)
                                        val hosts = scanner.scanHostsWithPorts(
                                            targets = targets,
                                            onProgress = { done, total ->
                                                scanProgress = done.toFloat() / total.toFloat()
                                                progressText = "$done / $total"
                                            }
                                        )
                                        scanResult = ScanResult(
                                            targets = targets,
                                            aliveHosts = hosts,
                                            elapsedMs = 0,
                                            scannedCount = targets.size,
                                            foundCount = hosts.size
                                        )
                                    } catch (e: Exception) {
                                        error = e.message ?: "Scan error"
                                    } finally {
                                        isScanning = false
                                    }
                                }
                            }
                        },
                        enabled = !isScanning
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan")
                    }
                }
            }

            if (isScanning) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = scanProgress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Scanning: $progressText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            error?.let { e ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(e, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            val alive = scanResult?.aliveHosts ?: emptyList()
            if (alive.isNotEmpty()) {
                item {
                    Text(
                        "Found ${alive.size} host(s)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(alive, key = { it.ip }) { host ->
                    HostCard(host = host)
                }
            } else if (!isScanning && scanResult != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            "No hosts found",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: NetworkProfile) {
    val modeColor = modeColors[profile.mode] ?: Color.Gray

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.NetworkCheck,
                        contentDescription = null,
                        tint = modeColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = profile.displayMode,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = modeColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            InfoRow(label = "Local IP", value = profile.localIp)
            if (profile.publicIp.isNotBlank()) {
                InfoRow(label = "Public IP", value = profile.publicIp)
            }
            if (profile.gatewayIp.isNotBlank()) {
                InfoRow(label = "Gateway", value = profile.gatewayIp)
            }
            InfoRow(label = "Interface", value = profile.interfaceName)
            if (profile.ssid.isNotBlank()) {
                InfoRow(label = "SSID", value = profile.ssid)
            }
            if (profile.isManualOverride) {
                InfoRow(label = "Override CIDR", value = profile.manualCidr)
            }
        }
    }
}

@Composable
private fun HostCard(host: HostInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = host.ip,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
                if (host.rttMs >= 0) {
                    Text(
                        text = "${host.rttMs}ms",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (host.hostname.isNotBlank()) {
                Text(
                    text = host.hostname,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (host.mac.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = host.mac,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (host.vendor.isNotBlank()) {
                        Text(
                            text = " (${host.vendor})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (host.openPorts.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Open: ${host.openPorts.joinToString(", ")}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label: ",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
