package com.kuzyamond.adbstudio

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import java.lang.reflect.Method

object ShizukuManager {

    fun isAvailable(): Boolean = try { Shizuku.pingBinder() } catch (e: Exception) { false }

    fun hasPermission(): Boolean {
        return if (isAvailable()) {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    fun isAuthorizedFlow(): Flow<Boolean> = flow {
        while(true) {
            emit(hasPermission())
            delay(3000)
        }
    }

    fun requestPermission(requestCode: Int = 0) {
        if (isAvailable()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    private var cachedMethod: Method? = null

    suspend fun executeCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        if (command.isBlank()) return@withContext Result.failure(Exception("EMPTY_COMMAND"))
        if (!isAvailable()) return@withContext Result.failure(Exception("SHIZUKU_OFFLINE"))
        if (!hasPermission()) return@withContext Result.failure(Exception("NO_PERMISSION"))

        try {
            if (cachedMethod == null) {
                cachedMethod = Shizuku::class.java.getDeclaredMethods().find { 
                    it.name == "newProcess" && it.parameterCount == 3 
                }?.apply { isAccessible = true }
            }
            val method = cachedMethod ?: throw Exception("REF_ERR")

            val cleanCmd = command.trim()
                .removePrefix("adb shell ")
                .removePrefix("adb -s R9HN50GZL4J shell ")

            // Запускаем через SH по умолчанию
            val process = method.invoke(null, arrayOf("sh", "-c", cleanCmd), null, null) as Process
            
            // Читаем вывод побайтово (самый надежный способ)
            val output = readStreamFully(process)
            val exitCode = process.waitFor()

            if (exitCode == 0 || output.isNotEmpty()) {
                Result.success(output.ifEmpty { "SUCCESS (EXIT 0)" })
            } else {
                val err = readErrorStream(process)
                Result.failure(Exception("CODE_$exitCode: $err"))
            }
        } catch (e: Exception) {
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
            Result.failure(Exception("FATAL: ${cause.message}"))
        }
    }

    private fun readStreamFully(process: Process): String {
        val out = ByteArrayOutputStream()
        try {
            val stream = process.inputStream
            val buffer = ByteArray(1024)
            var length: Int
            while (stream.read(buffer).also { length = it } != -1) {
                out.write(buffer, 0, length)
            }
        } catch (e: Exception) {}
        return out.toString().trim()
    }

    private fun readErrorStream(process: Process): String {
        return try { process.errorStream.readBytes().decodeToString().trim() } catch (e: Exception) { "" }
    }
}
