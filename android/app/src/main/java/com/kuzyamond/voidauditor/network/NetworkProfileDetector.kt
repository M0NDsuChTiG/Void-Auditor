package com.kuzyamond.voidauditor.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.util.Enumeration

object NetworkProfileDetector {

    private const val CGNAT_100_64_START = 0x64400000 // 100.64.0.0
    private const val CGNAT_100_64_END = 0x647FFFFF   // 100.127.255.255
    private const val CGNAT_192_168_START = 0xC0A80000 // 192.168.0.0
    private const val CGNAT_192_168_END = 0xC0A8FFFF   // 192.168.255.255
    private val LAB_13_13 = byteArrayOf(13, 13, (213).toByte(), 0).toInt()

    private val TETHERING_PREFIXES = listOf("rndis", "usb", "wlan")

    fun isIPv4InRange(ip: InetAddress, rangeStart: ByteArray, rangeEnd: ByteArray): Boolean {
        val addr = ip.address ?: return false
        if (addr.size != 4) return false
        val ipInt = addr.toInt()
        val startInt = rangeStart.toInt()
        val endInt = rangeEnd.toInt()
        return ipInt in startInt..endInt
    }

    private fun ByteArray.toInt(): Int {
        return ((this[0].toInt() and 0xFF) shl 24) or
                ((this[1].toInt() and 0xFF) shl 16) or
                ((this[2].toInt() and 0xFF) shl 8) or
                (this[3].toInt() and 0xFF)
    }

    private fun isCGNAT(ip: InetAddress): Boolean {
        val addr = ip.address ?: return false
        if (addr.size != 4) return false
        val ipInt = addr.toInt()
        return (ipInt in CGNAT_100_64_START..CGNAT_100_64_END) ||
                (ipInt in CGNAT_192_168_START..CGNAT_192_168_END)
    }

    private fun isLocalLab(ip: InetAddress): Boolean {
        val addr = ip.address ?: return false
        if (addr.size != 4) return false
        val ipInt = addr.toInt()
        return ipInt == LAB_13_13
    }

    private fun isCarrierGrade(ip: InetAddress): Boolean {
        val addr = ip.address ?: return false
        if (addr.size != 4) return false
        val ipInt = addr.toInt()
        val start = byteArrayOf(100, 64, 0, 0).toInt()
        val end = byteArrayOf(100, 127, (255).toByte(), (255).toByte()).toInt()
        return ipInt in start..end
    }

    suspend fun detectProfile(manualOverride: String? = null): NetworkProfile = withContext(Dispatchers.IO) {
        if (manualOverride != null) {
            return@withContext buildManualProfile(manualOverride)
        }

        val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
        var activeInterface: NetworkInterface? = null
        var localIp: InetAddress? = null

        while (interfaces.hasMoreElements()) {
            val intf = interfaces.nextElement()
            if (!intf.isUp || intf.isLoopback || intf.isVirtual) continue

            val inetAddresses: Enumeration<InetAddress> = intf.inetAddresses
            while (inetAddresses.hasMoreElements()) {
                val addr = inetAddresses.nextElement()
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    activeInterface = intf
                    localIp = addr
                    break
                }
            }
            if (localIp != null) break
        }

        if (localIp == null) {
            return@withContext NetworkProfile(mode = NetworkMode.OFFLINE)
        }

        val localIpStr = localIp.hostAddress ?: ""
        val interfaceName = activeInterface?.name ?: ""
        val isTethering = TETHERING_PREFIXES.any { interfaceName.startsWith(it) }

        val publicIp = tryFetchPublicIp()
        val gatewayIp = tryDetectGateway()
        val ssid = tryReadSsid()
        val bssid = tryReadBssid()

        val mode = when {
            isLocalLab(localIp) -> NetworkMode.LOCAL_LAB
            isTethering && isCarrierGrade(localIp) -> NetworkMode.TETHERING
            isCGNAT(localIp) -> NetworkMode.CGNAT
            publicIp.isNotBlank() && publicIp != localIpStr -> NetworkMode.PUBLIC_IP
            else -> NetworkMode.UNKNOWN
        }

        NetworkProfile(
            mode = mode,
            localIp = localIpStr,
            publicIp = publicIp,
            gatewayIp = gatewayIp,
            interfaceName = interfaceName,
            ssid = ssid,
            bssid = bssid,
            subnetMask = 24
        )
    }

    private suspend fun buildManualProfile(cidr: String): NetworkProfile {
        val parts = cidr.split("/")
        val ip = parts.getOrElse(0) { cidr }
        val prefix = parts.getOrNull(1)?.toIntOrNull() ?: 24
        return NetworkProfile(
            mode = NetworkMode.LOCAL_LAB,
            localIp = ip,
            subnetMask = prefix,
            isManualOverride = true,
            manualCidr = cidr
        )
    }

    private suspend fun tryFetchPublicIp(): String = withContext(Dispatchers.IO) {
        listOf("https://checkip.amazonaws.com", "https://api.ipify.org", "https://icanhazip.com")
            .firstNotNullOfOrNull { url ->
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val ip = reader.readLine()?.trim()
                    reader.close()
                    if (ip != null && ip.matches(Regex("^[\\d.]+$"))) ip else null
                } catch (_: Exception) {
                    null
                }
            } ?: ""
    }

    private suspend fun tryDetectGateway(): String = withContext(Dispatchers.IO) {
        try {
            val proc = Runtime.getRuntime().exec("ip route show default")
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val line = reader.readLine() ?: return@withContext ""
            reader.close()
            val parts = line.split("\\s+".toRegex())
            parts.getOrNull(2) ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun tryReadSsid(): String = withContext(Dispatchers.IO) {
        try {
            val proc = Runtime.getRuntime().exec("cmd wifi get-wifi-info 2>/dev/null")
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val lines = reader.readLines()
            reader.close()
            lines.find { it.startsWith("SSID") }?.substringAfter(":")?.trim() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun tryReadBssid(): String = withContext(Dispatchers.IO) {
        try {
            val proc = Runtime.getRuntime().exec("cmd wifi get-wifi-info 2>/dev/null")
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val lines = reader.readLines()
            reader.close()
            lines.find { it.startsWith("BSSID") }?.substringAfter(":")?.trim() ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
