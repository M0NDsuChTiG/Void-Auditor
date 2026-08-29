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
        val line = output.lines().firstOrNull() ?: return null
        val parts = line.trim().split("\\s+".toRegex())
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
        val ssid = output.lines()
            .find { it.trim().startsWith("SSID") }
            ?.substringAfter(":")
            ?.trim()
        val bssid = output.lines()
            .find { it.trim().startsWith("BSSID") }
            ?.substringAfter(":")
            ?.trim()
        return WifiEvidence(
            capturedAt = System.currentTimeMillis(),
            ssid = ssid,
            bssid = bssid
        )
    }
}

class PackageDetailsParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadPackageDetails ?: return null
        if (!result.isSuccessful) return null
        
        val output = result.output
        val versionName = output.lines().firstOrNull { it.contains("versionName") }?.substringAfter("versionName=")?.split(" ")[0]
        val versionCode = output.lines().firstOrNull { it.contains("versionCode") }?.substringAfter("versionCode=")?.split(" ")[0]?.toLongOrNull()
        val installer = output.lines().firstOrNull { it.contains("installerPackageName") }?.substringAfter("installerPackageName=")?.split(" ")[0]
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
}

class ARPTableParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadARPTable ?: return null
        if (!result.isSuccessful) return null
        
        val entries = result.output.lines()
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
        val uid = output.substringAfter("uid=").substringBefore("(")
        val gid = output.substringAfter("gid=").substringBefore("(")
        val groups = output.substringAfter("groups=").split(",").map { it.trim() }
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
        return SettingEvidence(
            capturedAt = System.currentTimeMillis(),
            namespace = cap.namespace,
            key = cap.key,
            value = result.output.trim().takeIf { it.isNotBlank() }
        )
    }
}

class PingSweepParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.PingSweep ?: return null
        if (!result.isSuccessful) return null
        
        val aliveHosts = result.output.lines()
            .map { it.trim() }
            .filter { it.matches(Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")) }
        return PingSweepEvidence(
            capturedAt = System.currentTimeMillis(),
            aliveHosts = aliveHosts
        )
    }
}

class PackageListParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.QueryPackages ?: return null
        if (!result.isSuccessful) return null
        
        val packages = result.output.lines()
            .map { it.substringAfter("package:") }
            .filter { it.isNotBlank() }
        return PackageListEvidence(
            capturedAt = System.currentTimeMillis(),
            packages = packages
        )
    }
}

class ServiceDumpParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.DumpService ?: return null
        if (!result.isSuccessful) return null
        return ServiceDumpEvidence(
            capturedAt = System.currentTimeMillis(),
            service = cap.service,
            output = result.output
        )
    }
}

class FeaturesParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadSystemFeatures ?: return null
        if (!result.isSuccessful) return null
        val features = result.output.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return FeaturesEvidence(
            capturedAt = System.currentTimeMillis(),
            features = features
        )
    }
}

class DangerousPermissionsParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadDangerousPermissions ?: return null
        if (!result.isSuccessful) return null
        val permissions = result.output.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return DangerousPermissionsEvidence(
            capturedAt = System.currentTimeMillis(),
            permissions = permissions
        )
    }
}

class CacheDirectoriesParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.DiscoverCacheDirectories ?: return null
        if (!result.isSuccessful) return null
        val paths = result.output.lines()
            .filter { it.isNotBlank() }
            .distinctBy { raw ->
                raw.replace("/data/user/0/", "/data/data/")
                    .replace("/storage/emulated/0/", "/sdcard/")
            }
        return CacheDirectoriesEvidence(
            capturedAt = System.currentTimeMillis(),
            paths = paths
        )
    }
}

class PackageCountParser : CapabilityEvidenceParser {
    override fun parse(capability: Capability, result: ShizukuExecutor.CommandResult): CapabilityEvidence? {
        val cap = capability as? Capability.ReadPackageCount ?: return null
        if (!result.isSuccessful) return null
        val count = result.output.trim().toIntOrNull() ?: 0
        return PackageCountEvidence(
            capturedAt = System.currentTimeMillis(),
            count = count
        )
    }
}