package com.kuzyamond.voidauditor.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkProfileDetectorTest {

    @Test
    fun `connected 24-bit identity resolves scan scope`() {
        val identity = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "192.168.1.77",
            prefix = 24,
            gateway = "192.168.1.1",
            isConnected = true
        )
        val scope = NetworkProfileDetector.resolveScanScope(identity)
        assertNotNull(scope)
        assertEquals("192.168.1.77", scope?.localIp)
        assertEquals(24, scope?.prefix)
        assertEquals("192.168.1.0", scope?.network)
        assertEquals("192.168.1.255", scope?.broadcast)
    }

    @Test
    fun `connected 4-bit prefix is the lower boundary`() {
        val identity = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "192.168.1.77",
            prefix = 4,
            gateway = "",
            isConnected = true
        )
        val scope = NetworkProfileDetector.resolveScanScope(identity)
        assertNotNull(scope)
        assertEquals("192.0.0.0", scope?.network)
        assertEquals("207.255.255.255", scope?.broadcast)
    }

    @Test
    fun `connected 30-bit prefix is the upper boundary`() {
        val identity = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "192.168.1.77",
            prefix = 30,
            gateway = "",
            isConnected = true
        )
        val scope = NetworkProfileDetector.resolveScanScope(identity)
        assertNotNull(scope)
        assertEquals("192.168.1.76", scope?.network)
        assertEquals("192.168.1.79", scope?.broadcast)
    }

    @Test
    fun `disconnected identity yields null scope`() {
        val identity = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "192.168.1.77",
            prefix = 24,
            gateway = "192.168.1.1",
            isConnected = false
        )
        assertNull(NetworkProfileDetector.resolveScanScope(identity))
    }

    @Test
    fun `invalid ipv4 yields null scope`() {
        val identity = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "999.1.1.1",
            prefix = 24,
            gateway = "",
            isConnected = true
        )
        assertNull(NetworkProfileDetector.resolveScanScope(identity))
    }

    @Test
    fun `empty ipv4 yields null scope`() {
        val identity = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "",
            prefix = 24,
            gateway = "",
            isConnected = true
        )
        assertNull(NetworkProfileDetector.resolveScanScope(identity))
    }

    @Test
    fun `prefix below 4 yields null scope`() {
        val identity = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "192.168.1.77",
            prefix = 3,
            gateway = "",
            isConnected = true
        )
        assertNull(NetworkProfileDetector.resolveScanScope(identity))
    }

    @Test
    fun `prefix above 30 yields null scope`() {
        val identity31 = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "192.168.1.77",
            prefix = 31,
            gateway = "",
            isConnected = true
        )
        assertNull(NetworkProfileDetector.resolveScanScope(identity31))

        val identity32 = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "192.168.1.77",
            prefix = 32,
            gateway = "",
            isConnected = true
        )
        assertNull(NetworkProfileDetector.resolveScanScope(identity32))
    }

    @Test
    fun `blank interface name yields null scope`() {
        val identity = NetworkIdentity(
            interfaceName = "",
            ipv4 = "192.168.1.77",
            prefix = 24,
            gateway = "192.168.1.1",
            isConnected = true
        )
        assertNull(NetworkProfileDetector.resolveScanScope(identity))
    }
}