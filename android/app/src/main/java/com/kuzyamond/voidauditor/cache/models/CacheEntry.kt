package com.kuzyamond.voidauditor.cache.models

data class CacheEntry(
    val path: String,
    val packageName: String,
    val sizeBytes: Long,
    val fileCount: Int,
    val lastModified: Long,
    val isSafeToDelete: Boolean = true
)
