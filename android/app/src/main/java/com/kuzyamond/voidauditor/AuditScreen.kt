package com.kuzyamond.voidauditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuzyamond.voidauditor.core.AuditEvent
import com.kuzyamond.voidauditor.core.AuditLogger
import com.kuzyamond.voidauditor.core.AuditStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen() {
    val events = remember { mutableStateListOf<AuditEvent>() }
    val stats = remember { mutableStateOf(AuditStats(0, 0, 0, 0, 0, 0, 0)) }
    var filter by remember { mutableStateOf("ALL") }

    fun refresh() {
        events.clear()
        events.addAll(AuditLogger.getAllEvents())
        stats.value = AuditLogger.getStats()
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // --- STATS ROW ---
        CyberCard(title = "AUDIT_TRAIL_STATS", color = CyberAccent) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBox("TOTAL", stats.value.total, CyberAccent)
                    StatBox("OK", stats.value.allowed + stats.value.confirmed, CyberAccent2)
                    StatBox("DENIED", stats.value.denied + stats.value.blocked, CyberWarning)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBox("CRIT", stats.value.criticalCount, Color(0xFFFF2D55))
                    StatBox("HIGH", stats.value.highCount, Color(0xFFFF9500))
                    StatBox("PASS", stats.value.total - stats.value.criticalCount - stats.value.highCount, CyberAccent)
                }

                Spacer(Modifier.height(6.dp))

                // --- FILTERS ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL", "ALLOWED", "DENIED", "CRITICAL").forEach { label ->
                        FilterChip(
                            selected = filter == label,
                            onClick = { filter = label },
                            label = { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberAccent.copy(alpha = 0.2f),
                                selectedLabelColor = CyberAccent
                            )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            AuditLogger.clear()
                            refresh()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = CyberWarning
                        ),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(2.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text("CLEAR", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { refresh() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = CyberAccent
                        ),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(2.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text("REFRESH", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- EVENTS LIST ---
        CyberCard(title = "EVENT_LOG", color = CyberAccent) {
            val filtered = remember(events, filter) {
                when (filter) {
                    "ALL" -> events
                    "ALLOWED" -> events.filter { it.decision == "ALLOWED" || it.decision == "CONFIRMED" }
                    "DENIED" -> events.filter { it.decision == "DENIED" || it.decision == "BLOCKED" }
                    "CRITICAL" -> events.filter { it.riskLevel == RiskLevel.CRITICAL || it.riskLevel == RiskLevel.HIGH }
                    else -> events
                }
            }

            if (filtered.isEmpty()) {
                Text(
                    "NO_EVENTS",
                    color = Color(0xFF475569),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 20.dp).fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filtered) { event ->
                        EventRow(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            label,
            color = color.copy(alpha = 0.7f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EventRow(event: AuditEvent) {
    val bgColor = when (event.decision) {
        "DENIED", "BLOCKED" -> Color(0xFFFF2D55).copy(alpha = 0.05f)
        "CONFIRMED" -> Color(0xFFFFCC00).copy(alpha = 0.05f)
        else -> Color.Transparent
    }
    val decisionColor = when (event.decision) {
        "ALLOWED" -> CyberAccent2
        "CONFIRMED" -> Color(0xFFFFCC00)
        "DENIED" -> Color(0xFFFF2D55)
        "BLOCKED" -> Color(0xFFFF9500)
        else -> Color(0xFF64748B)
    }
    val riskColor = when (event.riskLevel) {
        RiskLevel.CRITICAL -> Color(0xFFFF2D55)
        RiskLevel.HIGH -> Color(0xFFFF9500)
        RiskLevel.MEDIUM -> Color(0xFFFFCC00)
        else -> Color(0xFF64748B)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .border(0.5.dp, Color(0xFF1E293B))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            event.formattedTime,
            color = Color(0xFF475569),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(60.dp)
        )
        Text(
            event.actor.name.take(3),
            color = CyberAccent,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(24.dp)
        )
        Text(
            event.severityLabel,
            color = riskColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(32.dp)
        )
        Text(
            event.decision,
            color = decisionColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(50.dp)
        )
        Text(
            event.capability,
            color = Color(0xFFCBD5E1),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (event.durationMs != null) {
            Text(
                "${event.durationMs}ms",
                color = Color(0xFF64748B),
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
