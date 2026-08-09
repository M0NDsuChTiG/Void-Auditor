package com.kuzyamond.voidauditor.cache

import com.kuzyamond.voidauditor.RiskLevel

enum class CacheCapability(val displayName: String, val riskLevel: RiskLevel, val description: String) {
    QUICK("QUICK_CACHE_RESCAN", RiskLevel.LOW, "Scan recently modified cache dirs (<7d)"),
    FULL("FULL_CACHE_AUDIT", RiskLevel.MEDIUM, "Scan all /data/data/*/cache + /sdcard/Android/data/*/cache"),
    DEEP("DEEP_CACHE_FORENSICS", RiskLevel.HIGH, "Scan with file-type classification and size histogram"),
    SYSTEM_TRIM("SYSTEM_TRIM", RiskLevel.TIER_1_REVERSIBLE, "Bulk system cache trim via pm trim-caches")
}
