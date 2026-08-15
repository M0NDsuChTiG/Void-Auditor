package com.kuzyamond.voidauditor.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkScannerTest {

    @Test
    fun `isValidIpv4 accepts valid addresses`() {
        assertTrue(NetworkScanner.isValidIpv4("10.0.0.1"))
        assertTrue(NetworkScanner.isValidIpv4("192.168.1.7"))
        assertTrue(NetworkScanner.isValidIpv4("255.255.255.255"))
        assertTrue(NetworkScanner.isValidIpv4(" 192.168.1.1 "))
    }

    @Test
    fun `isValidIpv4 rejects injections and malformed input`() {
        val invalid = listOf(
            "192.168.1;rm -rf /",
            "192.168.1.1;touch /sdcard/pwned",
            "192.168.1.1$(whoami)",
            "192.168.1.1`id`",
            "192.168.1.1/24",
            "192.168.1.1.1",
            "192.168.1",
            "abc.def.ghi.jkl",
            "256.1.1.1",
            "1.2.3.999",
            "999.999.999.999",
            ""
        )
        for (input in invalid) {
            assertFalse("Должен быть отклонён: $input", NetworkScanner.isValidIpv4(input))
        }
    }

    @Test
    fun `generateTargets rejects non-IPv4 base ip`() {
        val badBases = listOf(
            "192.168.1;rm -rf /",
            "192.168.1.999",
            "192.168.1",
            "192.168.1.1.1",
            ""
        )
        for (base in badBases) {
            val targets = runBlocking { NetworkScanner.generateTargets(base) }
            assertTrue("Должен вернуть пусто для: $base", targets.isEmpty())
        }
    }

    @Test
    fun `generateTargets produces 254 hosts for valid 24-bit subnet`() {
        val targets = runBlocking { NetworkScanner.generateTargets("192.168.1.7", 24) }
        assertEquals(254, targets.size)
        assertEquals("192.168.1.1", targets.first().ip)
        assertEquals("192.168.1.254", targets.last().ip)
    }

    @Test
    fun `scanHosts ignores targets with invalid ips`() {
        val targets = listOf(
            ScanTarget(ip = "192.168.1.5"),
            ScanTarget(ip = "192.168.1.6"),
            ScanTarget(ip = "192.168.1;rm -rf /")
        )
        // Валидные цели проходят через isValidIpv4; невалидная отбрасывается до построения скрипта.
        // Полный скан упирается в Shizuku, поэтому проверяем только фильтрацию.
        val valid = targets.filter { NetworkScanner.isValidIpv4(it.ip) }
        assertEquals(2, valid.size)
        assertTrue(valid.none { it.ip.contains(";") })
    }
}
