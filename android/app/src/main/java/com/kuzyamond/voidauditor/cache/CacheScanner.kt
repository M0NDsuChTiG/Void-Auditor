package com.kuzyamond.voidauditor.cache

import com.kuzyamond.voidauditor.GlobalLog
import com.kuzyamond.voidauditor.ShizukuManager
import com.kuzyamond.voidauditor.cache.models.CacheEntry
import com.kuzyamond.voidauditor.cache.models.CacheStats
import com.kuzyamond.voidauditor.core.ActorType
import com.kuzyamond.voidauditor.core.AuditEvent
import com.kuzyamond.voidauditor.core.AuditLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CacheScanner {

    private const val TAG = "CACHE_SCAN"
    private const val MIN_DEPTH = 2
    private const val QUICK_MAX_DEPTH = 3
    private const val FULL_MAX_DEPTH = 4
    private const val DEEP_MAX_DEPTH = 4
    private val SUFFIX_NAME_REGEX = Regex("^[a-zA-Z0-9_]+$")

    private fun buildNameExpr(): String {
        val safe = PathSanitizer.SAFE_SUFFIX.filter { SUFFIX_NAME_REGEX.matches(it) }
        val quoted = safe.joinToString("") { """ -o -name "$it"""" }
        return """\( -false$quoted \)"""
    }

    suspend fun scan(capability: CacheCapability): CacheStats = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        AuditLogger.log(
            AuditEvent(
                actor = ActorType.SCRIPT,
                capability = "CACHE_SCAN:${capability.name}",
                riskLevel = capability.riskLevel,
                decision = "ALLOWED",
                details = "Starting ${capability.displayName}"
            )
        )
        GlobalLog.log("SCAN_START: ${capability.displayName}", "warn", TAG)

        val rawPaths = discoverCacheDirs(capability)
        val visiblePaths = PathSanitizer.sanitizeBatch(rawPaths, PathSanitizer.SanitizeMode.SCAN)
        GlobalLog.log("DISCOVERED: ${rawPaths.size} raw, ${visiblePaths.size} visible", "ok", TAG)

        val entries = mutableListOf<CacheEntry>()
        for (path in visiblePaths) {
            val entry = inspectDir(path) ?: continue
            val safeToDelete = PathSanitizer.isSafeToClean(path)
            entries.add(entry.copy(isSafeToDelete = safeToDelete))
        }

        val duration = System.currentTimeMillis() - startTime
        val byPackage = entries.groupBy { it.packageName }
        val totalSize = entries.sumOf { it.sizeBytes }
        val totalFiles = entries.sumOf { it.fileCount }
        val risk = when {
            entries.any { it.sizeBytes > 100_000_000L } -> com.kuzyamond.voidauditor.RiskLevel.HIGH
            entries.any { it.sizeBytes > 10_000_000L } -> com.kuzyamond.voidauditor.RiskLevel.MEDIUM
            else -> com.kuzyamond.voidauditor.RiskLevel.LOW
        }

        val installedPackages = ShizukuManager.executeCommand("pm list packages -3 2>/dev/null | wc -l")
            .getOrNull()?.trim()?.toIntOrNull() ?: 0

        val stats = CacheStats(
            totalSizeBytes = totalSize,
            totalEntries = entries.size,
            totalFiles = totalFiles,
            entriesByPackage = byPackage,
            scanDurationMs = duration,
            riskLevel = risk,
            installedPackages = installedPackages
        )

        AuditLogger.log(
            AuditEvent(
                actor = ActorType.SCRIPT,
                capability = "CACHE_SCAN:${capability.name}",
                riskLevel = risk,
                decision = "ALLOWED",
                details = "Found ${entries.size} dirs, ${formatSize(totalSize)} total"
            )
        )
        GlobalLog.log(
            "SCAN_DONE: ${entries.size} dirs, ${formatSize(totalSize)} in ${duration}ms",
            if (risk.ordinal >= com.kuzyamond.voidauditor.RiskLevel.HIGH.ordinal) "warn" else "ok",
            TAG
        )

        stats
    }

    private suspend fun discoverCacheDirs(capability: CacheCapability): List<String> = withContext(Dispatchers.IO) {
        val roots = when (capability) {
            CacheCapability.QUICK,
            CacheCapability.FULL -> listOf("/data/data", "/sdcard/Android/data")
            CacheCapability.DEEP -> listOf("/data/data", "/data/user_de/0", "/sdcard/Android/data")
            CacheCapability.SYSTEM_TRIM -> emptyList()
        }
        val maxDepth = when (capability) {
            CacheCapability.QUICK -> QUICK_MAX_DEPTH
            CacheCapability.FULL -> FULL_MAX_DEPTH
            CacheCapability.DEEP -> FULL_MAX_DEPTH
            CacheCapability.SYSTEM_TRIM -> 0
        }
        val nameExpr = buildNameExpr()
        val cmd = buildString {
            roots.forEachIndexed { i, root ->
                if (i > 0) append("\n")
                append("""find "$root" -mindepth $MIN_DEPTH -maxdepth $maxDepth -type d $nameExpr -prune 2>/dev/null""")
            }
        }
        val result = ShizukuManager.executeCommand(cmd)
            .getOrNull() ?: ""
        result.lines()
            .filter { it.isNotBlank() }
            .distinctBy { raw ->
                raw.replace("/data/user/0/", "/data/data/")
                    .replace("/storage/emulated/0/", "/sdcard/")
            }
    }

    private suspend fun inspectDir(path: String): CacheEntry? = withContext(Dispatchers.IO) {
        val cmd = """du -sb "$path" 2>/dev/null | cut -f1"""
        val countCmd = """find "$path" -type f 2>/dev/null | wc -l"""
        val modifiedCmd = """stat -c %Y "$path" 2>/dev/null"""

        val sizeBytes = ShizukuManager.executeCommand(cmd)
            .getOrNull()?.trim()?.toLongOrNull() ?: 0L

        val fileCount = ShizukuManager.executeCommand(countCmd)
            .getOrNull()?.trim()?.toIntOrNull() ?: 0

        val lastModified = ShizukuManager.executeCommand(modifiedCmd)
            .getOrNull()?.trim()?.toLongOrNull() ?: 0L

        val pkg = PathSanitizer.extractPackage(path) ?: "unknown"

        CacheEntry(
            path = path,
            packageName = pkg,
            sizeBytes = sizeBytes,
            fileCount = fileCount,
            lastModified = lastModified
        )
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}

