package com.kuzyamond.voidauditor.core

import java.util.regex.Pattern

private val DANGEROUS_PERMISSION_REGEX = Pattern.compile(
    "android\\.permission\\.(SEND_SMS|RECEIVE_SMS|READ_SMS|READ_CALL_LOG|WRITE_CALL_LOG|READ_CONTACTS|WRITE_CONTACTS|SYSTEM_ALERT_WINDOW|READ_PHONE_STATE|RECORD_AUDIO|ACCESS_FINE_LOCATION|REQUEST_INSTALL_PACKAGES|QUERY_ALL_PACKAGES)|BIND_ACCESSIBILITY_SERVICE",
    Pattern.CASE_INSENSITIVE
)

interface EvidenceParser {
    fun parse(
        capability: Capability,
        result: ShizukuExecutor.CommandResult
    ): EvidenceResult
}

class DefaultEvidenceParser : EvidenceParser {
    override fun parse(
        capability: Capability,
        result: ShizukuExecutor.CommandResult
    ): EvidenceResult = when (capability) {
        is Capability.ReadDefaultRoute -> parseDefaultRoute(capability, result)
        is Capability.ReadWifiInfo -> parseWifiInfo(capability, result)
        is Capability.ReadPackageDetails -> parsePackageDetails(capability, result)
        is Capability.ReadARPTable -> parseARPTable(capability, result)
        is Capability.ReadUserIdentity -> parseUserIdentity(capability, result)
        is Capability.ReadSetting -> parseSetting(capability, result)
        is Capability.ReadWifiInfo -> parseWifiInfo(capability, result)
        is Capability.PingSweep -> parsePingSweep(capability, result)
        is Capability.QueryPackages -> parsePackageList(capability, result)
        is Capability.DumpService -> parseServiceDump(capability, result)
        is Capability.ReadSystemFeatures -> parseSystemFeatures(capability, result)
        is Capability.ReadDangerousPermissions -> parseDangerousPermissions(capability, result)
        is Capability.DiscoverCacheDirectories -> parseCacheDirectories(capability, result)
        is Capability.ReadPackageCount -> parsePackageCount(capability, result)
        else -> EvidenceResult.NotApplicable(capability.id)
    }

    private fun parseDefaultRoute(cap: Capability.ReadDefaultRoute, result: ShizukuExecutor.CommandResult): EvidenceResult {
        if (!result.isSuccessful) {
            return EvidenceResult.ParseFailed(cap.id, "Command failed: ${result.error}", result.output)
        }
        val output = result.output
        val line = output.lines().firstOrNull() ?: ""
        val parts = line.trim().split("\\s+".toRegex())
        val devIdx = parts.indexOf("dev")
        val viaIdx = parts.indexOf("via")
        val interfaceName = if (devIdx >= 0 && devIdx + 1 < parts.size) parts[devIdx + 1] else null
        val gateway = if (viaIdx >= 0 && viaIdx + 1 < parts.size) parts[viaIdx + 1] else null
        return EvidenceResult.Parsed(cap.id, DefaultRouteEvidence(
            interfaceName = interfaceName,
            gateway = gateway
        ))
    }

    private fun parseWifiInfo(cap: Capability.ReadWifiInfo, result: ShizukuExecutor.CommandResult): EvidenceResult {
        if (!result.isSuccessful) {
            return EvidenceResult.ParseFailed(cap.id, "Command failed: ${result.error}", result.output)
        }
        val output = result.output
        val ssid = output.lines()
            .find { it.trim().startsWith("SSID") }
            ?.substringAfter(":")
            ?.trim()
        val bssid = output.lines()
            .find { it.trim().startsWith("BSSID") }
            ?.substringAfter(":")
            ?.trim()
        return EvidenceResult.Parsed(cap.id, WifiEvidence(
            ssid = ssid,
            bssid = bssid
        ))
    }

    private fun parsePackageDetails(cap: Capability.ReadPackageDetails, result: ShizukuExecutor.CommandResult): EvidenceResult {
        if (!result.isSuccessful) {
            return EvidenceResult.ParseFailed(cap.id, "Command failed: ${result.error}", result.output)
        }
        val output = result.output
        val versionName = output.lines().firstOrNull { it.contains("versionName") }?.substringAfter("versionName=")?.split(" ")[0]
        val versionCode = output.lines().firstOrNull { it.contains("versionCode") }?.substringAfter("versionCode=")?.split(" ")[0]?.toIntOrNull()
        val installer = output.lines().firstOrNull { it.contains("installerPackageName") }?.substringAfter("installerPackageName=")?.split(" ")[0]
        val permissions = DANGEROUS_PERMISSION_REGEX.findAll(output)
            .map { it.value }
            .distinct()
            .toList()
        return EvidenceResult.Parsed(cap.id, PackageDetailsEvidence(
            packageName = cap.packageName,
            versionName = versionName,
            versionCode = versionCode,
            installerPackageName = installer,
            permissions = permissions
        ))
    }

    private fun parseARPTable(cap: Capability.ReadARPTable, result: ShizukuExecutor.CommandResult): EvidenceResult {
        if (!result.isSuccessful) {
            return EvidenceResult.ParseFailed(cap.id, "Command failed: ${result.error}", result.output)
        }
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
        return EvidenceResult.Parsed(cap.id, ARPTableEvidence(entries = entries))
    }

    private fun parseUserIdentity(cap: Capability.ReadUserIdentity, result: ShizukuExecutor.CommandResult): EvidenceResult {
        if (!result.isSuccessful) {
            return EvidenceResult.ParseFailed(cap.id, "Command failed: ${result.error}", result.output)
        }
        val output = result.output
        val uid = output.substringAfter("uid=").substringBefore("(")
        val gid = output.substringAfter("gid=").substringBefore("(")
        val groups = output.substringAfter("groups=").split(",").map { it.trim() }
        return EvidenceResult.Parsed(cap.id, UserIdentityEvidence(
            uid = uid,
            gid = gid,
            groups = groups
        ))
    }

    private fun parseSetting(cap: Capability.ReadSetting, result: ShizukuExecutor.CommandResult): EvidenceResult {
        if (!result.isSuccessful) {
            return EvidenceResult.ParseFailed(cap.id, "Command failed: ${result.error}", result.output)
        }
        return EvidenceResult.Parsed(cap.id, SettingEvidence(
            namespace = cap.namespace,
            key = cap.key,
            value = result.output.trim().takeIf { it.isNotBlank() }
        ))
    }

    private fun parsePingSweep(cap: Capability.PingSweep, result: ShizukuExecutor.CommandResult): EvidenceResult {
        if (!result.isSuccessful) {
            return EvidenceResult.ParseFailed(cap.id, "Command failed: ${result.error}", result.output)
        }
        val aliveHosts = result.output.lines()
            .map { it.trim() }
            .filter { it.matches(Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")) }
        return EvidenceResult.Parsed(cap.id, PingSweepEvidence(aliveHosts = aliveHosts))
    }

    private fun parsePackageList(cap: Capability.QueryPackages, result: ShizukuExecutor.CommandResult): EvidenceResult {
        if (!result.isSuccessful) {
            return EvidenceResult.ParseFailed(cap.id, "Command failed: ${result.error}", result.output)
        }
        val packages = result.output.lines()
            .map { it.substringAfter("package:") }
            .filter { it.isNotBlank() }
        return EvidenceResult.Parsed(cap.id, PackageListEvidence(packages = packages))
    }

    private fun parseServiceDump(cap: Capability.DumpService, result: ShizukuExecutor.CommandResult): EvidenceResult {
        if (!result.isSuccessful) {
            return EvidenceResult.ParseFailed(cap.id, "Command failed: ${result.error}", result.output)
        }
        return EvidenceResult.Parsed(cap.id, ServiceDumpEvidence(
            service = cap.service,
            output = result.output
        ))
    }

    private fun parseSystemFeatures(cap: Capability.ReadSystemFeatures, result: ShizukuExecutor.CommandResult): EvidenceResult {
        if (!result.isSuccessful) {
            return EvidenceResult.ParseFailed(cap.id, "Command failed: ${result.error}", result.output)
        }
        val features = result.output.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return EvidenceResult.Parsed(cap.id, FeaturesEvidence(features = features))
    }

    private fun parseDangerousPermissions(cap: Capability.ReadDangerousPermissions, result: ShizukuExecutor.CommandResult): EvidenceResult {
        if (!result.isSuccessful) {
            return EvidenceResult.ParseFailed(cap.id, "Command failed: ${result.error}", result.output)
        }
        val permissions = result.output.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return EvidenceResult.Parsed(cap.id, DangerousPermissionsEvidence(permissions = permissions))
    }

    private fun parseCacheDirectories(cap: Capability.DiscoverCacheDirectories, result: ShizukuExecutor.CommandResult): EvidenceResult {
        if (!result.isSuccessful) {
            return EvidenceResult.ParseFailed(cap.id, "Command failed: ${result.error}", result.output)
        }
        val paths = result.output.lines()
            .filter { it.isNotBlank() }
            .distinctBy { raw ->
                raw.replace("/data/user/0/", "/data/data/")
                    .replace("/storage/emulated/0/", "/sdcard/")
            }
        return EvidenceResult.Parsed(cap.id, CacheDirectoriesEvidence(paths = paths))
    }

    private fun parsePackageCount(cap: Capability.ReadPackageCount, result: ShizukuExecutor.CommandResult): EvidenceResult {
        if (!result.isSuccessful) {
            return EvidenceResult.ParseFailed(cap.id, "Command failed: ${result.error}", result.output)
        }
        val count = result.output.trim().toIntOrNull() ?: 0
        return EvidenceResult.Parsed(cap.id, PackageCountEvidence(count = count))
    }
}