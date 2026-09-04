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
}
