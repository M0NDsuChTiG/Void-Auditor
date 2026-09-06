package com.kuzyamond.voidauditor.core.evidence

/**
 * System/audit-related evidence from capabilities like ReadUserIdentity,
 * ReadSetting, ReadSystemProp, ReadAppOps, ReadServiceState.
 */
sealed interface SystemEvidence : Evidence

data class UserIdentityEvidence(
    override val capabilityId: String = "ReadUserIdentity",
    override val capturedAt: Long = System.currentTimeMillis(),
    val uid: String?,
    val gid: String?,
    val groups: List<String>
) : SystemEvidence

data class SettingEvidence(
    override val capabilityId: String = "ReadSetting",
    override val capturedAt: Long = System.currentTimeMillis(),
    val namespace: String,
    val key: String,
    val value: String?
) : SystemEvidence

data class SystemPropEvidence(
    override val capabilityId: String = "ReadSystemProp",
    override val capturedAt: Long = System.currentTimeMillis(),
    val prop: String,
    val value: String?
) : SystemEvidence

data class AppOpsEvidence(
    override val capabilityId: String = "ReadAppOps",
    override val capturedAt: Long = System.currentTimeMillis(),
    val op: String,
    val output: String
) : SystemEvidence

data class ServiceStateEvidence(
    override val capabilityId: String = "ReadServiceState",
    override val capturedAt: Long = System.currentTimeMillis(),
    val service: String,
    val output: String
) : SystemEvidence
