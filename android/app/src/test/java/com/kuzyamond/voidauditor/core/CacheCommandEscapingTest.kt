package com.kuzyamond.voidauditor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the shell-command builders used by the CACHE scanner.
 *
 * Regression: commit 37e78a1 built the discover command inside a Kotlin *raw*
 * string as `-name \"cache\"`. Raw strings do NOT process escape sequences, so
 * the backslash-quote reached `sh -c` as a literal `\` + `"`, making find look
 * for a directory literally named `"cache"` (with quote characters) — 0 dirs.
 * The emitted command must contain a plain `-name "cache"`.
 */
class CacheCommandEscapingTest {

    @Test
    fun discoverCommandUsesPlainQuotesAroundCacheName() {
        val cmd = CapabilityExecutor.capabilityToCommand(
            Capability.DiscoverCacheDirectories(listOf("/data/data"), 4)
        )

        assertTrue("must quote -name value with plain quotes: $cmd", cmd.contains("-name \"cache\""))
        assertFalse("must NOT contain backslash-escaped quotes: $cmd", cmd.contains("\\\"cache\\\""))
        // sh -c sees escaped quotes as literal quote characters → find matches nothing
        assertFalse("no backslash-quote sequences allowed: $cmd", cmd.contains("\\\""))
    }

    @Test
    fun discoverCommandCoversAllRoots() {
        val cmd = CapabilityExecutor.capabilityToCommand(
            Capability.DiscoverCacheDirectories(listOf("/data/data", "/sdcard/Android/data"), 4)
        )
        assertTrue(cmd.contains("find \"/data/data\""))
        assertTrue(cmd.contains("find \"/sdcard/Android/data\""))
    }

    @Test
    fun directorySizeCommandQuotesPathWithPlainQuotes() {
        val cmd = CapabilityExecutor.capabilityToCommand(
            Capability.ReadDirectorySize("/data/data/com.example/cache")
        )
        assertEquals("du -sb \"/data/data/com.example/cache\" 2>/dev/null | cut -f1", cmd)
    }

    @Test
    fun pingIpCommandProducesExactBytes() {
        val cmd = CapabilityExecutor.capabilityToCommand(
            Capability.PingIp("13.13.213.111")
        )
        // Only isValidIpv4-validated IPs reach sh from scanHosts(), so the IP is
        // embedded inline (dots/digits only); quoted to match the builder's defensive
        // quoting convention for inline values. Assert the exact byte sequence sh sees.
        val expected = "ping -c 1 -W 1 \"13.13.213.111\" >/dev/null 2>&1 && echo \"13.13.213.111\""
        assertEquals("PingIp command bytes must be exact", expected, cmd)

        // Must not leak interpolation artefacts into the shell.
        assertFalse("no dollar-brace artefacts: $cmd", cmd.contains("\${"))
        assertFalse("single command only, no raw LF allowed: $cmd", cmd.contains("\n"))
    }
}
