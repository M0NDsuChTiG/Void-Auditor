package com.kuzyamond.voidauditor.core

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