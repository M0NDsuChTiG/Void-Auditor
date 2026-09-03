package com.kuzyamond.voidauditor.network

import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import com.kuzyamond.voidauditor.core.USFPipeline

object NetworkAdb {

    suspend fun enableWifiAdb(port: Int = 5555): Result<String> = try {
        val result = CapabilityExecutor.execute(USFPipeline.Context(), Capability.ConfigureAdbTcp(port))
        if (result.commandResult.isSuccessful) {
            Result.success("ADB over WiFi enabled on port $port")
        } else {
            Result.failure(Exception(result.commandResult.error.ifBlank { "EXEC_FAILED (code ${result.commandResult.exitCode})" }))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun isWifiAdbEnabled(port: Int = 5555): Boolean {
        val result = CapabilityExecutor.execute(USFPipeline.Context(), Capability.ReadSystemProp("service.adb.tcp.port"))
        return result.commandResult.isSuccessful && result.commandResult.output.trim() == port.toString()
    }
}
