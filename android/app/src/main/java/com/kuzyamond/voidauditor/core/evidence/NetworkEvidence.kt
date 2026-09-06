package com.kuzyamond.voidauditor.core.evidence

/**
 * Network-related evidence from capabilities like ReadDefaultRoute,
 * ReadWifiInfo, ReadARPTable, PingSweep.
 */
sealed interface NetworkEvidence : Evidence

data class DefaultRouteEvidence(
    override val capabilityId: String = "ReadDefaultRoute",
    override val capturedAt: Long = System.currentTimeMillis(),
    val interfaceName: String?,
    val gateway: String?
) : NetworkEvidence

data class WifiEvidence(
    override val capabilityId: String = "ReadWifiInfo",
    override val capturedAt: Long = System.currentTimeMillis(),
    val ssid: String?,
    val bssid: String?
) : NetworkEvidence

data class ARPTableEvidence(
    override val capabilityId: String = "ReadARPTable",
    override val capturedAt: Long = System.currentTimeMillis(),
    val entries: List<ARPEntry>
) : NetworkEvidence

data class ARPEntry(
    val ipAddress: String,
    val hwType: String?,
    val flags: String?,
    val hwAddress: String?,
    val mask: String?,
    val device: String?
)

data class PingSweepEvidence(
    override val capabilityId: String = "PingSweep",
    override val capturedAt: Long = System.currentTimeMillis(),
    val aliveHosts: List<String>
) : NetworkEvidence
