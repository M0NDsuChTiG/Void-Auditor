package com.kuzyamond.voidauditor.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kuzyamond.voidauditor.cache.CacheCapability
import com.kuzyamond.voidauditor.cache.ui.ScanState
import com.kuzyamond.voidauditor.network.NetworkProfile
import com.kuzyamond.voidauditor.network.ScanResult

object CacheScanStorage {
    var scanState by mutableStateOf<ScanState>(ScanState.Idle)
    var selectedCapability by mutableStateOf<CacheCapability>(CacheCapability.QUICK)
    var selectedPaths by mutableStateOf<Set<String>>(emptySet())
    var expandedPackages by mutableStateOf<Set<String>>(emptySet())
}

object NetScanStorage {
    var profile by mutableStateOf<NetworkProfile?>(null)
    var scanResult by mutableStateOf<ScanResult?>(null)
    var isScanning by mutableStateOf(false)
    var scanProgress by mutableStateOf(0f)
    var progressText by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
}