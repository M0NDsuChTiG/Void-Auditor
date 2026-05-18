package com.kuzyamond.adbstudio.core

import com.kuzyamond.adbstudio.GlobalLog
import com.kuzyamond.adbstudio.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShizukuExecutor {

    var logListener: ((type: String, message: String) -> Unit)? = null

    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int,
        val executionTimeMs: Long
    ) {
        val isSuccessful: Boolean get() = success && exitCode == 0
        val fullOutput: String
            get() = buildString {
                if (output.isNotBlank()) appendLine(output.trim())
                if (error.isNotBlank()) {
                    appendLine("\n--- STDERR ---")
                    appendLine(error.trim())
                }
            }.trim()
    }

    fun init() {
        logListener = { type, msg ->
            GlobalLog.log("[$type] $msg")
        }
    }

    suspend fun executeCommand(
        command: String,
        timeoutMs: Long = 45000L
    ): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        logListener?.invoke("CMD", command.trim())

        val result = try {
            ShizukuManager.executeCommand(command)
        } catch (e: Exception) {
            val err = e.localizedMessage ?: "Unknown error"
            logListener?.invoke("ERROR", err)
            return@withContext CommandResult(
                success = false, output = "", error = err,
                exitCode = -1, executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val execTime = System.currentTimeMillis() - startTime
        val output = result.getOrNull() ?: ""
        val error = result.exceptionOrNull()?.localizedMessage ?: ""
        val exitCode = if (result.isSuccess) 0 else -1

        val cmdResult = CommandResult(
            success = result.isSuccess, output = output, error = error,
            exitCode = exitCode, executionTimeMs = execTime
        )

        logListener?.invoke("RESULT", "Exit: $exitCode | ${execTime}ms")
        if (error.isNotBlank()) logListener?.invoke("STDERR", error)
        cmdResult
    }

    suspend fun executeBatch(commands: List<String>): List<CommandResult> =
        commands.map { executeCommand(it) }
}
