package com.kuzyamond.voidauditor.cache

import com.kuzyamond.voidauditor.GlobalLog
import com.kuzyamond.voidauditor.core.ActorType
import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import com.kuzyamond.voidauditor.core.USFPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CleanResult(
    val cleanedDirs: Set<String>,
    val deletedFiles: Long,
    val freedBytes: Long,
    val errors: List<String>,
    val durationMs: Long,
    val dryRun: Boolean
)

object CacheCleaner {

    private const val TAG = "CACHE_CLEAN"
    private val pipelineContext = USFPipeline.Context(actor = ActorType.SCRIPT)

    suspend fun clean(
        paths: List<String>,
        capability: CacheCapability,
        dryRun: Boolean = true
    ): CleanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val sanitized = PathSanitizer.sanitizeBatch(paths, PathSanitizer.SanitizeMode.PURGE)

        if (dryRun) calculateDryRun(sanitized, startTime) else executePurge(sanitized, startTime)
    }

    suspend fun systemTrim(freeBytesHint: String = "500M"): CleanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        GlobalLog.log("SYSTEM_TRIM $freeBytesHint", "ok", TAG)

        val beforeKb = CapabilityExecutor.execute(
            pipelineContext, Capability.ReadDiskUsage("/data")
        ).commandResult.output.trim().toLongOrNull() ?: 0L
        val result = CapabilityExecutor.execute(
            pipelineContext, Capability.ExecuteSystemTrim(freeBytesHint)
        ).commandResult
        val afterKb = CapabilityExecutor.execute(
            pipelineContext, Capability.ReadDiskUsage("/data")
        ).commandResult.output.trim().toLongOrNull() ?: 0L
        val duration = System.currentTimeMillis() - startTime

        if (result.isSuccessful) {
            GlobalLog.log("SYSTEM_TRIM ok: ${result.output}", "ok", TAG)
            CleanResult(
                cleanedDirs = setOf("system"),
                deletedFiles = 0,
                freedBytes = ((beforeKb - afterKb).coerceAtLeast(0L)) * 1024L,
                errors = emptyList(),
                durationMs = duration,
                dryRun = false
            )
        } else {
            val errMsg = result.error.ifBlank { "exit code ${result.exitCode}" }
            GlobalLog.log("SYSTEM_TRIM fail: $errMsg", "crit", TAG)
            CleanResult(
                cleanedDirs = emptySet(),
                deletedFiles = 0,
                freedBytes = 0,
                errors = listOf(errMsg),
                durationMs = duration,
                dryRun = false
            )
        }
    }

    private suspend fun calculateDryRun(
        paths: List<String>,
        startTime: Long
    ): CleanResult = withContext(Dispatchers.IO) {
        var totalFiles = 0L
        var totalBytes = 0L
        val succeeded = mutableSetOf<String>()
        val errors = mutableListOf<String>()

        for (path in paths) {
            val size = CapabilityExecutor.execute(
                pipelineContext, Capability.ReadDirectorySize(path)
            ).commandResult.run {
                if (isSuccessful) output.trim().toLongOrNull() ?: 0L else 0L
            }
            val files = CapabilityExecutor.execute(
                pipelineContext, Capability.ReadFileCount(path)
            ).commandResult.run {
                if (isSuccessful) output.trim().toIntOrNull() ?: 0 else 0
            }

            if (size > 0 || files > 0) {
                totalFiles += files
                totalBytes += size
                succeeded.add(path)
            } else {
                succeeded.add(path)
            }
        }

        val duration = System.currentTimeMillis() - startTime

        GlobalLog.log(
            "DRY_RUN: $totalFiles files, ${formatBytes(totalBytes)} in ${succeeded.size} dirs",
            "ok", TAG
        )

        CleanResult(
            cleanedDirs = succeeded,
            deletedFiles = totalFiles,
            freedBytes = totalBytes,
            errors = errors,
            durationMs = duration,
            dryRun = true
        )
    }

    private suspend fun executePurge(
        paths: List<String>,
        startTime: Long
    ): CleanResult = withContext(Dispatchers.IO) {
        var totalDeleted = 0L
        var totalFreed = 0L
        val succeeded = mutableSetOf<String>()
        val errors = mutableListOf<String>()

        for (path in paths) {
            val sizeBefore = CapabilityExecutor.execute(
                pipelineContext, Capability.ReadDirectorySize(path)
            ).commandResult.run {
                if (isSuccessful) output.trim().toLongOrNull() ?: 0L else 0L
            }

            val safeCmd = PathSanitizer.safeCleanCommand(path)
            if (safeCmd == null) {
                val reason = PathSanitizer.blockedReason(path)
                errors.add("BLOCKED:$path:$reason")
                GlobalLog.log("BLOCKED: $path → $reason", "crit", TAG)
                continue
            }

            val result = CapabilityExecutor.execute(
                pipelineContext, Capability.CleanCache(path, safeCmd)
            ).commandResult
            if (result.isSuccessful) {
                succeeded.add(path)
                totalDeleted += sizeBefore.toInt() / 1024 + 1
                totalFreed += sizeBefore
            } else {
                val errMsg = result.error.ifBlank { "exit code ${result.exitCode}" }
                errors.add("FAILED:$path:$errMsg")
                GlobalLog.log("FAILED: $path → $errMsg", "crit", TAG)
            }
        }

        val duration = System.currentTimeMillis() - startTime

        GlobalLog.log(
            "PURGE: ${succeeded.size} dirs cleaned, ${formatBytes(totalFreed)} freed, ${errors.size} errors in ${duration}ms",
            if (errors.isNotEmpty()) "crit" else "ok", TAG
        )

        CleanResult(
            cleanedDirs = succeeded,
            deletedFiles = totalDeleted,
            freedBytes = totalFreed,
            errors = errors,
            durationMs = duration,
            dryRun = false
        )
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
