package com.kuzyamond.voidauditor

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import com.kuzyamond.voidauditor.core.ShizukuExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppManagerSyncTest {

    @Test
    fun `packageQueries use the enabled and disabled dash flags`() {
        val queries = AppManagerSync.packageQueries()
        assertEquals(2, queries.size)
        assertEquals(Capability.QueryPackages("-e"), queries[0])
        assertEquals(Capability.QueryPackages("-d"), queries[1])
    }

    @Test
    fun `query capabilities map to pm list packages with dash flags`() {
        assertEquals(
            "pm list packages -e",
            CapabilityExecutor.capabilityToCommand(Capability.QueryPackages("-e"))
        )
        assertEquals(
            "pm list packages -d",
            CapabilityExecutor.capabilityToCommand(Capability.QueryPackages("-d"))
        )
    }

    @Test
    fun `buildAppInfo maps enabled and disabled output to statuses`() {
        val info = AppManagerSync.buildAppInfo(
            enabledOutput = "package:com.example.one\npackage:com.example.two\n",
            disabledOutput = "package:com.example.off\n"
        )
        assertEquals(3, info.size)
        assertEquals(AppStatus.WORKING, info.first { it.packageName == "com.example.one" }.status)
        assertEquals(AppStatus.WORKING, info.first { it.packageName == "com.example.two" }.status)
        assertEquals(AppStatus.DISABLED, info.first { it.packageName == "com.example.off" }.status)
    }

    @Test
    fun `buildAppInfo skips lines without package prefix`() {
        val info = AppManagerSync.buildAppInfo(
            enabledOutput = "package:com.example.one\nWARNING: junk line\n",
            disabledOutput = ""
        )
        assertEquals(1, info.size)
        assertEquals("com.example.one", info[0].packageName)
    }

    @Test
    fun `refresh queries enabled and disabled and builds the combined list`() = runBlocking {
        val results: Map<Capability, ShizukuExecutor.CommandResult> = mapOf(
            Capability.QueryPackages("-e") to ShizukuExecutor.CommandResult(
                success = true, output = "package:com.example.one\n", error = "", exitCode = 0, executionTimeMs = 1
            ),
            Capability.QueryPackages("-d") to ShizukuExecutor.CommandResult(
                success = true, output = "package:com.example.off\n", error = "", exitCode = 0, executionTimeMs = 1
            )
        )
        val list = AppManagerSync.refresh { results.getValue(it) }
        assertEquals(2, list.size)
        assertEquals(
            setOf("com.example.one", "com.example.off"),
            list.map { it.packageName }.toSet()
        )
        assertEquals(AppStatus.WORKING, list.first { it.packageName == "com.example.one" }.status)
        assertEquals(AppStatus.DISABLED, list.first { it.packageName == "com.example.off" }.status)
    }

    @Test
    fun `status groups contain no permanently empty FROZEN section`() {
        val info = AppManagerSync.buildAppInfo("package:com.example.one\n", "")
        val grouped = AppManagerSync.groupByStatus(info)
        assertFalse("FROZEN must not be a fake always-zero section", grouped.containsKey("FROZEN"))
        assertEquals(1, grouped["RUNNING"]?.size)
        assertTrue(grouped["DISABLED"]?.isEmpty() == true)
    }
}