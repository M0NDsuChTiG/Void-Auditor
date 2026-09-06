package com.kuzyamond.voidauditor.core.evidence

/**
 * Permission-related evidence from capabilities like ReadDangerousPermissions.
 */
sealed interface PermissionEvidence : Evidence

data class DangerousPermissionsEvidence(
    override val capabilityId: String = "ReadDangerousPermissions",
    override val capturedAt: Long = System.currentTimeMillis(),
    val permissions: List<String>
) : PermissionEvidence
