package com.kuzyamond.voidauditor.cache

import org.junit.Assert.*
import org.junit.Test

class PathSanitizerTest {

    @Test
    fun `valid data data cache path passes`() {
        assertTrue(PathSanitizer.isSafe("/data/data/com.example.app/cache"))
    }

    @Test
    fun `valid data user cache path passes`() {
        assertTrue(PathSanitizer.isSafe("/data/user/0/com.example.app/cache"))
    }

    @Test
    fun `valid data user de cache path passes`() {
        assertTrue(PathSanitizer.isSafe("/data/user_de/0/com.example.app/cache"))
    }

    @Test
    fun `valid sdcard android data cache passes`() {
        assertTrue(PathSanitizer.isSafe("/sdcard/Android/data/com.example.app/cache"))
    }

    @Test
    fun `empty string rejected`() {
        assertFalse(PathSanitizer.isSafe(""))
    }

    @Test
    fun `blank string rejected`() {
        assertFalse(PathSanitizer.isSafe("   "))
    }

    @Test
    fun `gms package blocked`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.google.android.gms/cache"))
    }

    @Test
    fun `gsf package blocked`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.google.android.gsf/cache"))
    }

    @Test
    fun `systemui package blocked`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.android.systemui/cache"))
    }

    @Test
    fun `chrome package blocked`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.android.chrome/cache"))
    }

    @Test
    fun `vending package blocked`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.android.vending/cache"))
    }

    @Test
    fun `settings package blocked`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.android.settings/cache"))
    }

    @Test
    fun `phone package blocked`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.android.phone/cache"))
    }

    @Test
    fun `nfc package blocked`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.android.nfc/cache"))
    }

    @Test
    fun `bluetooth package blocked`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.android.bluetooth/cache"))
    }

    @Test
    fun `se package blocked`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.android.se/cache"))
    }

    @Test
    fun `shell semicolon rejected`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.example.app/cache; rm -rf /"))
    }

    @Test
    fun `shell ampersand rejected`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.example.app/cache & rm -rf /"))
    }

    @Test
    fun `shell pipe rejected`() {
        assertFalse(PathSanitizer.isSafe("| rm -rf /"))
    }

    @Test
    fun `shell backtick rejected`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.example.app/`ls`/cache"))
    }

    @Test
    fun `shell dollar rejected`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.example.app/\$(id)/cache"))
    }

    @Test
    fun `path traversal dotdot rejected`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.example.app/cache/../../system"))
    }

    @Test
    fun `path traversal dotdot encoded rejected`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.example.app/%2e%2e%2f..%2f..%2fsystem"))
    }

    @Test
    fun `path traversal dotdot slash rejected`() {
        assertFalse(PathSanitizer.isSafe("../cache"))
    }

    @Test
    fun `root system rejected`() {
        assertFalse(PathSanitizer.isSafe("/system"))
    }

    @Test
    fun `root vendor rejected`() {
        assertFalse(PathSanitizer.isSafe("/vendor"))
    }

    @Test
    fun `root product rejected`() {
        assertFalse(PathSanitizer.isSafe("/product"))
    }

    @Test
    fun `root data rejected`() {
        assertFalse(PathSanitizer.isSafe("/data"))
    }

    @Test
    fun `root device rejected`() {
        assertFalse(PathSanitizer.isSafe("/dev"))
    }

    @Test
    fun `root proc rejected`() {
        assertFalse(PathSanitizer.isSafe("/proc"))
    }

    @Test
    fun `root sys rejected`() {
        assertFalse(PathSanitizer.isSafe("/sys"))
    }

    @Test
    fun `root etc rejected`() {
        assertFalse(PathSanitizer.isSafe("/etc"))
    }

    @Test
    fun `root bin rejected`() {
        assertFalse(PathSanitizer.isSafe("/bin"))
    }

    @Test
    fun `root sbin rejected`() {
        assertFalse(PathSanitizer.isSafe("/sbin"))
    }

    @Test
    fun `root data data dir itself rejected`() {
        assertFalse(PathSanitizer.isSafe("/data/data/"))
    }

    @Test
    fun `root data user dir itself rejected`() {
        assertFalse(PathSanitizer.isSafe("/data/user/"))
    }

    @Test
    fun `root data user de dir itself rejected`() {
        assertFalse(PathSanitizer.isSafe("/data/user_de/"))
    }

    @Test
    fun `root sdcard android data rejected`() {
        assertFalse(PathSanitizer.isSafe("/sdcard/Android/data/"))
    }

    @Test
    fun `dot segment in path rejected`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.example.app/./cache"))
    }

    @Test
    fun `double dot segment in path rejected`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.example.app/foo/../cache"))
    }

    @Test
    fun `segment over 255 chars rejected`() {
        val longSeg = "a".repeat(256)
        assertFalse(PathSanitizer.isSafe("/data/data/com.example.app/$longSeg/cache"))
    }

    @Test
    fun `safeCleanCommand returns non null for valid path`() {
        val cmd = PathSanitizer.safeCleanCommand("/data/data/com.example.app/cache")
        assertNotNull(cmd)
    }

    @Test
    fun `safeCleanCommand uses find delete not rm rf`() {
        val cmd = PathSanitizer.safeCleanCommand("/data/data/com.example.app/cache")
        assertNotNull(cmd)
        assertTrue(cmd!!.startsWith("find "))
        assertTrue(cmd.contains("-delete"))
        assertFalse(cmd.contains("rm -rf"))
    }

    @Test
    fun `safeCleanCommand returns null for blocked package`() {
        assertNull(PathSanitizer.safeCleanCommand("/data/data/com.android.chrome/cache"))
    }

    @Test
    fun `safeCleanCommand returns null for traversal path`() {
        assertNull(PathSanitizer.safeCleanCommand("/data/data/com.example.app/../../../system"))
    }

    @Test
    fun `safeCleanCommand returns null for empty path`() {
        assertNull(PathSanitizer.safeCleanCommand(""))
    }

    @Test
    fun `blockedReason returns EMPTY_PATH for blank`() {
        assertEquals("EMPTY_PATH", PathSanitizer.blockedReason(""))
    }

    @Test
    fun `blockedReason returns FORBIDDEN_CHAR for semicolon`() {
        val reason = PathSanitizer.blockedReason("/data/data/com.example.app/cache;ls")
        assertTrue(reason.startsWith("FORBIDDEN_CHAR"))
    }

    @Test
    fun `blockedReason returns PATH_TRAVERSAL for dotdot`() {
        val reason = PathSanitizer.blockedReason("/data/data/com.example.app/../cache")
        assertTrue(reason.startsWith("PATH_TRAVERSAL"))
    }

    @Test
    fun `blockedReason returns NOT_SAFE_PREFIX for system path`() {
        val reason = PathSanitizer.blockedReason("/system/bin")
        assertTrue(reason.startsWith("NOT_SAFE_PREFIX"))
    }

    @Test
    fun `blockedReason returns ROOT_REFUSED for data data dir`() {
        val reason = PathSanitizer.blockedReason("/data/data/")
        assertTrue(reason.startsWith("ROOT_REFUSED"))
    }

    @Test
    fun `blockedReason returns PACKAGE_BLOCKED for gms`() {
        val reason = PathSanitizer.blockedReason("/data/data/com.google.android.gms/cache")
        assertTrue(reason.startsWith("PACKAGE_BLOCKED"))
    }

    @Test
    fun `extractPackage returns correct package`() {
        assertEquals("com.example.app", PathSanitizer.extractPackage("/data/data/com.example.app/cache"))
    }

    @Test
    fun `extractPackage works with subdirectories`() {
        assertEquals("com.example.app", PathSanitizer.extractPackage("/data/data/com.example.app/cache/sub/dir"))
    }

    @Test
    fun `extractPackage returns null for invalid prefix`() {
        assertNull(PathSanitizer.extractPackage("/system/bin"))
    }

    @Test
    fun `extractPackage works with sdcard prefix`() {
        assertEquals("com.example.app", PathSanitizer.extractPackage("/sdcard/Android/data/com.example.app/cache"))
    }

    @Test
    fun `sanitizeBatch filters blocked paths`() {
        val input = listOf(
            "/data/data/com.example.app/cache",
            "/data/data/com.android.chrome/cache",
            "/system/bin"
        )
        val result = PathSanitizer.sanitizeBatch(input)
        assertEquals(1, result.size)
        assertEquals("/data/data/com.example.app/cache", result[0])
    }

    @Test
    fun `sanitizeBatch returns empty for all unsafe`() {
        val input = listOf("/system", "/data", "/proc/1")
        assertTrue(PathSanitizer.sanitizeBatch(input).isEmpty())
    }

    @Test
    fun `URLdecode handles plain string`() {
        assertEquals("hello", PathSanitizer.URLdecode("hello"))
    }

    @Test
    fun `URLdecode handles single encoding`() {
        assertEquals("/data/data/com.example.app/cache", PathSanitizer.URLdecode("/data/data/com.example.app/cache"))
    }

    @Test
    fun `URLdecode handles double encoding`() {
        val result = PathSanitizer.URLdecode("%252e%252e%252f")
        assertTrue(result.contains("../") || result.contains("%"))
    }

    @Test
    fun `trailing slash normalized`() {
        assertTrue(PathSanitizer.isSafe("/data/data/com.example.app/cache/"))
    }

    @Test
    fun `deeply nested cache file validated`() {
        assertTrue(PathSanitizer.isSafe("/data/data/com.example.app/cache/sub/dir/file.txt"))
    }

    @Test
    fun `cache subdir with numbers works`() {
        assertTrue(PathSanitizer.isSafe("/data/user/10/com.example.app/cache"))
    }

    @Test
    fun `mnt path rejected`() {
        assertFalse(PathSanitizer.isSafe("/mnt/media"))
    }

    @Test
    fun `safe prefix inside prefix check`() {
        assertTrue(PathSanitizer.isSafe("/data/data/com.example.app/cache"))
    }

    @Test
    fun `no prefix at all rejected`() {
        assertFalse(PathSanitizer.isSafe("/custom/path"))
    }

    @Test
    fun `data with slash after rejected exact`() {
        assertFalse(PathSanitizer.isSafe("/data/"))
    }

    @Test
    fun `line feed in path rejected`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.example.app/cache\nls"))
    }

    @Test
    fun `carriage return in path rejected`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.example.app/cache\rid"))
    }

    @Test
    fun `parentheses in path rejected`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.example.app/cache(id)"))
    }

    @Test
    fun `safeCleanCommand returns find delete for user de`() {
        val cmd = PathSanitizer.safeCleanCommand("/data/user_de/0/com.example.app/cache")
        assertNotNull(cmd)
        assertTrue(cmd!!.contains("-delete"))
    }

    @Test
    fun `safeCleanCommand returns find delete for sdcard path`() {
        val cmd = PathSanitizer.safeCleanCommand("/sdcard/Android/data/com.example.app/cache")
        assertNotNull(cmd)
        assertTrue(cmd!!.contains("-delete"))
    }

    @Test
    fun `blockedReason returns UNKNOWN for safe path`() {
        assertEquals("UNKNOWN", PathSanitizer.blockedReason("/data/data/com.example.app/cache"))
    }

    @Test
    fun `same package safe with different package variants`() {
        assertTrue(PathSanitizer.isSafe("/data/data/com.example.app.test/cache"))
    }

    @Test
    fun `blocked prefix nested check`() {
        assertFalse(PathSanitizer.isSafe("/data/data/com.google.android.gms.something/cache"))
    }

    @Test
    fun `very long overall path safe`() {
        val seg = "a".repeat(200)
        assertTrue(PathSanitizer.isSafe("/data/data/com.example.app/cache/$seg"))
    }

    @Test
    fun `blockedReason for safe path with traversal`() {
        assertTrue(PathSanitizer.blockedReason("/data/data/com.example.app/foo/../cache").startsWith("PATH_TRAVERSAL"))
    }

    @Test
    fun `safeCleanCommand correct format`() {
        val cmd = PathSanitizer.safeCleanCommand("/data/data/com.example.app/cache")
        assertNotNull(cmd)
        assertTrue(cmd!!.contains("/data/data/com.example.app/cache"))
        assertTrue(cmd.contains(";"))
        assertTrue(cmd.contains("-type f"))
        assertTrue(cmd.contains("-type d"))
    }
}
