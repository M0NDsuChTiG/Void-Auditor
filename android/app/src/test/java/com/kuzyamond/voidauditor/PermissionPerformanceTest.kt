package com.kuzyamond.voidauditor

import com.kuzyamond.voidauditor.core.PermissionAuditEngine
import com.kuzyamond.voidauditor.core.AppPermissionAudit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.system.measureTimeMillis

class PermissionPerformanceTest {

    @Test
    fun testBuildAuditsPerformance() {
        // Создаем фиктивный набор данных (например, 100 пакетов с разным dumpsys)
        val dummyResults = List(100) { i ->
            "pkg.$i" to "requested permissions: android.permission.CAMERA: granted=true\nversionName=1.0"
        }
        val systemSet = emptySet<String>()

        val time = measureTimeMillis {
            val audits = PermissionAuditEngine.buildAudits(dummyResults, systemSet)
            assertEquals(100, audits.size)
        }

        println("Performance test: 100 packages parsed in ${time}ms")
        assertTrue(time < 2000) { "Parsing 100 packages took too long: ${time}ms" }
    }
}
