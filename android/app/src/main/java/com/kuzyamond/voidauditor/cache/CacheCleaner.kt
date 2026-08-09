package com.kuzyamond.voidauditor.cache

import com.kuzyamond.voidauditor.GlobalLog
import com.kuzyamond.voidauditor.RiskLevel
import com.kuzyamond.voidauditor.core.ActorType
import com.kuzyamond.voidauditor.core.AuditEvent
import com.kuzyamond.voidauditor.core.AuditLogger
import com.kuzyamond.voidauditor.core.ShizukuExecutor
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

    suspend fun clean(
        paths: List<String>,
        capability: CacheCapability,
        dryRun: Boolean = true
    ): CleanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val sanitized = PathSanitizer.sanitizeBatch(paths, PathSanitizer.SanitizeMode.PURGE)

        AuditLogger.log(
            AuditEvent(
                actor = ActorType.SCRIPT,
                capability = "CACHE_CLEAN",
                riskLevel = RiskLevel.TIER_1_REVERSIBLE,
                decision = "ALLOWED",
                details = "${if (dryRun) "DRY_RUN" else "PURGE"}: ${sanitized.size} dirs, capability=${capability.name}"
            )
        )

        if (dryRun) calculateDryRun(sanitized, startTime) else executePurge(sanitized, startTime)
    }

    suspend fun systemTrim(freeBytesHint: String = "500M"): CleanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val cmd = "pm trim-caches $freeBytesHint"

        AuditLogger.log(
            AuditEvent(
                actor = ActorType.SCRIPT,
                capability = "SYSTEM_TRIM",
                riskLevel = RiskLevel.TIER_1_REVERSIBLE,
                decision = "ALLOWED",
                details = "System trim with hint $freeBytesHint"
            )
        )
        GlobalLog.log("SYSTEM_TRIM $freeBytesHint", "ok", TAG)

        val dfCmd = "df -k /data 2>/dev/null | tail -1 | awk '{print $(NF-2)}'"
        val beforeKb = ShizukuExecutor.executeCommand(dfCmd).output.trim().toLongOrNull() ?: 0L
        val result = ShizukuExecutor.executeCommand(cmd)
        val afterKb = ShizukuExecutor.executeCommand(dfCmd).output.trim().toLongOrNull() ?: 0L
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
            val cmd = """du -sb "$path" 2>/dev/null | cut -f1"""
            val countCmd = """find "$path" -type f 2>/dev/null | wc -l"""

            val size = ShizukuExecutor.executeCommand(cmd).run {
                if (isSuccessful) output.trim().toLongOrNull() ?: 0L else 0L
            }
            val files = ShizukuExecutor.executeCommand(countCmd).run {
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

        AuditLogger.log(
            AuditEvent(
                actor = ActorType.SCRIPT,
                capability = "CACHE_CLEAN:DRY_RESULT",
                riskLevel = RiskLevel.LOW,
                decision = "ALLOWED",
                details = "Dry-run: $totalFiles files, ${formatBytes(totalBytes)} in ${succeeded.size} dirs"
            )
        )
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
            val sizeBefore = ShizukuExecutor.executeCommand(
                """du -sb "$path" 2>/dev/null | cut -f1"""
            ).run {
                if (isSuccessful) output.trim().toLongOrNull() ?: 0L else 0L
            }

            val safeCmd = PathSanitizer.safeCleanCommand(path)
            if (safeCmd == null) {
                val reason = PathSanitizer.blockedReason(path)
                errors.add("BLOCKED:$path:$reason")
                GlobalLog.log("BLOCKED: $path → $reason", "crit", TAG)
                continue
            }

            val result = ShizukuExecutor.executeCommand(safeCmd)
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
        val risk = when {
            errors.isNotEmpty() -> RiskLevel.CRITICAL
            totalFreed > 100_000_000L -> RiskLevel.HIGH
            totalFreed > 10_000_000L -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        AuditLogger.log(
            AuditEvent(
                actor = ActorType.SCRIPT,
                capability = "CACHE_CLEAN:PURGE_RESULT",
                riskLevel = risk,
                decision = "ALLOWED",
                details = "Purged ${succeeded.size} dirs, freed ${formatBytes(totalFreed)}, ${errors.size} errors"
            )
        )
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

