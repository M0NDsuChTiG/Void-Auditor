package com.kuzyamond.voidauditor.network

import com.kuzyamond.voidauditor.core.ShizukuExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.Socket

object NetworkScanner {

    private val COMMON_PORTS = listOf(22, 80, 443, 5555, 8080, 8443, 9090, 3389, 5900, 8443)

    suspend fun generateTargets(baseIp: String, prefix: Int = 24): List<ScanTarget> {
        return withContext(Dispatchers.Default) {
            try {
                val parts = baseIp.split(".")
                if (parts.size != 4) return@withContext emptyList()
                val base = parts.take(3).joinToString(".")
                val hostBits = 32 - prefix
                val total = (1 shl hostBits) - 2
                val start = 1
                val end = minOf(start + total - 1, 254)

                (start..end).map { i ->
                    ScanTarget(ip = "$base.$i")
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun scanHosts(targets: List<ScanTarget>, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<HostInfo> {
        if (targets.isEmpty()) return emptyList()
        onProgress(1, targets.size)

        val aliveIps = try {
            withTimeout(45_000) {
                val script = buildString {
                    append("for ip in ")
                    append(targets.joinToString(" ") { it.ip })
                    append("; do (ping -c 1 -W 1 \"\$ip\" >/dev/null 2>&1 && echo \"\$ip\") & done; wait")
                }
                val result = ShizukuExecutor.executeCommand(script, timeoutMs = 45_000)
                if (!result.isSuccessful) emptyList()
                else result.output.lines().map { it.trim() }.filter { line ->
                    line.matches(Regex("^(\\d{1,3}\\.){3}\\d{1,3}$"))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        onProgress(targets.size, targets.size)

        return withContext(Dispatchers.IO) {
            aliveIps.map { ip ->
                val mac = tryReadMac(ip)
                val hostname = tryResolveHostname(ip)
                HostInfo(
                    ip = ip,
                    mac = mac,
                    hostname = hostname,
                    vendor = if (mac.isNotEmpty()) lookupVendor(mac) else "",
                    rttMs = -1,
                    isAlive = true
                )
            }
        }
    }

    suspend fun scanHostsWithPorts(
        targets: List<ScanTarget>,
        ports: List<Int> = COMMON_PORTS,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<HostInfo> {
        val aliveHosts = scanHosts(targets, onProgress)
        return aliveHosts.map { host ->
            host.copy(openPorts = tcpScanPorts(host.ip, ports))
        }
    }

    private suspend fun tcpScanPorts(ip: String, ports: List<Int>): List<Int> {
        return withContext(Dispatchers.IO) {
            ports.filter { port ->
                try {
                    withTimeout(1500) {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(ip, port), 1000)
                        socket.close()
                        true
                    }
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    private suspend fun tryReadMac(ip: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val result = ShizukuExecutor.executeCommand("cat /proc/net/arp")
                if (!result.isSuccessful) return@withContext ""
                val lines = result.output.lines()

                for (line in lines.drop(1)) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 4 && parts[0] == ip) {
                        val mac = parts[3]
                        if (mac.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"))) {
                            return@withContext mac.uppercase()
                        }
                    }
                }
                ""
            } catch (_: Exception) {
                ""
            }
        }
    }

    private suspend fun tryResolveHostname(ip: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val addr = java.net.InetAddress.getByName(ip)
                val hostname = addr.hostName
                if (hostname != ip) hostname else ""
            } catch (_: Exception) {
                ""
            }
        }
    }

    private val macVendors: Map<String, String> by lazy {
        loadMacPrefixes()
    }

    private fun loadMacPrefixes(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val stream = javaClass.classLoader?.getResourceAsStream("assets/mac_vendors.txt")
            stream?.bufferedReader()?.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.length >= 8) {
                        val prefix = trimmed.take(8).uppercase().replace(":", "")
                        val vendor = trimmed.substring(8).trim()
                        map[prefix] = vendor
                    }
                }
            }
        } catch (_: Exception) {
            map["00037F"] = "Cisco"
            map["0015E1"] = "MikroTik"
            map["002590"] = "Huawei"
            map["0050C2"] = "Microsoft"
            map["005056"] = "VMware"
            map["080027"] = "Oracle"
            map["BC5FF4"] = "ASUSTek"
            map["3C5A37"] = "Intel"
            map["A4A1C2"] = "TP-Link"
        }
        return map
    }

    fun lookupVendor(mac: String): String {
        val clean = mac.replace(":", "").uppercase()
        if (clean.length < 6) return ""
        val prefix = clean.take(6)
        return macVendors[prefix] ?: ""
    }

    suspend fun scanFull(targetIp: String, prefix: Int = 24): ScanResult {
        val startTime = System.currentTimeMillis()
        val targets = generateTargets(targetIp, prefix)
        val aliveHosts = scanHostsWithPorts(targets)

        return ScanResult(
            targets = targets,
            aliveHosts = aliveHosts,
            elapsedMs = System.currentTimeMillis() - startTime,
            scannedCount = targets.size,
            foundCount = aliveHosts.size
        )
    }
}
