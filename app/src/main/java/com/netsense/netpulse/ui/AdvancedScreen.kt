package com.netsense.netpulse.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.netsense.netpulse.engine.SimulatedFaultScenario
import com.netsense.netpulse.model.DiagnosticMode
import com.netsense.netpulse.model.HealerActionItem
import com.netsense.netpulse.model.TargetCategory
import com.netsense.netpulse.ui.theme.NetPulseBorder

/**
 * The home for every existing engineering screen (Section 19/29 of the redesign spec):
 * Diagnostics, RF Radar, Speed Test, Healer, Logs & Audit, and AI Copilot. None of these
 * screens changed - this is purely a new host that re-parents the same
 * DiagnosticsTabContent/RadarTabContent/SpeedTestTabContent/HealerTabContent/
 * AnalyticsLogsTabContent composables (and the AI Copilot card, previously buried in the old
 * Overview tab) that used to live at the top level, one navigation layer deeper behind the
 * new consumer Home experience.
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
    onRequestLocationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    context: Context
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MinimalistScrollableTabNavigation(
            selectedTab = uiState.selectedTab,
            onSelectTab = onSelectTab
        )

        HorizontalDivider(color = NetPulseBorder, thickness = 1.dp)

        when (uiState.selectedTab) {
            DashboardTab.DIAGNOSTICS -> {
                DiagnosticsTabContent(
                    uiState = uiState,
                    onSelectMode = onSelectMode,
                    onRunProbe = onRunProbe,
                    onRunPingMatrix = onRunPingMatrix,
                    onRunDualStackCheck = onRunDualStackCheck,
                    onRunHopTrace = onRunHopTrace,
                    onSelectCategoryFilter = onSelectCategoryFilter
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
                Column(modifier = Modifier.padding(16.dp)) {
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
