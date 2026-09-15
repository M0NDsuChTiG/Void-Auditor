package com.kuzyamond.voidauditor.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import com.kuzyamond.voidauditor.core.evidence.DefaultRouteEvidence
import com.kuzyamond.voidauditor.core.EvidenceResult
import com.kuzyamond.voidauditor.core.evidence.WifiEvidence
import com.kuzyamond.voidauditor.core.USFPipeline
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

    private const val CGNAT_100_64_START = 0x64400000
    private const val CGNAT_100_64_END = 0x647FFFFF
    private const val CGNAT_192_168_START = 0xC0A80000
    private const val CGNAT_192_168_END = 0xC0A8FFFF
    private val LAB_13_13 = byteArrayOf(13, 13, 213.toByte(), 0).toInt()

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
        val end = byteArrayOf(100, 127, 255.toByte(), 255.toByte()).toInt()
        return ipInt in start..end
    }

    suspend fun detectProfile(context: Context, manualOverride: String? = null): NetworkProfile = withContext(Dispatchers.IO) {
        if (manualOverride != null) {
            buildManualProfile(manualOverride)
        } else {
            var activeInterface: NetworkInterface? = null
            var localIp: InetAddress? = null

            val activeNet = tryResolveActiveNetwork(context)
            if (activeNet != null) {
                try {
                    val intf = NetworkInterface.getByName(activeNet.first)
                    if (intf != null && intf.isUp) {
                        localIp = activeNet.second
                        activeInterface = intf
                    }
                } catch (_: Exception) {
                }
            }

            val routeResult = CapabilityExecutor.execute(USFPipeline.Context(), Capability.ReadDefaultRoute)

            if (localIp == null) {
                val routeEvidence = routeResult.evidence
                if (routeEvidence is EvidenceResult.Parsed && routeEvidence.evidence is DefaultRouteEvidence) {
                    val evidence = routeEvidence.evidence
                    evidence.interfaceName?.let { defaultIface ->
                        if (defaultIface.isNotBlank()) {
                            try {
                                val intf = NetworkInterface.getByName(defaultIface)
                                if (intf != null && intf.isUp) {
                                    localIp = intf.inetAddresses
                                        .asSequence()
                                        .filterIsInstance<Inet4Address>()
                                        .firstOrNull { !it.isLoopbackAddress }
                                    if (localIp != null) activeInterface = intf
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }

            if (localIp == null) {
                val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    if (!intf.isUp || intf.isLoopback || intf.isVirtual) continue

                    val inetAddresses: Enumeration<InetAddress> = intf.inetAddresses
                    while (inetAddresses.hasMoreElements()) {
                        val addr = inetAddresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            localIp = addr
                            activeInterface = intf
                            break
                        }
                    }
                    if (localIp != null) break
                }
            }

            if (localIp == null) {
                NetworkProfile(mode = NetworkMode.OFFLINE)
            } else {
                val resolvedLocalIp = localIp!!
                val localIpStr = resolvedLocalIp.hostAddress ?: ""

                val wifiResult = CapabilityExecutor.execute(USFPipeline.Context(), Capability.ReadWifiInfo)

                var finalInterfaceName = activeInterface?.name ?: ""
                if (finalInterfaceName.isBlank()) {
                    val evidence = (routeResult.evidence as? EvidenceResult.Parsed)?.evidence
                    if (evidence is DefaultRouteEvidence) {
                        finalInterfaceName = evidence.interfaceName ?: ""
                    }
                }

                val isTethering = TETHERING_PREFIXES.any { finalInterfaceName.startsWith(it) }

                val gatewayIp = (routeResult.evidence as? EvidenceResult.Parsed)?.evidence
                    ?.let { (it as? DefaultRouteEvidence)?.gateway } ?: ""

                val wifiEvidence = wifiResult.evidence
                val ssid = (wifiEvidence as? EvidenceResult.Parsed)?.evidence
                    ?.let { (it as? WifiEvidence)?.ssid } ?: ""
                val bssid = (wifiEvidence as? EvidenceResult.Parsed)?.evidence
                    ?.let { (it as? WifiEvidence)?.bssid } ?: ""

                val publicIp = tryFetchPublicIp()

                val mode = when {
                    isLocalLab(resolvedLocalIp) -> NetworkMode.LOCAL_LAB
                    isTethering && isCarrierGrade(resolvedLocalIp) -> NetworkMode.TETHERING
                    isCGNAT(resolvedLocalIp) -> NetworkMode.CGNAT
                    publicIp.isNotBlank() && publicIp != localIpStr -> NetworkMode.PUBLIC_IP
                    else -> NetworkMode.UNKNOWN
                }

                NetworkProfile(
                    mode = mode,
                    localIp = localIpStr,
                    publicIp = publicIp,
                    gatewayIp = gatewayIp,
                    interfaceName = finalInterfaceName,
                    ssid = ssid,
                    bssid = bssid,
                    subnetMask = 24
                )
            }
        }
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

    private fun tryResolveActiveNetwork(context: Context): Pair<String, InetAddress>? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            val network = cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(network) ?: return null
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return null
            val lp = cm.getLinkProperties(network) ?: return null
            val iface = lp.interfaceName ?: return null
            val ipv4 = lp.linkAddresses
                .mapNotNull { it.address }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?: return null
            iface to ipv4
        } catch (_: Exception) {
            null
        }
    }
}