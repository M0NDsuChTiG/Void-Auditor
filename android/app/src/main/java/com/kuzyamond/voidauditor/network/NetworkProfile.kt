package com.kuzyamond.voidauditor.network

enum class NetworkMode {
    LOCAL_LAB,
    CGNAT,
    PUBLIC_IP,
    TETHERING,
    OFFLINE,
    UNKNOWN
}

enum class Subnet(val cidr: String, val prefix: Int) {
    CGNAT_100_64("100.64.0.0", 10),
    CGNAT_192_168("192.168.0.0", 16),
    LAB_13_13("13.13.213.0", 24)
}

data class ScanTarget(
    val ip: String,
    val hostname: String = "",
    val mac: String = "",
    val vendor: String = ""
)

data class HostInfo(
    val ip: String,
    val mac: String = "",
    val hostname: String = "",
    val vendor: String = "",
    val openPorts: List<Int> = emptyList(),
    val services: Map<Int, String> = emptyMap(),
    val rttMs: Long = -1,
    val isAlive: Boolean = false,
    val fullPortsScanned: Boolean = false
)

data class ScanResult(
    val targets: List<ScanTarget>,
    val aliveHosts: List<HostInfo>,
    val elapsedMs: Long,
    val scannedCount: Int,
    val foundCount: Int
)

data class NetworkProfile(
    val mode: NetworkMode = NetworkMode.UNKNOWN,
    val localIp: String = "",
    val publicIp: String = "",
    val gatewayIp: String = "",
    val interfaceName: String = "",
    val ssid: String = "",
    val bssid: String = "",
    val subnetMask: Int = 24,
    val isManualOverride: Boolean = false,
    val manualCidr: String = ""
) {
    val displayMode: String
        get() = when (mode) {
            NetworkMode.LOCAL_LAB -> "LOCAL_LAB"
            NetworkMode.CGNAT -> "CGNAT"
            NetworkMode.PUBLIC_IP -> "PUBLIC_IP"
            NetworkMode.TETHERING -> "TETHERING"
            NetworkMode.OFFLINE -> "OFFLINE"
            NetworkMode.UNKNOWN -> "UNKNOWN"
        }
}
