package com.kuzyamond.voidauditor.network

data class HostInfo(
    val ip: String,
    val mac: String = "",
    val hostname: String = "",
    val vendor: String = "",
    val openPorts: List<Int> = emptyList(),
    val services: Map<Int, String> = emptyMap(),
    val rttMs: Long = -1,
    val isAlive: Boolean = false,
    val fullPortsScanned: Boolean = false // Флаг полного сканирования (1-65535)
) {
    /**
     * Помощник для объединения результатов обычного и полного сканирования
     */
    fun withUpdatedPorts(newServices: Map<Int, String>, isFull: Boolean): HostInfo {
        val mergedServices = this.services + newServices
        return this.copy(
            openPorts = mergedServices.keys.toList().sorted(),
            services = mergedServices,
            fullPortsScanned = this.fullPortsScanned || isFull
        )
    }
}