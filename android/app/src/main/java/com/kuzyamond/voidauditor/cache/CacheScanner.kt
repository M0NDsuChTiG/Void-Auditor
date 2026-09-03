package com.kuzyamond.voidauditor.cache

import com.kuzyamond.voidauditor.GlobalLog
import com.kuzyamond.voidauditor.cache.models.CacheEntry
import com.kuzyamond.voidauditor.cache.models.CacheStats
import com.kuzyamond.voidauditor.core.ActorType
import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.CapabilityExecutor
import com.kuzyamond.voidauditor.core.USFPipeline
import com.kuzyamond.voidauditor.core.CacheDirectoriesEvidence
import com.kuzyamond.voidauditor.core.PackageCountEvidence
import com.kuzyamond.voidauditor.core.DirectorySizeEvidence
import com.kuzyamond.voidauditor.core.FileCountEvidence
import com.kuzyamond.voidauditor.core.LastModifiedEvidence
import com.kuzyamond.voidauditor.core.EvidenceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CacheScanner {

    private const val TAG = "CACHE_SCAN"
    private const val MIN_DEPTH = 2
    private const val QUICK_MAX_DEPTH = 3
    private const val FULL_MAX_DEPTH = 4
    private const val DEEP_MAX_DEPTH = 4
    private val SUFFIX_NAME_REGEX = Regex("^[a-zA-Z0-9_]+$")
    private val pipelineContext = USFPipeline.Context(actor = ActorType.SCRIPT)

    private fun buildNameExpr(): String {
        val safe = PathSanitizer.SAFE_SUFFIX.filter { SUFFIX_NAME_REGEX.matches(it) }
        val quoted = safe.joinToString("") { """ -o -name "$it"""" }
        return """\( -false$quoted \)"""
    }

    suspend fun scan(capability: CacheCapability): CacheStats = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
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

        val installedPackagesResult = CapabilityExecutor.execute(
            pipelineContext, Capability.ReadPackageCount
        )
        val installedPackagesEvidence = (installedPackagesResult.evidence as? EvidenceResult.Parsed)?.evidence as? PackageCountEvidence
        val installedPackages = installedPackagesEvidence?.count ?: installedPackagesResult.commandResult.output.trim().toIntOrNull() ?: 0

        val stats = CacheStats(
            totalSizeBytes = totalSize,
            totalEntries = entries.size,
            totalFiles = totalFiles,
            entriesByPackage = byPackage,
            scanDurationMs = duration,
            riskLevel = risk,
            installedPackages = installedPackages
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
        val result = CapabilityExecutor.execute(
            pipelineContext, Capability.DiscoverCacheDirectories(roots, maxDepth)
        )
        val evidence = (result.evidence as? EvidenceResult.Parsed)?.evidence as? CacheDirectoriesEvidence
        val paths = evidence?.paths ?: result.commandResult.output.lines()
            .filter { it.isNotBlank() }
            .distinctBy { raw ->
                raw.replace("/data/user/0/", "/data/data/")
                    .replace("/storage/emulated/0/", "/sdcard/")
            }
        paths
    }

    private suspend fun inspectDir(path: String): CacheEntry? = withContext(Dispatchers.IO) {
        val sizeResult = CapabilityExecutor.execute(pipelineContext, Capability.ReadDirectorySize(path))
        val sizeEvidence = (sizeResult.evidence as? EvidenceResult.Parsed)?.evidence as? DirectorySizeEvidence
        val sizeBytes = sizeEvidence?.bytes ?: sizeResult.commandResult.output.trim().toLongOrNull() ?: 0L

        val fileResult = CapabilityExecutor.execute(pipelineContext, Capability.ReadFileCount(path))
        val fileEvidence = (fileResult.evidence as? EvidenceResult.Parsed)?.evidence as? FileCountEvidence
        val fileCount = fileEvidence?.count ?: fileResult.commandResult.output.trim().toIntOrNull() ?: 0

        val modifiedResult = CapabilityExecutor.execute(pipelineContext, Capability.ReadLastModified(path))
        val modifiedEvidence = (modifiedResult.evidence as? EvidenceResult.Parsed)?.evidence as? LastModifiedEvidence
        val lastModified = modifiedEvidence?.timestamp ?: modifiedResult.commandResult.output.trim().toLongOrNull() ?: 0L

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

