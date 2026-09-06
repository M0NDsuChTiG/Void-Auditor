package com.kuzyamond.voidauditor.core.evidence

/**
 * File/cache-related evidence from capabilities like DiscoverCacheDirectories,
 * ReadDiskUsage, ReadDirectorySize, ReadFileCount, ReadLastModified.
 */
sealed interface FileEvidence : Evidence

data class CacheDirectoriesEvidence(
    override val capabilityId: String = "DiscoverCacheDirectories",
    override val capturedAt: Long = System.currentTimeMillis(),
    val paths: List<String>
) : FileEvidence

data class DiskUsageEvidence(
    override val capabilityId: String = "ReadDiskUsage",
    override val capturedAt: Long = System.currentTimeMillis(),
    val kilobytes: Long?
) : FileEvidence

data class DirectorySizeEvidence(
    override val capabilityId: String = "ReadDirectorySize",
    override val capturedAt: Long = System.currentTimeMillis(),
    val bytes: Long?
) : FileEvidence

data class FileCountEvidence(
    override val capabilityId: String = "ReadFileCount",
    override val capturedAt: Long = System.currentTimeMillis(),
    val count: Int?
) : FileEvidence

data class LastModifiedEvidence(
    override val capabilityId: String = "ReadLastModified",
    override val capturedAt: Long = System.currentTimeMillis(),
    val timestamp: Long?
) : FileEvidence
