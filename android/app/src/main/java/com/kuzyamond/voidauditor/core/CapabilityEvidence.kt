package com.kuzyamond.voidauditor.core

sealed interface CapabilityEvidence {
    val capabilityId: String
    val observedAt: Long
}

// Structured evidence types
data class DefaultRouteEvidence(
    override val capabilityId: String = "ReadDefaultRoute",
    override val observedAt: Long = System.currentTimeMillis(),
    val interfaceName: String?,
    val gateway: String?
) : CapabilityEvidence

data class WifiEvidence(
    override val capabilityId: String = "ReadWifiInfo",
    override val observedAt: Long = System.currentTimeMillis(),
    val ssid: String?,
    val bssid: String?
) : CapabilityEvidence

data class PackageDetailsEvidence(
    override val capabilityId: String = "ReadPackageDetails",
    override val observedAt: Long = System.currentTimeMillis(),
    val packageName: String,
    val versionName: String?,
    val versionCode: Int?,
    val installerPackageName: String?,
    val permissions: List<String>
) : CapabilityEvidence

data class ARPTableEvidence(
    override val capabilityId: String = "ReadARPTable",
    override val observedAt: Long = System.currentTimeMillis(),
    val entries: List<ARPEntry>
) : CapabilityEvidence

data class ARPEntry(
    val ipAddress: String,
    val hwType: String?,
    val flags: String?,
    val hwAddress: String?,
    val mask: String?,
    val device: String?
)

data class UserIdentityEvidence(
    override val capabilityId: String = "ReadUserIdentity",
    override val observedAt: Long = System.currentTimeMillis(),
    val uid: String?,
    val gid: String?,
    val groups: List<String>
) : CapabilityEvidence

data class SettingEvidence(
    override val capabilityId: String = "ReadSetting",
    override val observedAt: Long = System.currentTimeMillis(),
    val namespace: String,
    val key: String,
    val value: String?
) : CapabilityEvidence

data class PingSweepEvidence(
    override val capabilityId: String = "PingSweep",
    override val observedAt: Long = System.currentTimeMillis(),
    val aliveHosts: List<String>
) : CapabilityEvidence

data class PackageListEvidence(
    override val capabilityId: String = "QueryPackages",
    override val observedAt: Long = System.currentTimeMillis(),
    val packages: List<String>
) : CapabilityEvidence

data class ServiceDumpEvidence(
    override val capabilityId: String = "DumpService",
    override val observedAt: Long = System.currentTimeMillis(),
    val service: String,
    val output: String
) : CapabilityEvidence

data class RawOutputEvidence(
    override val capabilityId: String,
    override val observedAt: Long = System.currentTimeMillis(),
    val output: String
) : CapabilityEvidence

// Collection evidence
data class FeaturesEvidence(
    override val capabilityId: String = "ReadSystemFeatures",
    override val observedAt: Long = System.currentTimeMillis(),
    val features: List<String>
) : CapabilityEvidence

data class DangerousPermissionsEvidence(
    override val capabilityId: String = "ReadDangerousPermissions",
    override val observedAt: Long = System.currentTimeMillis(),
    val permissions: List<String>
) : CapabilityEvidence

data class CacheDirectoriesEvidence(
    override val capabilityId: String = "DiscoverCacheDirectories",
    override val observedAt: Long = System.currentTimeMillis(),
    val paths: List<String>
) : CapabilityEvidence

data class PackageCountEvidence(
    override val capabilityId: String = "ReadPackageCount",
    override val observedAt: Long = System.currentTimeMillis(),
    val count: Int
) : CapabilityEvidence