package com.kuzyamond.voidauditor

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import com.kuzyamond.voidauditor.network.NetworkScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectScreenCapabilityTest {

    // ── riskScore ──────────────────────────────────────────────

    @Test
    fun `AdbConnect has risk score 50`() {
        val cap = Capability.AdbConnect("192.168.1.100", 5555)
        assertEquals(50, cap.riskScore)
    }

    @Test
    fun `AdbScanDevices has risk score 10`() {
        val cap = Capability.AdbScanDevices
        assertEquals(10, cap.riskScore)
    }

    // ── riskScore (confirmation threshold) ────────────────────────

    @Test
    fun `AdbConnect risk score is 50 (ACTION tier, confirmation expected)`() {
        val cap = Capability.AdbConnect("192.168.1.100", 5555)
        assertEquals(50, cap.riskScore)
    }

    @Test
    fun `AdbScanDevices risk score is 10 (READ tier, no confirmation)`() {
        val cap = Capability.AdbScanDevices
        assertEquals(10, cap.riskScore)
    }

    // ── capabilityToCommand ────────────────────────────────────

    @Test
    fun `AdbConnect command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.AdbConnect("192.168.1.100", 5555))
        assertEquals("adb connect 192.168.1.100:5555", cmd)
    }

    @Test
    fun `AdbConnect command with different ip and port`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.AdbConnect("10.0.0.5", 5037))
        assertEquals("adb connect 10.0.0.5:5037", cmd)
    }

    @Test
    fun `AdbScanDevices command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.AdbScanDevices)
        assertEquals("adb devices", cmd)
    }

    // ── non-null checks ───────────────────────────────────────

    @Test
    fun `AdbConnect does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.AdbConnect("192.168.1.100", 5555))
        assertFalse("capabilityToCommand must not return null for AdbConnect", cmd.isNullOrEmpty())
    }

    @Test
    fun `AdbScanDevices does not return null`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.AdbScanDevices)
        assertFalse("capabilityToCommand must not return null for AdbScanDevices", cmd.isNullOrEmpty())
    }

    // ── batch non-null ────────────────────────────────────────

    @Test
    fun `capabilityToCommand returns non-null for all ConnectScreen capabilities`() {
        val capabilities = listOf(
            Capability.AdbConnect("192.168.1.100", 5555),
            Capability.AdbScanDevices
        )
        capabilities.forEach { cap ->
            val cmd = CapabilityExecutor.capabilityToCommand(cap)
            assertFalse(
                "capabilityToCommand returned null for ${cap::class.simpleName}",
                cmd.isNullOrEmpty()
            )
        }
    }

    // ── Network Discovery: PingIp / PingSweep (Stage 3) ─────────
    // scanHosts теперь ходит per-host через Capability.PingIp (вместо xargs
    // PingSweep) — см. NetworkScanner.scanHosts. Тут проверяется точная форма
    // команды, генерируемой CommandMapper, и riskScore.

    @Test
    fun `PingIp command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(Capability.PingIp("192.168.1.100"))
        assertEquals(
            "ping -c 1 -W 1 \"192.168.1.100\" >/dev/null 2>&1 && echo \"192.168.1.100\"",
            cmd
        )
    }

    @Test
    fun `PingSweep command is correct`() {
        val cmd = CapabilityExecutor.capabilityToCommand(
            Capability.PingSweep(listOf("192.168.1.1", "192.168.1.2"))
        )
        assertEquals(
            "printf '%s\\n' 192.168.1.1 192.168.1.2 | xargs -P 16 -I {} sh -c 'ping -c 1 -W 1 \"\$1\" >/dev/null 2>&1 && echo \"\$1\"' _ {}",
            cmd
        )
    }

    @Test
    fun `PingIp and PingSweep have risk score 30`() {
        assertEquals(30, Capability.PingIp("192.168.1.1").riskScore)
        assertEquals(30, Capability.PingSweep(listOf("192.168.1.1")).riskScore)
    }

    @Test
    fun `PingIp injection payload is rejected by the isValidIpv4 gate`() {
        // CommandMapper интерполирует IP без валидации — единственная защита на
        // пути сканирования это isValidIpv4-фильтр в NetworkScanner.scanHosts
        // (до создания Capability.PingIp). Убеждаемся, что гейт отклоняет любые
        // инъекции, способные сломать "ping -c 1 -W 1 \"<ip>\" ...".
        val malicious = listOf(
            "192.168.1.1;rm -rf /",
            "192.168.1.1;touch /sdcard/pwned",
            "192.168.1.1\$(whoami)",
            "192.168.1.1`id`",
            "192.168.1.1/24",
            "192.168.1.999",
            "\"192.168.1.1\"",
            "192.168.1.1 && echo pwned"
        )
        for (input in malicious) {
            assertFalse("Должен быть отклонён гейтом: $input", NetworkScanner.isValidIpv4(input))
        }
    }
}
