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

data class NetworkIdentity(
    val interfaceName: String,
    val ipv4: String,
    val prefix: Int,
    val gateway: String = "",
    val isConnected: Boolean = false
) {
    val network: String
        get() {
            val int = ipv4ToInt(ipv4) ?: return ""
            return intToIpv4(int and networkMask(prefix))
        }

    val broadcast: String
        get() {
            val int = ipv4ToInt(ipv4) ?: return ""
            return intToIpv4(int or networkMask(prefix).inv())
        }
}

data class ScanScope(
    val localIp: String,
    val prefix: Int,
    val network: String,
    val broadcast: String
) {
    val hostCount: Int
        get() = if (prefix in 4..30) (1L shl (32 - prefix)).toInt() else 0
}

internal fun ipv4ToInt(ip: String): Int? {
    val parts = ip.split('.')
    if (parts.size != 4) return null
    var result = 0
    for (part in parts) {
        val octet = part.toIntOrNull() ?: return null
        if (octet < 0 || octet > 255) return null
        result = (result shl 8) or octet
    }
    return result
}

internal fun intToIpv4(value: Int): String =
    "${(value ushr 24) and 0xFF}.${(value ushr 16) and 0xFF}.${(value ushr 8) and 0xFF}.${value and 0xFF}"

internal fun networkMask(prefix: Int): Int = when {
    prefix <= 0 -> 0
    prefix >= 32 -> -1
    else -> ((1L shl (32 - prefix)) - 1L).toInt().inv()
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
