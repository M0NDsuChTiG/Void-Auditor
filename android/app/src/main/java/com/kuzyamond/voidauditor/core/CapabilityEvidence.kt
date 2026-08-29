package com.kuzyamond.voidauditor.core

sealed interface CapabilityEvidence {
    val capabilityId: String
    val capturedAt: Long
}

// Structured evidence types
data class DefaultRouteEvidence(
    override val capabilityId: String = "ReadDefaultRoute",
    override val capturedAt: Long = System.currentTimeMillis(),
    val interfaceName: String?,
    val gateway: String?
) : CapabilityEvidence

data class WifiEvidence(
    override val capabilityId: String = "ReadWifiInfo",
    override val capturedAt: Long = System.currentTimeMillis(),
    val ssid: String?,
    val bssid: String?
) : CapabilityEvidence

data class PackageDetailsEvidence(
    override val capabilityId: String = "ReadPackageDetails",
    override val capturedAt: Long = System.currentTimeMillis(),
    val packageName: String,
    val versionName: String?,
    val versionCode: Long?,
    val installerPackageName: String?,
    val permissions: List<String>
) : CapabilityEvidence

data class ARPTableEvidence(
    override val capabilityId: String = "ReadARPTable",
    override val capturedAt: Long = System.currentTimeMillis(),
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
    override val capturedAt: Long = System.currentTimeMillis(),
    val uid: String?,
    val gid: String?,
    val groups: List<String>
) : CapabilityEvidence

data class SettingEvidence(
    override val capabilityId: String = "ReadSetting",
    override val capturedAt: Long = System.currentTimeMillis(),
    val namespace: String,
    val key: String,
    val value: String?
) : CapabilityEvidence

data class PingSweepEvidence(
    override val capabilityId: String = "PingSweep",
    override val capturedAt: Long = System.currentTimeMillis(),
    val aliveHosts: List<String>
) : CapabilityEvidence

data class PackageListEvidence(
    override val capabilityId: String = "QueryPackages",
    override val capturedAt: Long = System.currentTimeMillis(),
    val packages: List<String>
) : CapabilityEvidence

data class ServiceDumpEvidence(
    override val capabilityId: String = "DumpService",
    override val capturedAt: Long = System.currentTimeMillis(),
    val service: String,
    val output: String
) : CapabilityEvidence

data class RawOutputEvidence(
    override val capabilityId: String,
    override val capturedAt: Long = System.currentTimeMillis(),
    val output: String
) : CapabilityEvidence

// Collection evidence
data class FeaturesEvidence(
    override val capabilityId: String = "ReadSystemFeatures",
    override val capturedAt: Long = System.currentTimeMillis(),
    val features: List<String>
) : CapabilityEvidence

data class DangerousPermissionsEvidence(
    override val capabilityId: String = "ReadDangerousPermissions",
    override val capturedAt: Long = System.currentTimeMillis(),
    val permissions: List<String>
) : CapabilityEvidence

data class CacheDirectoriesEvidence(
    override val capabilityId: String = "DiscoverCacheDirectories",
    override val capturedAt: Long = System.currentTimeMillis(),
    val paths: List<String>
) : CapabilityEvidence

data class PackageCountEvidence(
    override val capabilityId: String = "ReadPackageCount",
    override val capturedAt: Long = System.currentTimeMillis(),
    val count: Int
) : CapabilityEvidence

data class DiskUsageEvidence(
    override val capabilityId: String = "ReadDiskUsage",
    override val capturedAt: Long = System.currentTimeMillis(),
    val kilobytes: Long?
) : CapabilityEvidence

data class DirectorySizeEvidence(
    override val capabilityId: String = "ReadDirectorySize",
    override val capturedAt: Long = System.currentTimeMillis(),
    val bytes: Long?
) : CapabilityEvidence

data class FileCountEvidence(
    override val capabilityId: String = "ReadFileCount",
    override val capturedAt: Long = System.currentTimeMillis(),
    val count: Int?
) : CapabilityEvidence

data class LastModifiedEvidence(
    override val capabilityId: String = "ReadLastModified",
    override val capturedAt: Long = System.currentTimeMillis(),
    val timestamp: Long?
) : CapabilityEvidence

data class SystemPropEvidence(
    override val capabilityId: String = "ReadSystemProp",
    override val capturedAt: Long = System.currentTimeMillis(),
    val prop: String,
    val value: String?
) : CapabilityEvidence

data class AppOpsEvidence(
    override val capabilityId: String = "ReadAppOps",
    override val capturedAt: Long = System.currentTimeMillis(),
    val op: String,
    val output: String
) : CapabilityEvidence

data class ServiceStateEvidence(
    override val capabilityId: String = "ReadServiceState",
    override val capturedAt: Long = System.currentTimeMillis(),
    val service: String,
    val output: String
) : CapabilityEvidence