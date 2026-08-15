package com.kuzyamond.voidauditor.network

import com.kuzyamond.voidauditor.core.ShizukuExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.asCoroutineDispatcher

object NetworkScanner {

    val COMMON_PORTS = listOf(22, 53, 80, 443, 445, 5555, 8080, 8443, 9090, 3389, 5900)
    val FULL_PORTS: List<Int> = (1..65535).toList()

    // Сканирование портов: 256 одновременных SYN переполняют очередь роутера
    // (netfilter backlog) — реально открытые порты теряются (замер на CPE:
    // 256→31%, 64→78%, 32→~92%, а под постоянной нагрузкой роутер коллапсирует
    // до <20%, восстанавливаясь за ~5с тишины). Поэтому:
    //  - 32 параллельных (лучший компромисс точность/скорость),
    //  - таймаут 150мс (LAN-ответ приходит за миллисекунды, таймаут нужен только
    //    для потерянных/фильтруемых SYN),
    //  - адаптивная пауза между порциями, если прошлая упёрлась в таймауты
    //    (роутер успевает дренировать SYN-очередь; на здоровых сетях пауз нет),
    //  - до 4 проходов: не ответившие порты повторяются (потери ~0.5-3%/проход).
    private const val PORT_CONNECT_TIMEOUT_MS = 150
    private const val PORT_BATCH_SIZE = 32
    private const val MAX_SCAN_PASSES = 4
    private const val CHUNK_PAUSE_MS = 80L
    private const val BANNER_READ_TIMEOUT_MS = 400
    private const val BANNER_MAX_LEN = 64

    private val portScanDispatcher by lazy {
        Executors.newFixedThreadPool(PORT_BATCH_SIZE).asCoroutineDispatcher()
    }

    private val HTTP_PORTS = setOf(80, 8000, 8080, 8888, 3000, 5000, 8081, 9080)

    private val KNOWN_SERVICES = mapOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS",
        67 to "DHCP", 68 to "DHCP", 80 to "HTTP", 110 to "POP3", 123 to "NTP",
        143 to "IMAP", 443 to "HTTPS", 465 to "SMTPS", 587 to "SMTP",
        993 to "IMAPS", 995 to "POP3S", 3306 to "MySQL", 3389 to "RDP",
        445 to "SMB", 5353 to "mDNS", 5555 to "ADB", 5900 to "VNC", 5432 to "PostgreSQL"
    )

    private val IPV4_REGEX = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    /**
     * Валидация IPv4-адреса (4 октета 0..255).
     * Обязательна перед интерполяцией адресов в shell-скрипты — защита от command injection.
     */
    internal fun isValidIpv4(ip: String): Boolean {
        val m = IPV4_REGEX.matchEntire(ip.trim()) ?: return false
        return m.groupValues.drop(1).all { it.toInt() in 0..255 }
    }

    suspend fun generateTargets(baseIp: String, prefix: Int = 24): List<ScanTarget> {
        return withContext(Dispatchers.Default) {
            try {
                // Валидация IPv4 перед построением диапазона (защита от инъекций в shell)
                if (!isValidIpv4(baseIp)) return@withContext emptyList()
                val parts = baseIp.split(".")
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
        // Defense in depth: допускаем в shell-скрипт только валидные IPv4
        val validTargets = targets.filter { isValidIpv4(it.ip) }
        if (validTargets.isEmpty()) return emptyList()
        onProgress(1, validTargets.size)

        val aliveIps = try {
            withTimeout(45_000) {
                val script = buildString {
                    append("for ip in ")
                    append(validTargets.joinToString(" ") { it.ip })
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
        onProgress(validTargets.size, validTargets.size)

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
        onProgress: (hostDone: Int, hostTotal: Int, portDone: Int, portTotal: Int) -> Unit = { _, _, _, _ -> },
        onHostResult: (HostInfo) -> Unit = {}
    ): List<HostInfo> {
        if (targets.isEmpty()) return emptyList()
        val aliveHosts = scanHosts(targets) { done, total ->
            onProgress(done, total, 0, 0)
        }
        val result = mutableListOf<HostInfo>()
        aliveHosts.forEachIndexed { index, host ->
val scanned = tcpScanPorts(host.ip, ports) { done, total ->
                    onProgress(index + 1, aliveHosts.size, done, total)
                }
                val updated = host.copy(
                    openPorts = scanned.keys.sorted(),
                    services = scanned.filterValues { it.isNotEmpty() }
                )
            onHostResult(updated)
            result.add(updated)
        }
        return result
    }

    private suspend fun tcpScanPorts(
        ip: String,
        ports: List<Int>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Map<Int, String> {
        if (ports.isEmpty()) return emptyMap()
        val open = Collections.synchronizedMap(mutableMapOf<Int, String>())
        val done = AtomicInteger(0)
        val total = ports.size

        // Многопроходный скан: не ответившие за таймаут порты (SYN потерян роутером
        // при перегрузке либо порт фильтруется) повторяются на следующих проходах.
        // Пул неответивших схлопывается в 2-10 раз за проход; 4 проходов хватает
        // с запасом даже на капризных роутерах.
        var pending = ports
        repeat(MAX_SCAN_PASSES) { pass ->
            if (pending.isEmpty()) return@repeat
            val nextTimedOut = Collections.synchronizedList(mutableListOf<Int>())
            val prevChunkTimedOut = AtomicBoolean(false)

            pending.chunked(PORT_BATCH_SIZE).forEach { batch ->
                // Адаптивная пауза: только если прошлая порция упёрлась в таймауты
                // (роутер задыхается). На здоровых сетях скан идёт без замедления.
                if (prevChunkTimedOut.get()) delay(CHUNK_PAUSE_MS)
                val chunkTimedOut = AtomicBoolean(false)

                coroutineScope {
                    batch.forEach { port ->
                        launch(portScanDispatcher) {
                            try {
                                val socket = Socket()
                                try {
                                    socket.connect(InetSocketAddress(ip, port), PORT_CONNECT_TIMEOUT_MS)
                                    val banner = grabService(socket, ip, port)
                                    open[port] = banner.ifEmpty { KNOWN_SERVICES[port] ?: "" }
                                } catch (_: SocketTimeoutException) {
                                    chunkTimedOut.set(true)
                                    if (pass < MAX_SCAN_PASSES - 1) nextTimedOut.add(port)
                                } finally {
                                    try {
                                        socket.close()
                                    } catch (_: Exception) {
                                    }
                                }
                            } catch (_: Exception) {
                            } finally {
                                onProgress(minOf(done.incrementAndGet(), total), total)
                            }
                        }
                    }
                }

                prevChunkTimedOut.set(chunkTimedOut.get())
            }

            pending = nextTimedOut
        }

        return open.toSortedMap()
    }

    private fun grabService(socket: Socket, ip: String, port: Int): String {
        return try {
            socket.soTimeout = BANNER_READ_TIMEOUT_MS
            if (port in HTTP_PORTS) {
                socket.getOutputStream().write(
                    "GET / HTTP/1.0\r\nHost: $ip\r\nUser-Agent: VoidAuditor/1.4\r\n\r\n".toByteArray()
                )
                socket.getOutputStream().flush()
            }
            val buf = ByteArray(2048)
            val n = socket.getInputStream().read(buf)
            if (n <= 0) return ""
            val raw = String(buf, 0, n, Charsets.ISO_8859_1)
            val firstLine = raw.lineSequence().firstOrNull() ?: ""
            sanitizeBanner(firstLine)
        } catch (_: Exception) {
            ""
        }
    }

    private fun sanitizeBanner(raw: String): String {
        val cleaned = raw.map { ch ->
            if (ch.code in 32..126) ch else ' '
        }.joinToString("").trim().replace(Regex("\\s+"), " ")
        return cleaned.take(BANNER_MAX_LEN)
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

    suspend fun scanAllPorts(
        ip: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Map<Int, String> {
        return tcpScanPorts(ip, FULL_PORTS, onProgress)
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
