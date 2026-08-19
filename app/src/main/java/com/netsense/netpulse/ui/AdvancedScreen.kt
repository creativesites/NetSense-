package com.netsense.netpulse.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.netsense.netpulse.engine.SimulatedFaultScenario
import com.netsense.netpulse.model.DiagnosticMode
import com.netsense.netpulse.model.HealerActionItem
import com.netsense.netpulse.model.TargetCategory
import com.netsense.netpulse.ui.theme.NetPulseAccentContainer
import com.netsense.netpulse.ui.theme.NetPulseBorder
import com.netsense.netpulse.ui.theme.NetPulseOnAccentContainer
import com.netsense.netpulse.ui.theme.NetPulseTextPrimary
import com.netsense.netpulse.ui.theme.NetPulseTextSecondary

/**
 * The home for every existing engineering screen (Section 19/29 of the redesign spec):
 * the full technical Overview, Diagnostics, RF Radar, Speed Test, Healer, Logs & Audit, and
 * AI Copilot. None of these screens' internal logic changed - this host re-parents the same
 * OverviewTabContent/DiagnosticsTabContent/RadarTabContent/SpeedTestTabContent/
 * HealerTabContent/AnalyticsLogsTabContent composables one navigation layer deeper behind the
 * consumer Home experience, and wraps each with the same calm header + spacing + motion
 * language Home uses, so "Advanced" doesn't feel like a different, older app.
 */
@Composable
fun AdvancedScreen(
    uiState: DashboardUiState,
    onSelectTab: (DashboardTab) -> Unit,
    onRunProbe: (DiagnosticMode) -> Unit,
    onSelectMode: (DiagnosticMode) -> Unit,
    onStartSpeedTest: () -> Unit,
    onCancelSpeedTest: () -> Unit,
    onSelectSimulation: (SimulatedFaultScenario) -> Unit,
    onExportCsv: () -> Unit,
    onExportMlDataset: () -> Unit,
    onClearHistory: () -> Unit,
    onDeleteLog: (Long) -> Unit,
    onRunPingMatrix: () -> Unit,
    onRunDualStackCheck: () -> Unit,
    onRunHopTrace: (String) -> Unit,
    onSelectCategoryFilter: (TargetCategory?) -> Unit,
    onExecuteHealerAction: (HealerActionItem) -> Unit,
    onGenerateIncidentReport: () -> Unit,
    onShareIncidentReport: () -> Unit,
    onRunBenchmark: () -> Unit,
    onConsultAi: (String) -> Unit,
    onClearAiConsultation: () -> Unit,
    onToggleSentinel: () -> Unit,
    hasLocationPermission: Boolean,
    isLocationServicesEnabled: Boolean,
    onRequestLocationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    context: Context
) {
    val (icon, description) = when (uiState.selectedTab) {
        DashboardTab.OVERVIEW -> Icons.Default.Dashboard to "The full technical picture in one place"
        DashboardTab.DIAGNOSTICS -> Icons.Default.NetworkCheck to "DNS, TCP, and HTTP probe results"
        DashboardTab.RADAR -> Icons.Default.CellTower to "Wi-Fi and cellular RF telemetry"
        DashboardTab.SPEED_TEST -> Icons.Default.Speed to "Measure real throughput"
        DashboardTab.HEALER -> Icons.Default.Build to "Manual fixes and fault simulation"
        DashboardTab.ANALYTICS -> Icons.Default.ListAlt to "Raw logs, exports, and incident reports"
        DashboardTab.COPILOT -> Icons.Default.AutoAwesome to "Ask NetPulse about your connection"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MinimalistScrollableTabNavigation(
            selectedTab = uiState.selectedTab,
            onSelectTab = onSelectTab
        )

        HorizontalDivider(color = NetPulseBorder, thickness = 1.dp)

        AdvancedSectionHeader(icon = icon, title = uiState.selectedTab.label, description = description)

        AnimatedContent(
            targetState = uiState.selectedTab,
            transitionSpec = {
                (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 12 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 12 })
            },
            label = "advanced_section_transition"
        ) { selectedTab ->
            when (selectedTab) {
                DashboardTab.OVERVIEW -> {
                    OverviewTabContent(
                        uiState = uiState,
                        hasLocationPermission = hasLocationPermission,
                        isLocationServicesEnabled = isLocationServicesEnabled,
                        onRequestPermissions = onRequestLocationPermission,
                        onOpenLocationSettings = onOpenLocationSettings,
                        onRunProbe = { onRunProbe(uiState.selectedMode) },
                        onNavigateToDiagnostics = { onSelectTab(DashboardTab.DIAGNOSTICS) },
                        onNavigateToTroubleshoot = { onSelectTab(DashboardTab.HEALER) },
                        onToggleSentinel = onToggleSentinel,
                        onRunBenchmark = onRunBenchmark,
                        onConsultAi = onConsultAi,
                        onClearAiConsultation = onClearAiConsultation,
                        onSelectMode = onSelectMode,
                        context = context
                    )
                }
                DashboardTab.DIAGNOSTICS -> {
                    DiagnosticsTabContent(
                        uiState = uiState,
                        onSelectMode = onSelectMode,
                        onRunProbe = onRunProbe,
                        onRunPingMatrix = onRunPingMatrix,
                        onRunDualStackCheck = onRunDualStackCheck,
                        onRunHopTrace = onRunHopTrace,
                        onSelectCategoryFilter = onSelectCategoryFilter,
                        onHealConnection = {
                            uiState.policyDecision.recommendedAction?.let { onExecuteHealerAction(it) }
                        }
                    )
                }
                DashboardTab.RADAR -> {
                    RadarTabContent(
                        uiState = uiState,
                        onRequestLocationPermission = onRequestLocationPermission,
                        onOpenLocationSettings = onOpenLocationSettings
                    )
                }
                DashboardTab.SPEED_TEST -> {
                    SpeedTestTabContent(
                        speedState = uiState.speedTestState,
                        isTesting = uiState.isSpeedTesting,
                        onStartTest = onStartSpeedTest,
                        onCancelTest = onCancelSpeedTest
                    )
                }
                DashboardTab.HEALER -> {
                    HealerTabContent(
                        uiState = uiState,
                        onExecuteAction = onExecuteHealerAction,
                        onSelectScenario = onSelectSimulation,
                        onRunProbe = { onRunProbe(uiState.selectedMode) },
                        context = context
                    )
                }
                DashboardTab.ANALYTICS -> {
                    AnalyticsLogsTabContent(
                        uiState = uiState,
                        onExportCsv = onExportCsv,
                        onExportMlDataset = onExportMlDataset,
                        onClearHistory = onClearHistory,
                        onDeleteLog = onDeleteLog,
                        onGenerateReport = onGenerateIncidentReport,
                        onShareReport = onShareIncidentReport
                    )
                }
                DashboardTab.COPILOT -> {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        AiDashboardCopilotCard(
                            consultation = uiState.geminiConsultation,
                            isConsulting = uiState.isConsultingAi,
                            onConsultAi = onConsultAi,
                            onClearConsultation = onClearAiConsultation
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedSectionHeader(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NetPulseAccentContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NetPulseOnAccentContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NetPulseTextPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = NetPulseTextSecondary
            )
        }
    }
}
