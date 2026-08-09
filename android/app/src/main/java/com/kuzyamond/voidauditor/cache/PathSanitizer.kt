package com.kuzyamond.voidauditor.cache

import java.net.URLDecoder

object PathSanitizer {

    private val BLOCKED_PACKAGE_PREFIXES = listOf(
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.systemui",
        "com.android.chrome",
        "com.android.vending",
        "com.android.settings",
        "com.android.phone",
        "com.android.nfc",
        "com.android.bluetooth",
        "com.android.se"
    )

    private val SAFE_PREFIXES = listOf(
        "/data/data/",
        "/data/user/",
        "/data/user_de/",
        "/sdcard/Android/data/"
    )

    internal val SAFE_SUFFIX = setOf(
        "cache",
        "logs",
        "log",
        "thumbnails",
        "temp",
        "tmp",
        "glide",
        "fresco",
        "image_cache",
        "webview",
        "http_cache",
        "okhttp",
        "picasso",
        "coil",
        "download",
        "downloads",
        "file_cache",
        "videos",
        "audio",
        "offline",
        "databases",
        "no_backup",
        "code_cache",
        "shader_cache"
    )

    private val FORBIDDEN_PREFIXES = listOf(
        "/", "/system", "/vendor", "/product", "/data",
        "/dev", "/proc", "/sys", "/etc", "/bin", "/sbin", "/mnt",
        "/data/data/", "/data/user/", "/data/user_de/"
    )

    private val FORBIDDEN_CHARS = setOf(';', '&', '|', '`', '$', '(', ')', '{', '}', '<', '>', '\n', '\r')

    private val PATH_TRAVERSAL_PATTERNS = listOf(
        "../", "..\\", "..", "%2e%2e%2f", "%2e%2e/",
        "%2e./", ".%2e/", "..%252f", "..%c0%af"
    )

    fun URLdecode(path: String): String {
        return try {
            val decoded = URLDecoder.decode(path, "UTF-8")
            if (decoded != path) URLdecode(decoded) else decoded
        } catch (_: Exception) {
            path
        }
    }

    fun isVisible(path: String): Boolean {
        if (path.isBlank()) return false
        return SAFE_PREFIXES.any { path.startsWith(it) }
    }

    fun isSafeToClean(path: String): Boolean = isSafe(path)

    fun isSafe(rawPath: String): Boolean {
        if (rawPath.isBlank()) return false
        val path = URLdecode(rawPath.trim())

        if (FORBIDDEN_CHARS.any { path.contains(it) }) return false
        if (PATH_TRAVERSAL_PATTERNS.any { path.contains(it, ignoreCase = true) }) return false

        val hasValidPrefix = SAFE_PREFIXES.any { path.startsWith(it) }
        if (!hasValidPrefix) return false

        val hasForbiddenPrefix = FORBIDDEN_PREFIXES.any { path == it || path.startsWith("$it/") }
        if (hasForbiddenPrefix && SAFE_PREFIXES.none { path.startsWith(it) }) return false

        if (path == "/data/data/" || path == "/data/user/" ||
            path == "/data/user_de/" || path == "/sdcard/Android/data/"
        ) return false

        val segments = path.split("/").filter { it.isNotBlank() }
        for (seg in segments) {
            if (seg == "." || seg == "..") return false
            if (seg.length > 255) return false
        }

        val pkg = extractPackage(path)
        if (pkg != null && BLOCKED_PACKAGE_PREFIXES.any { pkg.startsWith(it) }) return false

        val firstDir = firstSegmentAfterPackage(path)
        if (firstDir == null || firstDir !in SAFE_SUFFIX) return false

        return true
    }

    private fun firstSegmentAfterPackage(path: String): String? {
        val clean = path.trimEnd('/')
        for (prefix in SAFE_PREFIXES) {
            if (!clean.startsWith(prefix)) continue
            val parts = clean.removePrefix(prefix).split("/").filter { it.isNotBlank() }
            if (parts.isEmpty()) return null
            val skipUserId = prefix == "/data/user/" || prefix == "/data/user_de/"
            return parts.getOrNull(if (skipUserId) 2 else 1)
        }
        return null
    }

    fun safeCleanCommand(path: String): String? {
        if (!isSafe(path)) return null
        val cleanPath = path.trimEnd('/')
        return "find \"$cleanPath\" -type f -delete 2>/dev/null; find \"$cleanPath\" -type d -empty -delete 2>/dev/null"
    }

    fun extractPackage(path: String): String? {
        val clean = path.trimEnd('/')
        for (prefix in SAFE_PREFIXES) {
            if (clean.startsWith(prefix)) {
                val rest = clean.removePrefix(prefix)
                val pkg = rest.split("/").firstOrNull() ?: continue
                if (pkg.isNotBlank()) return pkg
            }
        }
        return null
    }

    fun blockedReason(rawPath: String): String {
        if (rawPath.isBlank()) return "EMPTY_PATH"
        val path = URLdecode(rawPath.trim())

        val forbiddenChar = FORBIDDEN_CHARS.firstOrNull { path.contains(it) }
        if (forbiddenChar != null) return "FORBIDDEN_CHAR: '$forbiddenChar'"

        val traversal = PATH_TRAVERSAL_PATTERNS.firstOrNull { path.contains(it, ignoreCase = true) }
        if (traversal != null) return "PATH_TRAVERSAL: '$traversal'"

        val validPrefix = SAFE_PREFIXES.any { path.startsWith(it) }
        if (!validPrefix) return "NOT_SAFE_PREFIX: must start with ${SAFE_PREFIXES.firstOrNull()}"

        if (path.trimEnd('/') in SAFE_PREFIXES.map { it.trimEnd('/') }) return "ROOT_REFUSED: cannot clean base dir itself"

        val pkg = extractPackage(path)
        if (pkg != null) {
            val blocked = BLOCKED_PACKAGE_PREFIXES.firstOrNull { pkg.startsWith(it) }
            if (blocked != null) return "PACKAGE_BLOCKED: $blocked"
        }

        return "UNKNOWN"
    }

    enum class SanitizeMode { SCAN, PURGE }

    fun sanitizeBatch(paths: List<String>, mode: SanitizeMode = SanitizeMode.PURGE): List<String> {
        return when (mode) {
            SanitizeMode.SCAN -> paths.filter { isVisible(it) }
            SanitizeMode.PURGE -> paths.filter { isSafe(it) }
        }
    }
}
