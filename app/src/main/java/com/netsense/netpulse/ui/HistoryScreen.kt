package com.netsense.netpulse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netsense.netpulse.data.DiagnosticLogEntity
import com.netsense.netpulse.ui.theme.NetPulseSurface
import com.netsense.netpulse.ui.theme.NetPulseTextPrimary
import com.netsense.netpulse.ui.theme.NetPulseTextSecondary
import com.netsense.netpulse.ui.theme.NetPulseTextTertiary
import com.netsense.netpulse.ui.theme.StatusDegraded
import com.netsense.netpulse.ui.theme.StatusDegradedBg
import com.netsense.netpulse.ui.theme.StatusOptimal
import com.netsense.netpulse.ui.theme.StatusOptimalBg
import com.netsense.netpulse.ui.theme.StatusUnusable
import com.netsense.netpulse.ui.theme.StatusUnusableBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A single plain-language incident derived from real DiagnosticLogEntity samples - never
 * invented. [causeLabel] reuses the same primaryDiagnosis text UsabilityEngine already
 * produces; this screen only groups and re-presents it, it doesn't diagnose anything new.
 */
private data class IncidentSummary(
    val title: String,
    val startTimestamp: Long,
    val durationMs: Long,
    val causeLabel: String,
    val recovered: Boolean,
    val isZombie: Boolean
)

@Composable
fun HistoryScreen(
    uiState: DashboardUiState,
    onNavigateToAdvanced: () -> Unit
) {
    val incidents = buildIncidentSummaries(uiState.persistentLogs)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NetPulseTextPrimary
            )
            Text(
                text = "Connection problems NetPulse has seen and fixed",
                style = MaterialTheme.typography.bodySmall,
                color = NetPulseTextSecondary
            )
        }

        if (incidents.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .testTag("history_empty_state"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = NetPulseTextTertiary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No connection problems yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NetPulseTextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("history_incident_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(incidents) { incident ->
                    IncidentCard(incident)
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun IncidentCard(incident: IncidentSummary) {
    val (badgeColor, badgeBg) = when {
        incident.isZombie -> StatusUnusable to StatusUnusableBg
        !incident.recovered -> StatusDegraded to StatusDegradedBg
        else -> StatusOptimal to StatusOptimalBg
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NetPulseSurface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_incident_card")
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (incident.recovered) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = incident.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = NetPulseTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatIncidentTime(incident.startTimestamp)} · ${formatDuration(incident.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NetPulseTextTertiary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Cause: ${incident.causeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NetPulseTextSecondary
                )
                Text(
                    text = if (incident.recovered) "Recovery: Successful" else "Recovery: Ongoing",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor
                )
            }
        }
    }
}

/**
 * Groups contiguous "bad" samples (zombie connection or score below the DEGRADED cutoff, the
 * same 60-point threshold UsabilityRating.GOOD already uses) into incidents. This is a
 * presentation grouping only - it does not re-diagnose anything UsabilityEngine didn't already
 * decide, and every field it shows (timestamp, duration, cause) comes directly from the stored
 * DiagnosticLogEntity rows.
 */
private fun buildIncidentSummaries(logs: List<DiagnosticLogEntity>): List<IncidentSummary> {
    if (logs.isEmpty()) return emptyList()
    val chronological = logs.sortedBy { it.timestamp }

    val incidents = mutableListOf<IncidentSummary>()
    var runStart: DiagnosticLogEntity? = null
    var runEnd: DiagnosticLogEntity? = null

    fun flush(recoveredAfter: Boolean) {
        val start = runStart ?: return
        val end = runEnd ?: start
        incidents.add(
            IncidentSummary(
                title = if (start.isZombieConnection) "Internet outage" else "Connection degraded",
                startTimestamp = start.timestamp,
                durationMs = (end.timestamp - start.timestamp).coerceAtLeast(0L),
                causeLabel = start.rootCauseSummary,
                recovered = recoveredAfter,
                isZombie = start.isZombieConnection
            )
        )
        runStart = null
        runEnd = null
    }

    for (log in chronological) {
        val isBad = log.isZombieConnection || log.score < 60
        if (isBad) {
            if (runStart == null) runStart = log
            runEnd = log
        } else if (runStart != null) {
            flush(recoveredAfter = true)
        }
    }
    // A run still open at the end of the log means the last known sample was still bad -
    // report it honestly as ongoing rather than claiming a recovery that hasn't been observed.
    if (runStart != null) flush(recoveredAfter = false)

    return incidents.sortedByDescending { it.startTimestamp }
}

private fun formatIncidentTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(now)) ==
        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(timestampMs))
    return if (sameDay) {
        "Today · ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))}"
    } else {
        SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(timestampMs))
    }
}
