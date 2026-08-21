package com.kuzyamond.voidauditor.network

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor

object NetworkAdb {

    suspend fun enableWifiAdb(port: Int = 5555): Result<String> = try {
        val result = CapabilityExecutor.execute(Capability.ConfigureAdbTcp(port))
        if (result.success) {
            Result.success("ADB over WiFi enabled on port $port")
        } else {
            Result.failure(Exception(result.error.ifBlank { "EXEC_FAILED (code ${result.exitCode})" }))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun isWifiAdbEnabled(port: Int = 5555): Boolean {
        val result = CapabilityExecutor.execute(Capability.ReadSystemProp("service.adb.tcp.port"))
        return result.success && result.output.trim() == port.toString()
    }
}
