package com.kuzyamond.voidauditor.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkIdentityTest {

    @Test
    fun `local 24-bit subnet derives network and broadcast`() {
        val identity = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "192.168.1.77",
            prefix = 24,
            gateway = "192.168.1.1",
            isConnected = true
        )
        assertEquals("192.168.1.0", identity.network)
        assertEquals("192.168.1.255", identity.broadcast)
    }

    @Test
    fun `documentation 24-bit subnet derives network and broadcast`() {
        val identity = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "10.0.0.111",
            prefix = 24,
            gateway = "10.0.0.1",
            isConnected = true
        )
        assertEquals("10.0.0.0", identity.network)
        assertEquals("10.0.0.255", identity.broadcast)
    }

    @Test
    fun `cgNAT 10-bit subnet derives network and broadcast`() {
        val identity = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "100.70.42.5",
            prefix = 10,
            gateway = "100.64.0.1",
            isConnected = true
        )
        assertEquals("100.64.0.0", identity.network)
        assertEquals("100.127.255.255", identity.broadcast)
    }

    @Test
    fun `30-bit subnet derives network and broadcast`() {
        val identity = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "10.0.0.111",
            prefix = 30,
            gateway = "10.0.0.109",
            isConnected = true
        )
        assertEquals("10.0.0.108", identity.network)
        assertEquals("10.0.0.111", identity.broadcast)
    }

    @Test
    fun `invalid ipv4 yields empty network and broadcast`() {
        val identity = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "999.1.1.1",
            prefix = 24,
            gateway = "192.168.1.1",
            isConnected = true
        )
        assertEquals("", identity.network)
        assertEquals("", identity.broadcast)
    }

    @Test
    fun `prefix extremes produce correct masks`() {
        val prefixZero = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "192.168.1.77",
            prefix = 0,
            gateway = "",
            isConnected = true
        )
        assertEquals("0.0.0.0", prefixZero.network)
        assertEquals("255.255.255.255", prefixZero.broadcast)

        val prefixThirtyTwo = NetworkIdentity(
            interfaceName = "wlan0",
            ipv4 = "192.168.1.77",
            prefix = 32,
            gateway = "",
            isConnected = true
        )
        assertEquals("192.168.1.77", prefixThirtyTwo.network)
        assertEquals("192.168.1.77", prefixThirtyTwo.broadcast)
    }

    @Test
    fun `constructor fields are passed through`() {
        val identity = NetworkIdentity(
            interfaceName = "rndis0",
            ipv4 = "192.168.42.10",
            prefix = 24,
            gateway = "192.168.42.1",
            isConnected = false
        )
        assertEquals("rndis0", identity.interfaceName)
        assertEquals("192.168.42.10", identity.ipv4)
        assertEquals(24, identity.prefix)
        assertEquals("192.168.42.1", identity.gateway)
        assertTrue(!identity.isConnected)
    }
}