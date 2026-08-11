package com.kuzyamond.voidauditor.network

import com.kuzyamond.voidauditor.core.ShizukuExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/**
 * NET_SCAN engine: subnet discovery (Shizuku ICMP batch + TCP fallback)
 * and TCP port scan with optional service banners.
 *
 * Cancellation: cooperative via [ensureActive] — cancel the outer Job
 * from UI (e.g. HostDetailDialog) to stop in-flight work.
 */
object NetworkScanner {

    // -------------------------------------------------------------------------
    // Port sets
    // -------------------------------------------------------------------------

    val COMMON_PORTS: List<Int> = listOf(
        22, 53, 80, 443, 445, 3389, 5555, 8080, 8443, 9090
    )

    val FULL_PORTS: List<Int> = (1..65535).toList()

    // -------------------------------------------------------------------------
    // Tunables
    // -------------------------------------------------------------------------

    const val FULL_CONNECT_TIMEOUT_MS = 450
    const val COMMON_CONNECT_TIMEOUT_MS = 350
    const val FULL_MAX_CONCURRENCY = 128
    const val COMMON_MAX_CONCURRENCY = 64

    const val BANNER_CONNECT_MS = 400
    const val BANNER_SO_TIMEOUT_MS = 600
    const val BANNER_MAX_LEN = 64

    const val DISCOVERY_BATCH_TIMEOUT_MS = 15_000L
    const val TCP_FALLBACK_CONCURRENCY = 64
    const val TCP_FALLBACK_TIMEOUT_MS = 250

    private val HTTP_BANNER_PORTS = setOf(
        80, 8000, 8080, 8081, 8443, 9080, 3000, 5000
    )

    private val KNOWN_SERVICES: Map<Int, String> = mapOf(
        21 to "FTP",
        22 to "SSH",
        23 to "Telnet",
        25 to "SMTP",
        53 to "DNS",
        80 to "HTTP",
        110 to "POP3",
        143 to "IMAP",
        443 to "HTTPS",
        445 to "SMB",
        1433 to "MSSQL",
        3306 to "MySQL",
        3389 to "RDP",
        5432 to "PostgreSQL",
        5555 to "ADB",
        5900 to "VNC",
        6379 to "Redis",
        8080 to "HTTP-Proxy",
        8443 to "HTTPS-Alt",
        27017 to "MongoDB"
    )

    private val IP_REGEX = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")

    // -------------------------------------------------------------------------
    // Subnet host discovery
    // -------------------------------------------------------------------------

    /**
     * @param subnetPrefix e.g. "13.13.213" (without trailing dot / last octet)
     * @return sorted list of alive IPv4 addresses in .1–.254
     */
    suspend fun discoverSubnetHosts(
        subnetPrefix: String,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): List<String> = withContext(Dispatchers.IO) {
        val prefix = subnetPrefix.trim().trimEnd('.')
        val aliveHosts = mutableSetOf<String>()

        // 1) ICMP batch via Shizuku (one shell invocation)
        val batchCmd =
            "for i in \$(seq 1 254); do " +
                "(ping -c 1 -W 1 \"$prefix.\$i\" >/dev/null 2>&1 && echo \"$prefix.\$i\") & " +
                "done; wait"

        val pingResult = ShizukuExecutor.executeCommand(
            batchCmd,
            timeoutMs = DISCOVERY_BATCH_TIMEOUT_MS
        )

        if (pingResult.isSuccessful) {
            pingResult.output.lineSequence()
                .map { it.trim() }
                .filter { IP_REGEX.matches(it) }
                .forEach { aliveHosts.add(it) }
        }

        // 2) TCP fallback for ICMP-silent hosts
        val missingIps = (1..254)
            .map { "$prefix.$it" }
            .filter { it !in aliveHosts }

        if (missingIps.isNotEmpty()) {
            val semaphore = Semaphore(TCP_FALLBACK_CONCURRENCY)
            val done = AtomicInteger(0)

            coroutineScope {
                missingIps.map { ip ->
                    async {
                        semaphore.withPermit {
                            coroutineContext.ensureActive()
                            if (isAnyPortOpen(
                                    ip,
                                    listOf(80, 443, 445, 5555),
                                    TCP_FALLBACK_TIMEOUT_MS
                                )
                            ) {
                                synchronized(aliveHosts) { aliveHosts.add(ip) }
                            }
                            onProgress(done.incrementAndGet(), missingIps.size)
                        }
                    }
                }.awaitAll()
            }
        } else {
            onProgress(1, 1)
        }

        aliveHosts.sortedBy { ip ->
            ip.substringAfterLast('.').toIntOrNull() ?: 0
        }
    }

    // -------------------------------------------------------------------------
    // Port scanning
    // -------------------------------------------------------------------------

    /**
     * TCP connect-scan [ports] on [ip].
     * @param isFullScan uses FULL_* timeouts/concurrency; still pass [FULL_PORTS] explicitly
     * @return map port → service banner or known name
     */
    suspend fun scanPorts(
        ip: String,
        ports: List<Int>,
        isFullScan: Boolean = false,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        if (ports.isEmpty()) return@withContext emptyMap()

        val timeoutMs =
            if (isFullScan) FULL_CONNECT_TIMEOUT_MS else COMMON_CONNECT_TIMEOUT_MS
        val maxConcurrency =
            if (isFullScan) FULL_MAX_CONCURRENCY else COMMON_MAX_CONCURRENCY

        val openPortsMap = mutableMapOf<Int, String>()
        val semaphore = Semaphore(maxConcurrency)
        val scannedCount = AtomicInteger(0)
        val total = ports.size

        coroutineScope {
            ports.map { port ->
                async {
                    semaphore.withPermit {
                        coroutineContext.ensureActive()

                        if (checkTcpPort(ip, port, timeoutMs)) {
                            val banner = grabServiceBanner(ip, port)
                            synchronized(openPortsMap) {
                                openPortsMap[port] = banner
                            }
                        }

                        val n = scannedCount.incrementAndGet()
                        if (n % 50 == 0 || n == total) {
                            onProgress(n, total)
                        }
                    }
                }
            }.awaitAll()
        }

        openPortsMap.toMap()
    }

    /** Convenience: full 1–65535 scan for one host (Host Detail dialog). */
    suspend fun scanAllPorts(
        ip: String,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): Map<Int, String> = scanPorts(
        ip = ip,
        ports = FULL_PORTS,
        isFullScan = true,
        onProgress = onProgress
    )

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private fun isAnyPortOpen(ip: String, ports: List<Int>, timeoutMs: Int): Boolean {
        for (port in ports) {
            if (checkTcpPort(ip, port, timeoutMs)) return true
        }
        return false
    }

    private fun checkTcpPort(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.reuseAddress = true
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun grabServiceBanner(ip: String, port: Int): String {
        val defaultName = KNOWN_SERVICES[port] ?: "UNKNOWN"
        return try {
            Socket().use { socket ->
                socket.soTimeout = BANNER_SO_TIMEOUT_MS
                socket.connect(InetSocketAddress(ip, port), BANNER_CONNECT_MS)

                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                if (port in HTTP_BANNER_PORTS) {
                    val req = "GET / HTTP/1.0\r\nHost: $ip\r\n\r\n"
                    out.write(req.toByteArray(Charsets.US_ASCII))
                    out.flush()
                }

                val buffer = ByteArray(128)
                val bytesRead = input.read(buffer)
                if (bytesRead > 0) {
                    val raw = String(buffer, 0, bytesRead, Charsets.US_ASCII)
                    val sanitized = raw.lineSequence()
                        .map { it.trim() }
                        .firstOrNull { it.isNotEmpty() }
                        ?.replace(Regex("[^\\x20-\\x7E]"), "")
                        ?.take(BANNER_MAX_LEN)

                    if (!sanitized.isNullOrEmpty()) return sanitized
                }
            }
            defaultName
        } catch (_: Exception) {
            defaultName
        }
    }
}
