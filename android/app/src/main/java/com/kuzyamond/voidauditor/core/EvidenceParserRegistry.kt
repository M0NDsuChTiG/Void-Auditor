package com.kuzyamond.voidauditor.core

import java.util.regex.Pattern

private val DANGEROUS_PERMISSION_REGEX = Pattern.compile(
    "android\\.permission\\.(SEND_SMS|RECEIVE_SMS|READ_SMS|READ_CALL_LOG|WRITE_CALL_LOG|READ_CONTACTS|WRITE_CONTACTS|SYSTEM_ALERT_WINDOW|READ_PHONE_STATE|RECORD_AUDIO|ACCESS_FINE_LOCATION|REQUEST_INSTALL_PACKAGES|QUERY_ALL_PACKAGES)|BIND_ACCESSIBILITY_SERVICE",
    Pattern.CASE_INSENSITIVE
)

interface CapabilityEvidenceParser {
    fun parse(
        capability: Capability,
        result: ShizukuExecutor.CommandResult
    ): CapabilityEvidence?
}

class EvidenceParserRegistry(
    private val parsers: Map<String, CapabilityEvidenceParser>
) {
    fun parse(
        capability: Capability,
        result: ShizukuExecutor.CommandResult
    ): CapabilityEvidence? {
        if (!result.isSuccessful) return null
        return parsers[capability.id]?.parse(capability, result)
    }
}

class DefaultEvidenceParser : CapabilityEvidenceParser {
    private val registry = EvidenceParserRegistry(mapOf(
        "ReadDefaultRoute" to DefaultRouteParser(),
        "ReadWifiInfo" to WifiInfoParser(),
        "ReadPackageDetails" to PackageDetailsParser(),
        "ReadARPTable" to ARPTableParser(),
        "ReadUserIdentity" to UserIdentityParser(),
        "ReadSetting" to SettingParser(),
        "PingSweep" to PingSweepParser(),
        "QueryPackages" to PackageListParser(),
        "DumpService" to ServiceDumpParser(),
        "ReadSystemFeatures" to FeaturesParser(),
        "ReadDangerousPermissions" to DangerousPermissionsParser(),
        "DiscoverCacheDirectories" to CacheDirectoriesParser(),
        "ReadPackageCount" to PackageCountParser(),
        "ReadDiskUsage" to DiskUsageParser(),
        "ReadDirectorySize" to DirectorySizeParser(),
        "ReadFileCount" to FileCountParser(),
        "ReadLastModified" to LastModifiedParser(),
    ))

    override fun parse(
        capability: Capability,
        result: ShizukuExecutor.CommandResult
    ): CapabilityEvidence? {
        return registry.parse(capability, result)
    }
}

// --- Parsers ---

class DefaultRouteParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadDefaultRoute ?: return null
        if (!result.isSuccessful) return null

        val output = result.output
        val line = output.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("default ") }
            ?: return null
        val parts = line.split("\\s+".toRegex())
        val devIdx = parts.indexOf("dev")
        val viaIdx = parts.indexOf("via")
        val interfaceName = if (devIdx >= 0 && devIdx + 1 < parts.size) parts[devIdx + 1] else null
        val gateway = if (viaIdx >= 0 && viaIdx + 1 < parts.size) parts[viaIdx + 1] else null
        return DefaultRouteEvidence(
            capturedAt = System.currentTimeMillis(),
            interfaceName = interfaceName,
            gateway = gateway
        )
    }
}

class WifiInfoParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadWifiInfo ?: return null
        if (!result.isSuccessful) return null

        val output = result.output
        val ssid = valueForKey(output, "SSID")
        val bssid = valueForKey(output, "BSSID")
        return WifiEvidence(
            capturedAt = System.currentTimeMillis(),
            ssid = ssid,
            bssid = bssid
        )
    }

    private fun valueForKey(output: String, key: String): String? =
        output.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

class PackageDetailsParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadPackageDetails ?: return null
        if (!result.isSuccessful) return null

        val output = result.output
        val versionName = fieldValue(output, "versionName")
        val versionCode = fieldValue(output, "versionCode")?.toLongOrNull()
        val installer = fieldValue(output, "installerPackageName")
        val permissions = DANGEROUS_PERMISSION_REGEX.findAll(output)
            .map { it.value }
            .distinct()
            .toList()
        return PackageDetailsEvidence(
            capturedAt = System.currentTimeMillis(),
            packageName = cap.packageName,
            versionName = versionName,
            versionCode = versionCode,
            installerPackageName = installer,
            permissions = permissions
        )
    }

    private fun fieldValue(output: String, key: String): String? =
        output.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

class ARPTableParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadARPTable ?: return null
        if (!result.isSuccessful) return null

        val entries = result.output.lineSequence()
            .drop(1) // skip header
            .mapNotNull { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 6) ARPEntry(
                    ipAddress = parts[0],
                    hwType = parts[1].takeIf { it != "0x0" },
                    flags = parts[2],
                    hwAddress = parts[3].takeIf { it != "00:00:00:00:00:00" && it != "(incomplete)" },
                    mask = parts[4].takeIf { it != "*" },
                    device = parts[5]
                ) else null
            }
            .toList()
        return ARPTableEvidence(
            capturedAt = System.currentTimeMillis(),
            entries = entries
        )
    }
}

class UserIdentityParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadUserIdentity ?: return null
        if (!result.isSuccessful) return null

        val output = result.output
        val uid = output.substringAfter("uid=").substringBefore("(").takeIf { it.isNotEmpty() }
        val gid = output.substringAfter("gid=").substringBefore("(").takeIf { it.isNotEmpty() }
        val groups = output.substringAfter("groups=").split(",").map { it.trim() }.filter { it.isNotBlank() }

        if (uid == null && gid == null) return null
        return UserIdentityEvidence(
            capturedAt = System.currentTimeMillis(),
            uid = uid,
            gid = gid,
            groups = groups
        )
    }
}

class SettingParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadSetting ?: return null
        if (!result.isSuccessful) return null
        val value = result.output.trim().takeIf { it.isNotBlank() }
        return SettingEvidence(
            capturedAt = System.currentTimeMillis(),
            namespace = cap.namespace,
            key = cap.key,
            value = value
        )
    }
}

class DiskUsageParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadDiskUsage ?: return null
        if (!result.isSuccessful) return null
        val kilobytes = result.output.trim().toLongOrNull()
        return DiskUsageEvidence(
            capturedAt = System.currentTimeMillis(),
            kilobytes = kilobytes
        )
    }
}

class DirectorySizeParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadDirectorySize ?: return null
        if (!result.isSuccessful) return null
        val bytes = result.output.trim().toLongOrNull()
        return DirectorySizeEvidence(
            capturedAt = System.currentTimeMillis(),
            bytes = bytes
        )
    }
}

class FileCountParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadFileCount ?: return null
        if (!result.isSuccessful) return null
        val count = result.output.trim().toIntOrNull()
        return FileCountEvidence(
            capturedAt = System.currentTimeMillis(),
            count = count
        )
    }
}

class LastModifiedParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadLastModified ?: return null
        if (!result.isSuccessful) return null
        val timestamp = result.output.trim().toLongOrNull()
        return LastModifiedEvidence(
            capturedAt = System.currentTimeMillis(),
            timestamp = timestamp
        )
    }
}