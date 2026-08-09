package com.kuzyamond.voidauditor.network

import com.kuzyamond.voidauditor.core.ShizukuExecutor
import kotlinx.coroutines.delay

object NetworkAdb {

    suspend fun enableWifiAdb(port: Int = 5555): Result<String> = try {
        val setPort = ShizukuExecutor.executeCommand("setprop service.adb.tcp.port $port")
        if (!setPort.isSuccessful) {
            Result.failure(Exception(setPort.error.ifBlank { "SETPROP_FAILED (code ${setPort.exitCode})" }))
        } else {
            ShizukuExecutor.executeCommand("stop adbd")
            ShizukuExecutor.executeCommand("start adbd")
            delay(800) // дать adbd время подняться после restart
            val verified = isWifiAdbEnabled(port)
            if (!verified) {
                Result.failure(
                    Exception("VERIFY_FAILED: setprop reported success but port $port not active per getprop")
                )
            } else {
                Result.success("ADB over WiFi enabled on port $port")
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun isWifiAdbEnabled(port: Int = 5555): Boolean {
        val res = ShizukuExecutor.executeCommand("getprop service.adb.tcp.port")
        return res.isSuccessful && res.output.trim() == port.toString()
    }
}
