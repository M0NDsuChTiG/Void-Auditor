package com.kuzyamond.voidauditor.core.evidence

/**
 * App/package-related evidence from capabilities like ReadPackageDetails,
 * QueryPackages, ReadPackageCount, ReadSystemFeatures, DumpService.
 */
sealed interface AppEvidence : Evidence

data class PackageDetailsEvidence(
    override val capabilityId: String = "ReadPackageDetails",
    override val capturedAt: Long = System.currentTimeMillis(),
    val packageName: String,
    val versionName: String?,
    val versionCode: Long?,
    val installerPackageName: String?,
    val permissions: List<String>
) : AppEvidence

data class PackageListEvidence(
    override val capabilityId: String = "QueryPackages",
    override val capturedAt: Long = System.currentTimeMillis(),
    val packages: List<String>
) : AppEvidence

data class PackageCountEvidence(
    override val capabilityId: String = "ReadPackageCount",
    override val capturedAt: Long = System.currentTimeMillis(),
    val count: Int
) : AppEvidence

data class FeaturesEvidence(
    override val capabilityId: String = "ReadSystemFeatures",
    override val capturedAt: Long = System.currentTimeMillis(),
    val features: List<String>
) : AppEvidence

data class ServiceDumpEvidence(
    override val capabilityId: String = "DumpService",
    override val capturedAt: Long = System.currentTimeMillis(),
    val service: String,
    val output: String
) : AppEvidence
