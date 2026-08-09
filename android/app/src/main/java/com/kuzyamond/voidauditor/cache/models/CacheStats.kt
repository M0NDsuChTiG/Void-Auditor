package com.kuzyamond.voidauditor.cache.models

import com.kuzyamond.voidauditor.RiskLevel

data class CacheStats(
    val totalSizeBytes: Long = 0L,
    val totalEntries: Int = 0,
    val totalFiles: Int = 0,
    val entriesByPackage: Map<String, List<CacheEntry>> = emptyMap(),
    val scanDurationMs: Long = 0L,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val installedPackages: Int = 0
)

