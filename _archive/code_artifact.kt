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

object NetworkScanner {

    // Самые частые порты для быстрого сканирования и фоллбэк-детекта хостов
    val COMMON_PORTS = listOf(22, 53, 80, 443, 445, 3389, 5555, 8080, 8443, 9090)
    val FULL_PORTS = (1..65535).toList()

    // Известные сервисы для быстрых баннеров
    private val KNOWN_SERVICES = mapOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP",
        53 to "DNS", 80 to "HTTP", 110 to "POP3", 143 to "IMAP",
        443 to "HTTPS", 445 to "SMB", 1433 to "MSSQL", 3306 to "MySQL",
        3389 to "RDP", 5432 to "PostgreSQL", 5555 to "ADB", 5900 to "VNC",
        6379 to "Redis", 8080 to "HTTP-Proxy", 8443 to "HTTPS-Alt", 27017 to "MongoDB"
    )

    /**
     * Быстрый батч-пинг подсети через Shizuku (ICMP) + TCP Fallback для скрытых устройств
     */
    suspend fun discoverSubnetHosts(
        subnetPrefix: String, // например "13.13.213"
        onProgress: (int: Int, total: Int) -> Unit = { _, _ -> }
    ): List<String> = withContext(Dispatchers.IO) {
        val aliveHosts = mutableSetOf<String>()

        // 1. Быстрый ICMP Ping через Shizuku batch
        val batchCmd = "for i in \$(seq 1 254); do (ping -c 1 -W 1 \"$subnetPrefix.\$i\" >/dev/null 2>&1 && echo \"$subnetPrefix.\$i\") & done; wait"
        val result = ShizukuExecutor.executeCommand(batchCmd, timeoutMs = 15000)

        if (result.isSuccessful) {
            val ipRegex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
            result.output.lines().map { it.trim() }.filter { ipRegex.matches(it) }.forEach {
                aliveHosts.add(it)
            }
        }

        // 2. TCP Fallback для устройств, блокирующих ICMP (проверяем порты 80, 443, 445, 5555)
        val missingIps = (1..254).map { "$subnetPrefix.$it" }.filter { !aliveHosts.contains(it) }
        val semaphore = Semaphore(64) // Ограничение одновременных сокетов

        val fallbackJobs = missingIps.mapIndexed { index, ip ->
            async {
                semaphore.withPermit {
                    coroutineContext.ensureActive()
                    if (isAnyPortOpen(ip, listOf(80, 443, 445, 5555), timeoutMs = 250)) {
                        synchronized(aliveHosts) { aliveHosts.add(ip) }
                    }
                    onProgress(index + 1, missingIps.size)
                }
            }
        }
        fallbackJobs.awaitAll()

        return@withContext aliveHosts.toList().sortedBy { ip ->
            ip.substringAfterLast('.').toIntOrNull() ?: 0
        }
    }

    /**
     * Быстрая проверка открыт ли ХОТЯ БЫ один порт из списка (для определения "живой ли хост")
     */
    private fun isAnyPortOpen(ip: String, ports: List<Int>, timeoutMs: Int): Boolean {
        for (port in ports) {
            try {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(ip, port), timeoutMs)
                    return true
                }
            } catch (_: Exception) { }
        }
        return false
    }

    /**
     * Точный сканер портов с поддержкой батчинга, повторных проверок и баннер-граббинга
     */
    suspend fun scanPorts(
        ip: String,
        ports: List<Int>,
        isFullScan: Boolean = false,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        val openPortsMap = mutableMapOf<Int, String>()
        
        // Для полного скана (65535 портов) берем оптимизированный таймаут и ограничение параллелизма
        val timeoutMs = if (isFullScan) 450 else 350
        val maxConcurrency = if (isFullScan) 128 else 64 // Безопасно для FD limits Android
        val semaphore = Semaphore(maxConcurrency)

        var scannedCount = 0

        coroutineScope {
            val jobs = ports.map { port ->
                async {
                    semaphore.withPermit {
                        coroutineContext.ensureActive()
                        val isOpen = checkTcpPort(ip, port, timeoutMs)
                        
                        if (isOpen) {
                            val banner = grabServiceBanner(ip, port)
                            synchronized(openPortsMap) {
                                openPortsMap[port] = banner
                            }
                        }
                        
                        synchronized(this@NetworkScanner) {
                            scannedCount++
                            if (scannedCount % 50 == 0 || scannedCount == ports.size) {
                                onProgress(scannedCount, ports.size)
                            }
                        }
                    }
                }
            }
            jobs.awaitAll()
        }

        return@withContext openPortsMap
    }

    /**
     * Надежный TCP Connect
     */
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

    /**
     * Чтение баннера сервиса
     */
    private fun grabServiceBanner(ip: String, port: Int): String {
        val defaultName = KNOWN_SERVICES[port] ?: "UNKNOWN"
        return try {
            Socket().use { socket ->
                socket.soTimeout = 600
                socket.connect(InetSocketAddress(ip, port), 400)

                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                // Если порт HTTP/HTTPS-подобный, запрашиваем заголовки
                if (port in listOf(80, 8000, 8080, 8443, 8081, 9080, 3000, 5000)) {
                    out.write("GET / HTTP/1.0\r\nHost: $ip\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    out.flush()
                }

                val buffer = ByteArray(128)
                val bytesRead = input.read(buffer)
                if (bytesRead > 0) {
                    val raw = String(buffer, 0, bytesRead, Charsets.US_ASCII)
                    val sanitized = raw.lines().firstOrNull { it.isNotBlank() }
                        ?.replace(Regex("[^\\x20-\\x7E]"), "") // Только печатные ASCII
                        ?.trim()
                        ?.take(64)
                    
                    if (!sanitized.isNullOfEmpty()) return sanitized
                }
            }
            defaultName
        } catch (_: Exception) {
            defaultName
        }
    }
}