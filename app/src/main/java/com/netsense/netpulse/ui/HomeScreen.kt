package com.netsense.netpulse.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netsense.netpulse.data.DiagnosticLogEntity
import com.netsense.netpulse.model.ConnectionPresentationMapper
import com.netsense.netpulse.model.ConnectionVisual
import com.netsense.netpulse.model.HealerActionItem
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.ui.theme.NetPulseAccent
import com.netsense.netpulse.ui.theme.NetPulseSurface
import com.netsense.netpulse.ui.theme.NetPulseTextPrimary
import com.netsense.netpulse.ui.theme.NetPulseTextSecondary
import com.netsense.netpulse.ui.theme.NetPulseTextTertiary
import com.netsense.netpulse.ui.theme.StatusOptimal
import com.netsense.netpulse.ui.theme.StatusUnusable
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The new primary experience (redesign Section 4): a calm, spacious "is my Internet working"
 * answer within about a second, with exactly one action when something is wrong - Fix It -
 * driven by PulsePolicy's real recommendation, never a fabricated one. Everything technical
 * lives one tap away under "Advanced details".
 */
@Composable
fun HomeScreen(
    uiState: DashboardUiState,
    hasLocationPermission: Boolean,
    isLocationServicesEnabled: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onFixIt: () -> Unit,
    onRetryFix: () -> Unit,
    onDismissHealingOutcome: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val presentation = ConnectionPresentationMapper.map(
        status = uiState.policyDecision.state,
        snapshot = uiState.snapshot,
        scoreResult = uiState.scoreResult
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (!hasLocationPermission || !isLocationServicesEnabled) {
            item {
                MinimalPermissionBanner(
                    onRequest = if (!hasLocationPermission) onRequestPermissions else onOpenLocationSettings,
                    title = if (!hasLocationPermission) "Detailed signal info unavailable" else "Location Services off",
                    body = "Android needs Location permission and Location Services on for detailed cellular signal info. Your Internet health still works without it.",
                    buttonLabel = if (!hasLocationPermission) "Grant" else "Turn On"
                )
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            AnimatedContent(
                targetState = uiState.healingOutcome,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
                label = "healing_outcome_or_hero"
            ) { outcome ->
                if (outcome != null) {
                    HealingOutcomeCard(
                        succeeded = outcome.succeeded,
                        durationMs = outcome.durationMs,
                        message = outcome.message,
                        carrierName = uiState.snapshot.carrierName,
                        networkLabel = uiState.snapshot.cellularDataNetworkType,
                        transport = uiState.snapshot.primaryTransport,
                        onDone = onDismissHealingOutcome,
                        onTryAgain = onRetryFix
                    )
                } else {
                    ConnectionHeroCard(
                        score = uiState.scoreResult.score,
                        visual = presentation.visual,
                        headline = presentation.headline,
                        supportingText = presentation.supportingText,
                        showFixAction = presentation.showFixAction && !uiState.isHealing,
                        fixActionLabel = presentation.fixActionLabel,
                        isHealing = uiState.isHealing,
                        healingAction = uiState.policyDecision.recommendedAction,
                        onFixIt = onFixIt,
                        carrierName = uiState.snapshot.carrierName,
                        networkLabel = uiState.snapshot.cellularDataNetworkType,
                        transport = uiState.snapshot.primaryTransport,
                        lastCheckedTimestamp = uiState.lastCheckedTimestamp,
                        onAdvancedDetails = onNavigateToAdvanced
                    )
                }
            }
        }

        item {
            MonitoringStatusRow(isSentinelRunning = uiState.isSentinelRunning)
        }

        item {
            NetworkProgressionChartCard(logs = uiState.persistentLogs)
        }

        item {
            RecentActivityTeaser(
                logs = uiState.persistentLogs,
                onViewHistory = onNavigateToHistory
            )
        }

        item {
            AdvancedDetailsLink(onClick = onNavigateToAdvanced)
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun ConnectionHeroCard(
    score: Int,
    visual: ConnectionVisual,
    headline: String,
    supportingText: String,
    showFixAction: Boolean,
    fixActionLabel: String,
    isHealing: Boolean,
    healingAction: HealerActionItem?,
    onFixIt: () -> Unit,
    carrierName: String?,
    networkLabel: String?,
    transport: NetworkTransport,
    lastCheckedTimestamp: Long,
    onAdvancedDetails: () -> Unit
) {
    val effectiveVisual = if (isHealing) ConnectionVisual.RECOVERING else visual
    val ratingLabel = when {
        score >= 85 -> "Excellent"
        score >= 60 -> "Good"
        score >= 35 -> "Fair"
        score >= 10 -> "Poor"
        else -> "Critical"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HealthRing(
            score = score,
            visual = effectiveVisual,
            modifier = Modifier.testTag("home_health_ring")
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isHealing) {
                    Text(
                        text = "$score",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                        color = NetPulseTextPrimary
                    )
                    Text(
                        text = ratingLabel.uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextTertiary,
                        letterSpacing = 1.sp
                    )
                } else {
                    ConnectionStatusIcon(visual = ConnectionVisual.RECOVERING, modifier = Modifier.size(36.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (isHealing) "Fixing your connection..." else headline,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = NetPulseTextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("home_headline")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isHealing) {
                healingAction?.description ?: "NetPulse is working on it."
            } else {
                supportingText
            },
            style = MaterialTheme.typography.bodyMedium,
            color = NetPulseTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        if (isHealing) {
            Spacer(modifier = Modifier.height(10.dp))
            HealingProgressTicker()
        }

        if (!carrierName.isNullOrBlank() || !networkLabel.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (transport == NetworkTransport.WIFI) Icons.Default.Wifi else Icons.Default.CellTower,
                    contentDescription = null,
                    tint = NetPulseTextTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = listOfNotNull(carrierName, networkLabel).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = NetPulseTextTertiary
                )
            }
        }

        AnimatedVisibility(visible = showFixAction) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onFixIt,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusUnusable,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("home_fix_it_button")
                ) {
                    Text(fixActionLabel.uppercase(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Last checked ${relativeTime(lastCheckedTimestamp)}",
            style = MaterialTheme.typography.labelSmall,
            color = NetPulseTextTertiary
        )
    }
}

/**
 * A live elapsed-time readout with rotating micro-copy, so the (real, up to ~45s) wait for a
 * recovery attempt to settle reads as active progress rather than a frozen spinner. The
 * messages describe generic waiting states, never a fabricated specific outcome.
 */
@Composable
private fun HealingProgressTicker() {
    val startedAtMs = remember { System.currentTimeMillis() }
    var elapsedSec by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            elapsedSec = ((System.currentTimeMillis() - startedAtMs) / 1000).toInt()
            delay(1000)
        }
    }
    val messages = listOf("Waiting for the radio to reconnect", "Checking your connection", "Almost there")
    val message = messages[(elapsedSec / 8).coerceAtMost(messages.size - 1)]
    Text(
        text = "$message · ${elapsedSec}s",
        style = MaterialTheme.typography.labelMedium,
        color = NetPulseTextTertiary,
        modifier = Modifier.testTag("home_healing_ticker")
    )
}

@Composable
private fun HealingOutcomeCard(
    succeeded: Boolean,
    durationMs: Long,
    message: String,
    carrierName: String?,
    networkLabel: String?,
    transport: NetworkTransport,
    onDone: () -> Unit,
    onTryAgain: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HealthRing(
            score = if (succeeded) 100 else 0,
            visual = if (succeeded) ConnectionVisual.RECOVERED else ConnectionVisual.ALERT,
            modifier = Modifier.testTag("home_health_ring")
        ) {
            ConnectionStatusIcon(
                visual = if (succeeded) ConnectionVisual.RECOVERED else ConnectionVisual.ALERT,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (succeeded) "You're back online" else "We couldn't restore your connection",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = NetPulseTextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (succeeded) {
                "Connection recovered in ${formatDuration(durationMs)}."
            } else {
                "We found the likely problem: \"$message\""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = NetPulseTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        if (!carrierName.isNullOrBlank() || !networkLabel.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (transport == NetworkTransport.WIFI) Icons.Default.Wifi else Icons.Default.CellTower,
                    contentDescription = null,
                    tint = NetPulseTextTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = listOfNotNull(carrierName, networkLabel).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = NetPulseTextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = if (succeeded) onDone else onTryAgain,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (succeeded) StatusOptimal else NetPulseAccent,
                contentColor = Color.White
            ),
            modifier = Modifier
                .height(52.dp)
                .testTag("home_healing_done_button")
        ) {
            Text(if (succeeded) "DONE" else "TRY AGAIN", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        if (!succeeded) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Dismiss",
                style = MaterialTheme.typography.labelMedium,
                color = NetPulseTextTertiary,
                modifier = Modifier
                    .clickable { onDone() }
                    .padding(8.dp)
                    .testTag("home_healing_dismiss_button")
            )
        }
    }
}

@Composable
private fun MonitoringStatusRow(isSentinelRunning: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("home_monitoring_status")
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = if (isSentinelRunning) StatusOptimal else NetPulseTextTertiary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isSentinelRunning) {
                "Protected · NetPulse is monitoring your connection"
            } else {
                "Background monitoring is off"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (isSentinelRunning) NetPulseTextSecondary else NetPulseTextTertiary
        )
    }
}

@Composable
private fun RecentActivityTeaser(
    logs: List<DiagnosticLogEntity>,
    onViewHistory: () -> Unit
) {
    val recentProblem = logs.firstOrNull { it.isZombieConnection || it.score < 60 }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NetPulseSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewHistory() }
            .testTag("home_recent_activity_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Recent activity",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = NetPulseTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (recentProblem != null) {
                    "${recentProblem.primaryDiagnosis} · ${relativeTime(recentProblem.timestamp)}"
                } else {
                    "No problems detected."
                },
                style = MaterialTheme.typography.bodySmall,
                color = NetPulseTextSecondary
            )
        }
    }
}

@Composable
private fun AdvancedDetailsLink(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable { onClick() }
            .testTag("home_advanced_link")
    ) {
        Text(
            text = "Advanced details",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = NetPulseAccent
        )
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            tint = NetPulseAccent,
            modifier = Modifier.size(14.dp)
        )
    }
}

internal fun relativeTime(timestampMs: Long): String {
    val elapsedSec = (System.currentTimeMillis() - timestampMs) / 1000
    return when {
        elapsedSec < 5 -> "just now"
        elapsedSec < 60 -> "$elapsedSec sec ago"
        elapsedSec < 3600 -> "${elapsedSec / 60} min ago"
        elapsedSec < 86400 -> "${elapsedSec / 3600}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMs))
    }
}

internal fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    return if (seconds < 60) "$seconds seconds" else "${seconds / 60}m ${seconds % 60}s"
}
