package com.kuzyamond.voidauditor.network

import com.kuzyamond.voidauditor.core.NetScanStorage
import com.kuzyamond.voidauditor.GlobalLog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    var fullScan by remember { mutableStateOf(false) }
    var selectedHost by remember { mutableStateOf<HostInfo?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    fun startScan() {
        if (isScanning) return
        val ports = if (fullScan) NetworkScanner.FULL_PORTS else NetworkScanner.COMMON_PORTS
        scanJob = scope.launch {
            isScanning = true
            scanResult = null
            error = null
            scanProgress = 0f
            progressText = ""
            try {
                val ip = profile?.localIp ?: "192.168.1.1"
                val targets = scanner.generateTargets(ip, profile?.subnetMask ?: 24)
                val partialHosts = mutableListOf<HostInfo>()
                val hosts = scanner.scanHostsWithPorts(
                    targets = targets,
                    ports = ports,
                    onProgress = { hostDone, hostTotal, portDone, portTotal ->
                        if (portTotal > 0) {
                            scanProgress = portDone.toFloat() / portTotal.toFloat()
                            progressText = "host $hostDone/$hostTotal • port $portDone/$portTotal"
                        } else {
                            scanProgress = hostDone.toFloat() / hostTotal.toFloat()
                            progressText = "$hostDone / $hostTotal"
                        }
                    },
                    onHostResult = { host ->
                        partialHosts.add(host)
                        scanResult = ScanResult(
                            targets = targets,
                            aliveHosts = partialHosts.toList(),
                            elapsedMs = 0,
                            scannedCount = targets.size,
                            foundCount = partialHosts.size
                        )
                    }
                )
                scanResult = ScanResult(
                    targets = targets,
                    aliveHosts = hosts,
                    elapsedMs = 0,
                    scannedCount = targets.size,
                    foundCount = hosts.size
                )
            } catch (e: CancellationException) {
            } catch (e: Exception) {
                error = "Scan error: ${e.message}"
            } finally {
                isScanning = false
                scanJob = null
            }
        }
    }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        profile = detector.detectProfile(context)
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Subnet Scan", style = MaterialTheme.typography.titleMedium)
                        if (isScanning) {
                            TextButton(onClick = { scanJob?.cancel() }) {
                                Text("CANCEL", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            FilledTonalIconButton(
                                onClick = { startScan() },
                                enabled = !isScanning
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Scan")
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !fullScan,
                            onClick = { if (!isScanning) fullScan = false },
                            label = { Text("COMMON PORTS") },
                            enabled = !isScanning
                        )
                        FilterChip(
                            selected = fullScan,
                            onClick = { if (!isScanning) fullScan = true },
                            label = { Text("ALL 1-65535") },
                            enabled = !isScanning
                        )
                    }
                    if (fullScan) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Full scan of 65535 ports per host may take several minutes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    HostCard(host = host, onClick = { selectedHost = host })
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

    selectedHost?.let { host ->
        HostDetailDialog(
            host = host,
            onDismiss = { selectedHost = null },
            onHostUpdated = { updated ->
                scanResult = scanResult?.copy(
                    aliveHosts = scanResult!!.aliveHosts.map { if (it.ip == updated.ip) updated else it }
                )
                selectedHost = updated
            }
        )
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
private fun HostCard(host: HostInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
            if (host.services.isNotEmpty()) {
                Text(
                    text = host.services.entries.joinToString("  •  ") { "${it.key}: ${it.value}" },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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

@Composable
private fun HostDetailDialog(
    host: HostInfo,
    onDismiss: () -> Unit,
    onHostUpdated: (HostInfo) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copiedKey by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var liveHost by remember(host) { mutableStateOf(host) }
    var portScanning by remember { mutableStateOf(false) }
    var portProgress by remember { mutableStateOf("") }

    fun copyText(key: String, text: String) {
        clipboard.setText(AnnotatedString(text))
        copiedKey = key
        GlobalLog.log("COPIED $key → $text", "ok", "CONN")
        scope.launch {
            delay(1500)
            if (copiedKey == key) copiedKey = null
        }
    }

    fun scanAllPorts() {
        if (portScanning || liveHost.fullPortsScanned) return
        portScanning = true
        portProgress = "0/65535"
        scope.launch {
            val scanned = NetworkScanner.scanAllPorts(liveHost.ip) { done, total ->
                portProgress = "$done/$total"
            }
            val updated = liveHost.copy(
                openPorts = scanned.keys.toList().sorted(),
                services = scanned.filterValues { it.isNotEmpty() },
                fullPortsScanned = true
            )
            liveHost = updated
            onHostUpdated(updated)
            portScanning = false
            portProgress = ""
        }
    }

    val portsText = liveHost.openPorts.joinToString(", ").ifEmpty { "NONE" }
    val servicesText = liveHost.services.entries.joinToString("\n") { "${it.key}: ${it.value}" }.ifEmpty { "NONE" }
    val rttText = if (liveHost.rttMs >= 0) "${liveHost.rttMs} ms" else "—"
    val jsonBlock = buildString {
        append("{\"ip\":\"${liveHost.ip.jsonEscaped()}\"")
        append(",\"hostname\":\"${liveHost.hostname.jsonEscaped()}\"")
        append(",\"mac\":\"${liveHost.mac.jsonEscaped()}\"")
        append(",\"vendor\":\"${liveHost.vendor.jsonEscaped()}\"")
        append(",\"rtt_ms\":${liveHost.rttMs}")
        append(",\"alive\":${liveHost.isAlive}")
        append(",\"open_ports\":[${liveHost.openPorts.joinToString(",")}]")
        append(",\"services\":{${liveHost.services.entries.joinToString(",") { "\"${it.key}\":\"${it.value.jsonEscaped()}\"" }}}")
        append("}")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = liveHost.ip,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DetailCopyRow("HOSTNAME", liveHost.hostname.ifEmpty { "—" }, copiedKey) { k, v -> copyText(k, v) }
                DetailCopyRow("MAC", liveHost.mac.ifEmpty { "—" }, copiedKey) { k, v -> copyText(k, v) }
                DetailCopyRow("VENDOR", liveHost.vendor.ifEmpty { "—" }, copiedKey) { k, v -> copyText(k, v) }
                DetailCopyRow("RTT", rttText, copiedKey) { k, v -> copyText(k, v) }
                DetailCopyRow("ALIVE", if (liveHost.isAlive) "TRUE" else "FALSE", copiedKey) { k, v -> copyText(k, v) }
                DetailCopyRow("OPEN PORTS", portsText, copiedKey) { k, v -> copyText(k, v) }
                DetailCopyRow("SERVICES", servicesText, copiedKey, maxLines = 8) { k, v -> copyText(k, v) }

                Spacer(Modifier.height(8.dp))

                if (!liveHost.fullPortsScanned) {
                    FilledTonalButton(
                        onClick = { scanAllPorts() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !portScanning
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (portScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("SCANNING $portProgress", fontFamily = FontFamily.Monospace)
                            } else {
                                Text("SCAN ALL PORTS (1-65535)", fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    ) {
                        Text("✓ FULL SCAN COMPLETE", fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { copyText("ALL", jsonBlock) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (copiedKey == "ALL") "✓ COPIED (JSON)" else "COPY ALL (JSON)",
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("DISMISS")
            }
        }
    )
}

@Composable
private fun DetailCopyRow(
    label: String,
    value: String,
    copiedKey: String?,
    maxLines: Int = 2,
    onCopy: (String, String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(92.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = { onCopy(label, value) },
            modifier = Modifier.size(32.dp)
        ) {
            if (copiedKey == label) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "copied",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "copy $label",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun String.jsonEscaped(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")
