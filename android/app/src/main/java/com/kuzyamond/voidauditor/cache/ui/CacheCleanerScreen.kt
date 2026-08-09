package com.kuzyamond.voidauditor.cache.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuzyamond.voidauditor.RiskLevel
import com.kuzyamond.voidauditor.getRiskColor
import com.kuzyamond.voidauditor.AuditButton
import com.kuzyamond.voidauditor.CyberCard
import com.kuzyamond.voidauditor.cache.CacheCapability
import com.kuzyamond.voidauditor.cache.CacheCleaner
import com.kuzyamond.voidauditor.cache.CacheScanner
import com.kuzyamond.voidauditor.cache.CleanResult
import com.kuzyamond.voidauditor.cache.models.CacheEntry
import com.kuzyamond.voidauditor.cache.models.CacheStats
import com.kuzyamond.voidauditor.core.CacheScanStorage
import com.kuzyamond.voidauditor.core.Capability
import com.kuzyamond.voidauditor.core.ConfirmationManager
import kotlinx.coroutines.launch

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.2f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.2f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val detail: String) : ScanState
    data class Complete(
        val stats: CacheStats,
        val result: CleanResult?,
        val scanId: String
    ) : ScanState
}

@Composable
fun PulsingIndicator(cyberWarning: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(cyberWarning.copy(alpha = alpha))
    )
}

@Composable
fun CacheCleanerScreen(
    cyberBackground: Color,
    cyberSurface: Color,
    cyberAccent: Color,
    cyberWarning: Color,
    cyberError: Color,
    cyberText: Color
) {
    var scanState by CacheScanStorage::scanState
    var selectedCapability by CacheScanStorage::selectedCapability
    var selectedPaths by CacheScanStorage::selectedPaths
    var expandedPackages by CacheScanStorage::expandedPackages
    val scope = rememberCoroutineScope()
    val bodyColor = cyberText.copy(alpha = 0.85f)
    val labelColor = cyberText.copy(alpha = 0.5f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cyberBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "CACHE_CLEANER v1",
            color = cyberAccent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Orphaned cache analysis and targeted purge",
            color = labelColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(20.dp))

        CapabilitySelector(
            selected = selectedCapability,
            onSelect = {
                selectedCapability = it
                scanState = ScanState.Idle
                selectedPaths = emptySet()
            },
            cyberAccent = cyberAccent,
            cyberWarning = cyberWarning,
            cyberSurface = cyberSurface,
            bodyColor = bodyColor,
            labelColor = labelColor
        )
        Spacer(Modifier.height(24.dp))

        when (val state = scanState) {
            is ScanState.Idle -> {
                Button(
                    onClick = {
                        scanState = ScanState.Scanning("Initialising...")
                        scope.launch {
                            try {
                                val stats = CacheScanner.scan(selectedCapability)
                                scanState = ScanState.Complete(stats, null, System.currentTimeMillis().toString())
                            } catch (e: Exception) {
                                scanState = ScanState.Idle
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = cyberAccent)
                ) {
                    Text(
                        "> INITIATE_SCAN",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            is ScanState.Scanning -> {
                ScanningView(state.detail, cyberWarning, cyberAccent, bodyColor)
            }

            is ScanState.Complete -> {
                val allEntries = remember(state.stats) {
                    state.stats.entriesByPackage.values.flatten()
                }
                LaunchedEffect(state.scanId) {
                    selectedPaths = allEntries.filter { it.isSafeToDelete }.map { it.path }.toSet()
                }
                ResultView(
                    stats = state.stats,
                    result = state.result,
                    allEntries = allEntries,
                    selectedPaths = selectedPaths,
                    onSelectedPathsChange = { selectedPaths = it },
                    expandedPackages = expandedPackages,
                    onTogglePackage = { pkg ->
                        expandedPackages = if (pkg in expandedPackages) expandedPackages - pkg
                        else expandedPackages + pkg
                    },
                    onDryRun = {
                        ConfirmationManager.requestConfirmation(
                            intent = Capability.RunShellCommand("cache_dryrun_${selectedCapability.name}"),
                            onConfirm = {
                                scope.launch {
                                    val cleanResult = CacheCleaner.clean(selectedPaths.toList(), selectedCapability, dryRun = true)
                                    scanState = ScanState.Complete(state.stats, cleanResult, state.scanId)
                                }
                            }
                        )
                    },
                    onPurge = {
                        ConfirmationManager.requestConfirmation(
                            intent = Capability.RunShellCommand("cache_clean_${selectedCapability.name}"),
                            requiredPhrase = "PURGE",
                            onConfirm = {
                                scope.launch {
                                    val cleanResult = CacheCleaner.clean(selectedPaths.toList(), selectedCapability, dryRun = false)
                                    scanState = ScanState.Complete(state.stats, cleanResult, state.scanId)
                                }
                            }
                        )
                    },
                    onSystemTrim = {
                        ConfirmationManager.requestConfirmation(
                            intent = Capability.RunShellCommand("pm trim-caches 500M"),
                            requiredPhrase = "TRIM",
                            onConfirm = {
                                scope.launch {
                                    val trimResult = CacheCleaner.systemTrim("500M")
                                    scanState = ScanState.Complete(state.stats, trimResult, state.scanId)
                                }
                            }
                        )
                    },
                    onRescan = {
                        scanState = ScanState.Idle
                        selectedPaths = emptySet()
                    },
                    cyberBackground = cyberBackground,
                    cyberSurface = cyberSurface,
                    cyberAccent = cyberAccent,
                    cyberWarning = cyberWarning,
                    cyberError = cyberError,
                    cyberText = cyberText,
                    bodyColor = bodyColor,
                    labelColor = labelColor
                )
            }
        }
    }
}

@Composable
private fun CapabilitySelector(
    selected: CacheCapability,
    onSelect: (CacheCapability) -> Unit,
    cyberAccent: Color,
    cyberWarning: Color,
    cyberSurface: Color,
    bodyColor: Color,
    labelColor: Color
) {
    Text("SCAN_CAPABILITY", color = cyberAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
            CacheCapability.entries.filter { it != CacheCapability.SYSTEM_TRIM }.forEach { cap ->
            val isSelected = cap == selected
            val color = when (cap) {
                CacheCapability.QUICK -> cyberAccent
                CacheCapability.FULL -> cyberWarning
                CacheCapability.DEEP -> Color(0xFFFF2D55)
                CacheCapability.SYSTEM_TRIM -> Color(0xFFFF8C00)
            }
            OutlinedButton(
                onClick = { onSelect(cap) },
                modifier = Modifier.weight(1f),
                border = androidx.compose.foundation.BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) color else color.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isSelected) color else labelColor,
                    containerColor = if (isSelected) color.copy(alpha = 0.1f) else Color.Transparent
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(cap.displayName, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(cap.description, fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = labelColor)
                }
            }
        }
    }
}

@Composable
private fun ScanningView(
    detail: String,
    cyberWarning: Color,
    cyberAccent: Color,
    bodyColor: Color
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PulsingIndicator(cyberWarning)
            Spacer(Modifier.height(16.dp))
            Text(
                "SCANNING...",
                color = cyberAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(8.dp))
            Text(detail, color = bodyColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ResultView(
    stats: CacheStats,
    result: CleanResult?,
    allEntries: List<CacheEntry>,
    selectedPaths: Set<String>,
    onSelectedPathsChange: (Set<String>) -> Unit,
    expandedPackages: Set<String>,
    onTogglePackage: (String) -> Unit,
    onDryRun: () -> Unit,
    onPurge: () -> Unit,
    onSystemTrim: () -> Unit,
    onRescan: () -> Unit,
    cyberBackground: Color,
    cyberSurface: Color,
    cyberAccent: Color,
    cyberWarning: Color,
    cyberError: Color,
    cyberText: Color,
    bodyColor: Color,
    labelColor: Color
) {
    val riskColor = getRiskColor(stats.riskLevel)
    val sortedPkgs = remember(stats) {
        stats.entriesByPackage.entries.sortedByDescending { it.value.sumOf { e -> e.sizeBytes } }
    }
    val safeEntries = remember(allEntries) { allEntries.filter { it.isSafeToDelete } }
    val onlyExternal = remember(stats) {
        stats.entriesByPackage.values.flatten().all { it.path.startsWith("/sdcard/") || it.path.startsWith("/storage/") }
    }

    CyberCard(title = "SCAN_RESULTS", color = cyberAccent, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("SIZE", formatBytes(selectedPaths.sumOf { path ->
                    allEntries.find { it.path == path }?.sizeBytes ?: 0L
                }), bodyColor, labelColor)
                StatItem("FILES", "${stats.totalFiles}", bodyColor, labelColor)
                StatItem("DIRS", "${stats.totalEntries}", bodyColor, labelColor)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem("DURATION", "${stats.scanDurationMs}ms", bodyColor, labelColor)
                StatItem("RISK", stats.riskLevel.name, riskColor, labelColor)
                StatItem("PKGS", "${stats.entriesByPackage.size}", bodyColor, labelColor)
            }
        }
    }
    if (onlyExternal) {
        val installedText = if (stats.installedPackages > 0) " of ${stats.installedPackages} installed" else ""
        Spacer(Modifier.height(8.dp))
        Text(
            "EXTERNAL_CACHE_ONLY: ${stats.entriesByPackage.size}$installedText pkgs visible. Internal /data/data requires root or SYSTEM_TRIM.",
            color = cyberWarning,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
    Spacer(Modifier.height(16.dp))

    if (result != null) {
        val actionLabel = if (result.dryRun) "DRY_RUN_RESULT" else "PURGE_RESULT"
        val actionColor = if (result.dryRun) cyberWarning else cyberError
        CyberCard(title = actionLabel, color = actionColor, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatItem("CLEANED", "${result.cleanedDirs.size}", bodyColor, labelColor)
                    StatItem("DELETED", "${result.deletedFiles}", bodyColor, labelColor)
                    StatItem("FREED", formatBytes(result.freedBytes), bodyColor, labelColor)
                }
                Spacer(Modifier.height(4.dp))
                StatItem("DURATION", "${result.durationMs}ms", bodyColor, labelColor)
                if (result.errors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("ERRORS: ${result.errors.size}", color = cyberError, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    result.errors.take(5).forEach { err ->
                        Text(err, color = cyberError.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (result == null) {
        if (allEntries.isEmpty()) {
            Text(
                "No cache directories were discovered.\nThis may occur due to shell (SELinux) restrictions hiding /data/data paths.\nTry FULL or DEEP mode for broader visibility.",
                color = cyberWarning,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(16.dp))
        } else {
            Text("PACKAGES", color = cyberAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selectedPaths.containsAll(safeEntries.map { it.path }),
                        onCheckedChange = { checked ->
                            onSelectedPathsChange(
                                if (checked) safeEntries.map { it.path }.toSet() else emptySet()
                            )
                        },
                        colors = CheckboxDefaults.colors(checkedColor = cyberAccent, uncheckedColor = labelColor)
                    )
                    Text("SELECT ALL", color = labelColor, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Text(
                    "${selectedPaths.size} / ${safeEntries.size} selected",
                    color = bodyColor,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(8.dp))

            sortedPkgs.forEach { (pkg, entries) ->
                val pkgSize = entries.sumOf { it.sizeBytes }
                val pkgFiles = entries.sumOf { it.fileCount }
                val isExpanded = pkg in expandedPackages

                CyberCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTogglePackage(pkg) },
                    color = cyberAccent
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pkg, color = cyberAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("${formatBytes(pkgSize)} | ${pkgFiles} files", color = labelColor, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                        Text(
                            if (isExpanded) "[-]" else "[+]",
                            color = cyberAccent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Divider(color = cyberAccent.copy(alpha = 0.2f))
                            Spacer(Modifier.height(4.dp))
                            entries.sortedByDescending { it.sizeBytes }.forEach { entry ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (entry.isSafeToDelete) {
                                            Checkbox(
                                                checked = entry.path in selectedPaths,
                                                onCheckedChange = { checked ->
                                                    onSelectedPathsChange(
                                                        if (checked) selectedPaths + entry.path
                                                        else selectedPaths - entry.path
                                                    )
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = cyberAccent, uncheckedColor = labelColor)
                                            )
                                        } else {
                                            Text("\uD83D\uDD12 BLOCKED", color = cyberWarning, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            entry.path.split("/").lastOrNull() ?: entry.path,
                                            color = if (entry.isSafeToDelete) bodyColor else labelColor,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        formatBytes(entry.sizeBytes),
                                        color = labelColor,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val targetSizeText = when {
            result != null && !result.dryRun -> "Already purged"
            result != null && result.dryRun -> formatBytes(result.freedBytes)
            else -> formatBytes(selectedPaths.sumOf { path ->
                allEntries.find { it.path == path }?.sizeBytes ?: 0L
            })
        }
        val hasSelected = selectedPaths.isNotEmpty()

        Column(modifier = Modifier.weight(1f)) {
            AuditButton(
                label = "DRY_RUN ($targetSizeText)",
                color = cyberWarning,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasSelected && (result == null || result.dryRun),
                onClick = onDryRun
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            AuditButton(
                label = "PURGE ($targetSizeText)",
                color = cyberError,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasSelected && (result == null || result.dryRun),
                onClick = onPurge
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    AuditButton(
        label = "SYSTEM_TRIM (pm trim-caches 500M)",
        color = cyberAccent,
        modifier = Modifier.fillMaxWidth(),
        enabled = (result == null || result.dryRun),
        onClick = onSystemTrim
    )

    Spacer(Modifier.height(8.dp))

    OutlinedButton(
        onClick = onRescan,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, cyberAccent.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = cyberAccent)
    ) {
        Text("> RESCAN", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, color = labelColor, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

