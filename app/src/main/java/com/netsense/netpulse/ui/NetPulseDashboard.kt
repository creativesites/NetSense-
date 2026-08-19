package com.netsense.netpulse.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.netsense.netpulse.BuildConfig
import com.netsense.netpulse.ai.mind.PulseMindExplanation
import com.netsense.netpulse.ai.predictor.PredictorBenchmarkResult
import com.netsense.netpulse.ai.predictor.PredictorState
import com.netsense.netpulse.ai.predictor.PulsePrediction
import com.netsense.netpulse.analytics.UsabilityAnalyticsSummary
import com.netsense.netpulse.data.AppSettings
import com.netsense.netpulse.data.DiagnosticLogEntity
import com.netsense.netpulse.engine.IssueSeverity
import com.netsense.netpulse.engine.SimulatedFaultScenario
import com.netsense.netpulse.engine.SpeedTestState
import com.netsense.netpulse.engine.TroubleshootFinding
import com.netsense.netpulse.model.ConnectionPresentationMapper
import com.netsense.netpulse.model.ConnectionVisual
import com.netsense.netpulse.model.DiagnosticMode
import com.netsense.netpulse.model.DiagnosticProbeResult
import com.netsense.netpulse.model.EndpointProbeDetail
import com.netsense.netpulse.model.HealerActionItem
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.model.TargetCategory
import com.netsense.netpulse.model.UsabilityRating
import com.netsense.netpulse.model.UsabilityScoreResult
import com.netsense.netpulse.ui.theme.NetPulseAccent
import com.netsense.netpulse.ui.theme.NetPulseAccentContainer
import com.netsense.netpulse.ui.theme.NetPulseBg
import com.netsense.netpulse.ui.theme.NetPulseBorder
import com.netsense.netpulse.ui.theme.NetPulseBorderSubtle
import com.netsense.netpulse.ui.theme.NetPulseOnAccentContainer
import com.netsense.netpulse.ui.theme.NetPulseSurface
import com.netsense.netpulse.ui.theme.NetPulseSurfaceVariant
import com.netsense.netpulse.ui.theme.NetPulseTextPrimary
import com.netsense.netpulse.ui.theme.NetPulseTextSecondary
import com.netsense.netpulse.ui.theme.NetPulseTextTertiary
import com.netsense.netpulse.ui.theme.StatusDegraded
import com.netsense.netpulse.ui.theme.StatusDegradedBg
import com.netsense.netpulse.ui.theme.StatusGood
import com.netsense.netpulse.ui.theme.StatusGoodBg
import com.netsense.netpulse.ui.theme.StatusOptimal
import com.netsense.netpulse.ui.theme.StatusOptimalBg
import com.netsense.netpulse.ui.theme.StatusPoor
import com.netsense.netpulse.ui.theme.StatusPoorBg
import com.netsense.netpulse.ui.theme.StatusUnusable
import com.netsense.netpulse.ui.theme.StatusUnusableBg
import com.netsense.netpulse.ui.theme.StatusZombie
import com.netsense.netpulse.ui.theme.StatusZombieBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetPulseDashboard(
    uiState: DashboardUiState,
    onRunProbe: (DiagnosticMode) -> Unit,
    onSelectTab: (DashboardTab) -> Unit,
    onSelectPrimaryTab: (PrimaryTab) -> Unit = {},
    onFixIt: () -> Unit = {},
    onRetryFix: () -> Unit = {},
    onDismissHealingOutcome: () -> Unit = {},
    onSelectMode: (DiagnosticMode) -> Unit,
    onToggleSentinel: () -> Unit = {},
    onStartSpeedTest: () -> Unit = {},
    onCancelSpeedTest: () -> Unit = {},
    onSelectSimulation: (SimulatedFaultScenario) -> Unit = {},
    onUpdateZombieAlerts: (Boolean) -> Unit = {},
    onUpdateAlertThreshold: (Int) -> Unit = {},
    onUpdateSentinelInterval: (Long) -> Unit = {},
    onUpdateDnsProvider: (String) -> Unit = {},
    onUpdateDataSaver: (Boolean) -> Unit = {},
    onExportCsv: () -> Unit = {},
    onExportMlDataset: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    onDeleteLog: (Long) -> Unit = {},
    onRunPingMatrix: () -> Unit = {},
    onRunDualStackCheck: () -> Unit = {},
    onRunHopTrace: (String) -> Unit = {},
    onSelectCategoryFilter: (TargetCategory?) -> Unit = {},
    onExecuteHealerAction: (HealerActionItem) -> Unit = {},
    onGenerateIncidentReport: () -> Unit = {},
    onShareIncidentReport: () -> Unit = {},
    onRunBenchmark: () -> Unit = {},
    onConsultAi: (String) -> Unit = {},
    onClearAiConsultation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun checkLocationPermission() = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    fun checkLocationServicesEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return lm?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false
    }

    var hasLocationPermission by remember { mutableStateOf(checkLocationPermission()) }
    var isLocationServicesEnabled by remember { mutableStateOf(checkLocationServicesEnabled()) }

    // The header used to show a static tagline ("AI & RF Diagnostics Suite") that never
    // changed - replaced with the same live status every other surface (Home, notification)
    // already agrees on, so it's actually useful at a glance from any tab.
    val headerPresentation = ConnectionPresentationMapper.map(
        status = uiState.policyDecision.state,
        snapshot = uiState.snapshot,
        scoreResult = uiState.scoreResult
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    // Both permission grant AND the system Location toggle can change while this screen is
    // backgrounded (user grants from system Settings, or flips Location off/on) - re-check
    // on every resume instead of only reacting to the in-app permission launcher callback.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = checkLocationPermission()
                isLocationServicesEnabled = checkLocationServicesEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestLocationPermission: () -> Unit = {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    val openLocationSettings: () -> Unit = {
        try {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    Scaffold(
        containerColor = NetPulseBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NetPulseAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = "NetPulse Logo",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "NetPulse",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NetPulseTextPrimary
                            )
                            if (uiState.activeSimulationScenario != SimulatedFaultScenario.NONE) {
                                Text(
                                    text = "Simulation: ${uiState.activeSimulationScenario.simulatedRating}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusZombie
                                )
                            } else {
                                val statusColor = when (headerPresentation.visual) {
                                    ConnectionVisual.CHECK, ConnectionVisual.RECOVERED -> StatusOptimal
                                    ConnectionVisual.PULSE_WARNING -> StatusDegraded
                                    ConnectionVisual.ALERT, ConnectionVisual.OFFLINE -> StatusUnusable
                                    ConnectionVisual.RECOVERING, ConnectionVisual.SEARCHING -> StatusGood
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(statusColor)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "${uiState.scoreResult.score}/100 · ${headerPresentation.statusLine}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NetPulseTextTertiary,
                                        modifier = Modifier.testTag("header_status_line")
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    // The manual refresh/probe action is engineering-facing (Section 6: the
                    // primary Home action is Fix, not Run Diagnostics) - only show it once the
                    // user has actually navigated into Advanced, where a manual probe is useful.
                    if (uiState.selectedPrimaryTab == PrimaryTab.ADVANCED) {
                        IconButton(
                            onClick = { onRunProbe(uiState.selectedMode) },
                            enabled = !uiState.isProbing,
                            modifier = Modifier.testTag("refresh_probe_button")
                        ) {
                            if (uiState.isProbing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = NetPulseAccent
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Run Probe",
                                    tint = NetPulseTextPrimary
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NetPulseSurface
                )
            )
        },
        bottomBar = {
            NetPulseBottomNavigation(
                selected = uiState.selectedPrimaryTab,
                onSelect = onSelectPrimaryTab
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState.selectedPrimaryTab) {
                PrimaryTab.HOME -> {
                    HomeScreen(
                        uiState = uiState,
                        hasLocationPermission = hasLocationPermission,
                        isLocationServicesEnabled = isLocationServicesEnabled,
                        onRequestPermissions = requestLocationPermission,
                        onOpenLocationSettings = openLocationSettings,
                        onFixIt = onFixIt,
                        onRetryFix = onRetryFix,
                        onDismissHealingOutcome = onDismissHealingOutcome,
                        onNavigateToAdvanced = { onSelectPrimaryTab(PrimaryTab.ADVANCED) },
                        onNavigateToHistory = { onSelectPrimaryTab(PrimaryTab.HISTORY) }
                    )
                }
                PrimaryTab.HISTORY -> {
                    HistoryScreen(
                        uiState = uiState,
                        onNavigateToAdvanced = { onSelectPrimaryTab(PrimaryTab.ADVANCED) }
                    )
                }
                PrimaryTab.ADVANCED -> {
                    AdvancedScreen(
                        uiState = uiState,
                        onSelectTab = onSelectTab,
                        onRunProbe = onRunProbe,
                        onSelectMode = onSelectMode,
                        onStartSpeedTest = onStartSpeedTest,
                        onCancelSpeedTest = onCancelSpeedTest,
                        onSelectSimulation = onSelectSimulation,
                        onExportCsv = onExportCsv,
                        onExportMlDataset = onExportMlDataset,
                        onClearHistory = onClearHistory,
                        onDeleteLog = onDeleteLog,
                        onRunPingMatrix = onRunPingMatrix,
                        onRunDualStackCheck = onRunDualStackCheck,
                        onRunHopTrace = onRunHopTrace,
                        onSelectCategoryFilter = onSelectCategoryFilter,
                        onExecuteHealerAction = onExecuteHealerAction,
                        onGenerateIncidentReport = onGenerateIncidentReport,
                        onShareIncidentReport = onShareIncidentReport,
                        onRunBenchmark = onRunBenchmark,
                        onConsultAi = onConsultAi,
                        onClearAiConsultation = onClearAiConsultation,
                        onToggleSentinel = onToggleSentinel,
                        hasLocationPermission = hasLocationPermission,
                        isLocationServicesEnabled = isLocationServicesEnabled,
                        onRequestLocationPermission = requestLocationPermission,
                        onOpenLocationSettings = openLocationSettings,
                        context = context
                    )
                }
                PrimaryTab.SETTINGS -> {
                    SettingsTabContent(
                        appSettings = uiState.appSettings,
                        isSentinelRunning = uiState.isSentinelRunning,
                        onToggleSentinel = onToggleSentinel,
                        onUpdateZombieAlerts = onUpdateZombieAlerts,
                        onUpdateAlertThreshold = onUpdateAlertThreshold,
                        onUpdateSentinelInterval = onUpdateSentinelInterval,
                        onUpdateDnsProvider = onUpdateDnsProvider,
                        onUpdateDataSaver = onUpdateDataSaver
                    )
                }
            }
        }
    }
}

@Composable
fun NetPulseBottomNavigation(
    selected: PrimaryTab,
    onSelect: (PrimaryTab) -> Unit
) {
    val icons = mapOf(
        PrimaryTab.HOME to Icons.Default.NetworkCheck,
        PrimaryTab.HISTORY to Icons.Default.ClearAll,
        PrimaryTab.ADVANCED to Icons.Default.Science,
        PrimaryTab.SETTINGS to Icons.Default.Settings
    )
    NavigationBar(
        containerColor = NetPulseSurface,
        modifier = Modifier.testTag("primary_bottom_nav")
    ) {
        PrimaryTab.values().forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = icons.getValue(tab),
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NetPulseAccent,
                    selectedTextColor = NetPulseAccent,
                    unselectedIconColor = NetPulseTextTertiary,
                    unselectedTextColor = NetPulseTextTertiary,
                    indicatorColor = NetPulseAccentContainer
                ),
                modifier = Modifier.testTag("primary_tab_${tab.name.lowercase()}")
            )
        }
    }
}

@Composable
fun MinimalistScrollableTabNavigation(
    selectedTab: DashboardTab,
    onSelectTab: (DashboardTab) -> Unit
) {
    val scrollState = rememberScrollState()
    Surface(
        color = NetPulseSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DashboardTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val bgColor = if (isSelected) NetPulseAccent else NetPulseSurfaceVariant
                val textColor = if (isSelected) Color.White else NetPulseTextSecondary

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = bgColor,
                    modifier = Modifier
                        .clickable { onSelectTab(tab) }
                        .testTag("tab_${tab.name.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: OVERVIEW CONTENT
// -------------------------------------------------------------
@Composable
fun OverviewTabContent(
    uiState: DashboardUiState,
    hasLocationPermission: Boolean,
    isLocationServicesEnabled: Boolean = true,
    onRequestPermissions: () -> Unit,
    onOpenLocationSettings: () -> Unit = {},
    onRunProbe: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToTroubleshoot: () -> Unit,
    onToggleSentinel: () -> Unit,
    onRunBenchmark: () -> Unit,
    onConsultAi: (String) -> Unit = {},
    onClearAiConsultation: () -> Unit = {},
    onSelectMode: (DiagnosticMode) -> Unit = {},
    context: Context
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }

        if (!hasLocationPermission) {
            item {
                MinimalPermissionBanner(onRequest = onRequestPermissions)
            }
        } else if (!isLocationServicesEnabled) {
            item {
                MinimalPermissionBanner(
                    onRequest = onOpenLocationSettings,
                    title = "Location Services Off",
                    body = "Detailed cellular RF telemetry needs system Location turned on, even though the app permission is already granted.",
                    buttonLabel = "Turn On"
                )
            }
        }

        // 1. Current Connection Status Indicator (Live beacon, validated transit, IP/DNS/Gateway)
        item {
            CurrentConnectionStatusCard(
                snapshot = uiState.snapshot,
                wifiRadar = uiState.wifiRadar,
                scoreResult = uiState.scoreResult,
                probeResult = uiState.probeResult
            )
        }

        // 2. Signal Quality Gauge (Custom Arc Meter, dBm readout, tier badge, link speed)
        item {
            SignalQualityGaugeCard(
                snapshot = uiState.snapshot,
                wifiRadar = uiState.wifiRadar,
                cellularRf = uiState.cellularRf
            )
        }

        // 3. 'Run Diagnostics' Button & Execution Suite
        item {
            RunDiagnosticsActionCard(
                isProbing = uiState.isProbing,
                onRunProbe = { onRunProbe() },
                lastChecked = uiState.lastCheckedTimestamp,
                selectedMode = uiState.selectedMode,
                onSelectMode = onSelectMode
            )
        }

        // 4. Gemini AI Network Copilot & Diagnostic Brain (Interactive prompt chips + query field)
        item {
            AiDashboardCopilotCard(
                consultation = uiState.geminiConsultation,
                isConsulting = uiState.isConsultingAi,
                onConsultAi = onConsultAi,
                onClearConsultation = onClearAiConsultation
            )
        }

        // 5. Hero Usability Score Card
        item {
            MinimalistHeroScoreCard(
                scoreResult = uiState.scoreResult,
                snapshot = uiState.snapshot,
                probeResult = uiState.probeResult,
                isProbing = uiState.isProbing,
                lastChecked = uiState.lastCheckedTimestamp,
                onRunProbe = onRunProbe
            )
        }

        // 6. Sentinel Service Control Card
        item {
            SentinelControlCard(
                isRunning = uiState.isSentinelRunning,
                onToggle = onToggleSentinel
            )
        }

        // 7. Pulse Intelligence & Anomaly Predictor Card (Phase 9)
        item {
            PulseIntelligenceCard(
                explanation = uiState.pulseMindExplanation,
                prediction = uiState.prediction,
                mlTelemetryCount = uiState.mlTelemetryCount,
                benchmarkResult = uiState.predictorBenchmark,
                onRunBenchmark = onRunBenchmark
            )
        }

        // 8. Link Stability Index Card
        item {
            LinkStabilityCard(
                stabilityScore = uiState.networkComparison.stabilityScore,
                verdict = uiState.networkComparison.stabilityVerdict
            )
        }

        // 9. Diagnosis & Root Cause Card
        item {
            MinimalistDiagnosisCard(
                scoreResult = uiState.scoreResult,
                onNavigateToTroubleshoot = onNavigateToTroubleshoot
            )
        }

        // 10. Score Breakdown Component Card
        item {
            MinimalistScoreBreakdownCard(scoreResult = uiState.scoreResult)
        }

        // 11. Quick Active Telemetry Summary Card
        item {
            MinimalistQuickProbeSummary(
                probeResult = uiState.probeResult,
                onNavigateToDiagnostics = onNavigateToDiagnostics
            )
        }

        // 12. Safe Recovery Actions Card
        item {
            MinimalistRecoveryCard(context = context)
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun SentinelControlCard(
    isRunning: Boolean,
    onToggle: () -> Unit
) {
    val statusBg = if (isRunning) StatusOptimalBg else NetPulseSurface
    val statusBorder = if (isRunning) StatusOptimal.copy(alpha = 0.3f) else NetPulseBorder

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sentinel_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = statusBg),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(statusBorder)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) StatusOptimal else NetPulseSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Security else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = if (isRunning) Color.White else NetPulseTextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isRunning) "Background Sentinel Active" else "Background Sentinel Inactive",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                    Text(
                        text = if (isRunning) "Periodic micro-probes & zombie alerts enabled" else "Enable for continuous connectivity monitoring",
                        style = MaterialTheme.typography.bodySmall,
                        color = NetPulseTextSecondary
                    )
                }
            }

            Switch(
                checked = isRunning,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = StatusOptimal
                ),
                modifier = Modifier.testTag("sentinel_toggle_switch")
            )
        }
    }
}

// -------------------------------------------------------------
// PULSE INTELLIGENCE & ON-DEVICE ANOMALY PREDICTOR (PHASE 9)
// -------------------------------------------------------------
@Composable
fun PulseIntelligenceCard(
    explanation: PulseMindExplanation?,
    prediction: PulsePrediction,
    mlTelemetryCount: Int,
    benchmarkResult: PredictorBenchmarkResult?,
    onRunBenchmark: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pulse_intelligence_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NetPulseAccentContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = "Pulse Intelligence",
                            tint = NetPulseOnAccentContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Pulse Intelligence",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NetPulseTextPrimary
                        )
                        Text(
                            text = "Predictor ML + Mind Reasoning Layer",
                            style = MaterialTheme.typography.labelSmall,
                            color = NetPulseTextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (prediction.state) {
                        PredictorState.VALID_PREDICTION -> StatusOptimalBg
                        PredictorState.INSUFFICIENT_TELEMETRY -> StatusDegradedBg
                        else -> NetPulseSurfaceVariant
                    }
                ) {
                    Text(
                        text = when (prediction.state) {
                            PredictorState.VALID_PREDICTION -> "1D-CNN Active"
                            PredictorState.INSUFFICIENT_TELEMETRY -> "Buffering"
                            else -> "Dataset Logger"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (prediction.state) {
                            PredictorState.VALID_PREDICTION -> StatusOptimal
                            PredictorState.INSUFFICIENT_TELEMETRY -> StatusDegraded
                            else -> NetPulseTextSecondary
                        }
                    )
                }
            }

            HorizontalDivider(color = NetPulseBorderSubtle, thickness = 1.dp)

            // Section 1: PulseMind Narrative Reasoning
            if (explanation != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = explanation.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseAccent
                    )
                    Text(
                        text = explanation.narrative,
                        style = MaterialTheme.typography.bodySmall,
                        color = NetPulseTextPrimary,
                        lineHeight = 18.sp
                    )

                    // Key telemetry pills
                    if (explanation.keySignals.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            explanation.keySignals.take(3).forEach { signal ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = NetPulseSurfaceVariant
                                ) {
                                    Text(
                                        text = signal,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NetPulseTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Recommendation callout
                    if (explanation.recoveryRecommendation.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NetPulseSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = NetPulseAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = explanation.recoveryRecommendation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NetPulseTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: PulsePredictor Temporal ML
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = NetPulseSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "On-Device Temporal Model",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NetPulseTextPrimary
                        )
                        Text(
                            text = "Tensor: [1, 15, 12]",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = NetPulseTextTertiary
                        )
                    }

                    when (prediction.state) {
                        PredictorState.VALID_PREDICTION -> {
                            // Valid active prediction details
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Dropout Risk (30s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NetPulseTextSecondary
                                    )
                                    Text(
                                        text = "${(prediction.dropoutProbability30s * 100).toInt()}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (prediction.dropoutProbability30s > 0.5f) StatusUnusable else NetPulseTextPrimary
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Degradation Risk (15s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NetPulseTextSecondary
                                    )
                                    Text(
                                        text = "${(prediction.degradationProbability15s * 100).toInt()}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (prediction.degradationProbability15s > 0.5f) StatusDegraded else NetPulseTextPrimary
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Likely Root Cause",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NetPulseTextSecondary
                                    )
                                    Text(
                                        text = prediction.likelyCause.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = NetPulseAccent
                                    )
                                }
                            }
                        }
                        PredictorState.INSUFFICIENT_TELEMETRY -> {
                            Text(
                                text = prediction.stateReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = NetPulseTextSecondary
                            )
                        }
                        else -> {
                            // Awaiting model weights / training dataset collection mode
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Dataset Collection Active",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NetPulseTextPrimary
                                )
                                Text(
                                    text = "Recorded $mlTelemetryCount telemetry steps to local Room database. Awaiting trained pulse_predictor_v1.tflite weights (zero synthetic predictions).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NetPulseTextSecondary
                                )
                            }
                        }
                    }

                    // Benchmark result display
                    if (benchmarkResult != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = NetPulseBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Preprocess: ${benchmarkResult.preprocessingDurationNs / 1_000}µs",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = NetPulseTextSecondary
                                )
                                Text(
                                    text = "Inference: ${"%.2f".format(benchmarkResult.inferenceDurationMs)}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = NetPulseAccent
                                )
                            }
                        }
                    }

                    // Benchmark trigger button
                    OutlinedButton(
                        onClick = onRunBenchmark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_model_benchmark_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Benchmark Model Latency & Memory",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MinimalistHeroScoreCard(
    scoreResult: UsabilityScoreResult,
    snapshot: NetworkSnapshot,
    probeResult: DiagnosticProbeResult?,
    isProbing: Boolean,
    lastChecked: Long,
    onRunProbe: () -> Unit
) {
    val score = scoreResult.score
    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(durationMillis = 600),
        label = "score_anim"
    )

    val (badgeColor, badgeBg, ratingText) = when {
        scoreResult.isZombieConnection -> Triple(StatusZombie, StatusZombieBg, "ZOMBIE CONNECTION")
        scoreResult.rating == UsabilityRating.OPTIMAL -> Triple(StatusOptimal, StatusOptimalBg, "OPTIMAL")
        scoreResult.rating == UsabilityRating.GOOD -> Triple(StatusGood, StatusGoodBg, "GOOD")
        scoreResult.rating == UsabilityRating.DEGRADED -> Triple(StatusDegraded, StatusDegradedBg, "DEGRADED")
        scoreResult.rating == UsabilityRating.POOR -> Triple(StatusPoor, StatusPoorBg, "POOR")
        else -> Triple(StatusUnusable, StatusUnusableBg, "UNUSABLE")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("usability_hero_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header row: Transport pill + Rating pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val transportIcon = when (snapshot.primaryTransport) {
                        NetworkTransport.WIFI -> Icons.Default.Wifi
                        NetworkTransport.CELLULAR -> Icons.Default.CellTower
                        else -> Icons.Default.SignalCellularAlt
                    }
                    Icon(
                        imageVector = transportIcon,
                        contentDescription = null,
                        tint = NetPulseTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (!snapshot.isConnected) "Offline"
                        else "${snapshot.primaryTransport.name} • ${snapshot.cellularDataNetworkType ?: "Active"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NetPulseTextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = ratingText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Giant Minimalist Numerical Usability Score
            Text(
                text = "${animatedScore.toInt()}",
                fontSize = 58.sp,
                fontWeight = FontWeight.Black,
                color = badgeColor,
                lineHeight = 58.sp,
                modifier = Modifier.testTag("hero_score_text")
            )
            Text(
                text = "USABILITY INDEX / 100",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = NetPulseTextTertiary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Diagnosis summary sentence
            Text(
                text = scoreResult.primaryDiagnosis,
                style = MaterialTheme.typography.bodyMedium,
                color = NetPulseTextPrimary,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Time & Quick action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Checked: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastChecked))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NetPulseTextTertiary
                )

                FilledTonalButton(
                    onClick = onRunProbe,
                    enabled = !isProbing,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = NetPulseAccentContainer,
                        contentColor = NetPulseOnAccentContainer
                    ),
                    modifier = Modifier.testTag("hero_probe_button")
                ) {
                    if (isProbing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = NetPulseOnAccentContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Verify Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MinimalistDiagnosisCard(
    scoreResult: UsabilityScoreResult,
    onNavigateToTroubleshoot: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnosis_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (scoreResult.isZombieConnection) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (scoreResult.isZombieConnection) StatusZombie else NetPulseAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Diagnosis & Root Cause",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                }

                Text(
                    text = "Healer ➔",
                    style = MaterialTheme.typography.labelSmall,
                    color = NetPulseAccent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onNavigateToTroubleshoot() }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = scoreResult.rootCauseSummary,
                style = MaterialTheme.typography.bodySmall,
                color = NetPulseTextSecondary,
                lineHeight = 18.sp
            )

            if (scoreResult.explanatoryReasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NetPulseSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        scoreResult.explanatoryReasons.forEach { reason ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusGood,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = reason,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NetPulseTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MinimalistScoreBreakdownCard(scoreResult: UsabilityScoreResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("score_breakdown_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Score Sub-component Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NetPulseTextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            ScoreBarRow(
                label = "DNS Resolution",
                score = scoreResult.scoreBreakdown["DNS"] ?: 0,
                maxScore = 30,
                weightDesc = "30% weight"
            )
            Spacer(modifier = Modifier.height(8.dp))
            ScoreBarRow(
                label = "TCP Handshake & Jitter",
                score = scoreResult.scoreBreakdown["TCP"] ?: 0,
                maxScore = 35,
                weightDesc = "35% weight"
            )
            Spacer(modifier = Modifier.height(8.dp))
            ScoreBarRow(
                label = "HTTP 204 TTFB",
                score = scoreResult.scoreBreakdown["HTTP"] ?: 0,
                maxScore = 20,
                weightDesc = "20% weight"
            )
            Spacer(modifier = Modifier.height(8.dp))
            ScoreBarRow(
                label = "Physical Signal RF",
                score = scoreResult.scoreBreakdown["Signal"] ?: 0,
                maxScore = 15,
                weightDesc = "15% weight"
            )
        }
    }
}

@Composable
fun ScoreBarRow(
    label: String,
    score: Int,
    maxScore: Int,
    weightDesc: String
) {
    val progress = (score.toFloat() / maxScore.toFloat()).coerceIn(0f, 1f)
    val color = when {
        progress >= 0.8f -> StatusOptimal
        progress >= 0.5f -> StatusGood
        progress >= 0.25f -> StatusDegraded
        else -> StatusUnusable
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = NetPulseTextPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$score/$maxScore",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "($weightDesc)",
                    style = MaterialTheme.typography.labelSmall,
                    color = NetPulseTextTertiary,
                    fontSize = 10.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = NetPulseBorderSubtle
        )
    }
}

@Composable
fun MinimalistQuickProbeSummary(
    probeResult: DiagnosticProbeResult?,
    onNavigateToDiagnostics: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToDiagnostics() }
            .testTag("quick_probe_summary_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Telemetry Snapshot",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NetPulseTextPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Deep View",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetPulseAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = NetPulseAccent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (probeResult != null) {
                // 2-per-row, same fix as the Analytics KPI grid - 4 equal-weight pills crammed
                // into one Row left each one too narrow for its label ("HTTP 204", "TCP RTT"),
                // wrapping into the tall, narrow strip this whole audit pass is fixing.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MinimalistMetricPill(
                        label = "DNS",
                        value = if (probeResult.overallDnsSuccess) "${probeResult.averageDnsMs}ms" else "FAIL",
                        isSuccess = probeResult.overallDnsSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    MinimalistMetricPill(
                        label = "TCP RTT",
                        value = if (probeResult.overallTcpSuccess) "${probeResult.averageTcpMs}ms" else "FAIL",
                        isSuccess = probeResult.overallTcpSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MinimalistMetricPill(
                        label = "HTTP 204",
                        value = if (probeResult.overallHttpSuccess) "${probeResult.averageHttpMs}ms" else "FAIL",
                        isSuccess = probeResult.overallHttpSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    MinimalistMetricPill(
                        label = "Loss",
                        value = "${(probeResult.packetLossPct * 100).toInt()}%",
                        isSuccess = probeResult.packetLossPct < 0.1f,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = "No diagnostic probe recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NetPulseTextTertiary
                )
            }
        }
    }
}

@Composable
fun MinimalistMetricPill(
    label: String,
    value: String,
    isSuccess: Boolean,
    modifier: Modifier = Modifier
) {
    // maxLines=1 + ellipsis is load-bearing here, not cosmetic: without it, cramming several
    // of these into one equal-weight Row (as several screens did) let a long label wrap onto
    // multiple lines inside a narrow column, which read as a tall "narrow vertical bar" rather
    // than a pill - the actual bug several pages were hitting.
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = NetPulseSurfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = NetPulseTextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSuccess) NetPulseTextPrimary else StatusUnusable,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MinimalistRecoveryCard(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Legitimate Recovery Actions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NetPulseTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Safe system shortcuts to cycle network interfaces and recover from zombie states.",
                style = MaterialTheme.typography.bodySmall,
                color = NetPulseTextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                context.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                            } else {
                                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                            }
                        } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = NetPulseSurfaceVariant,
                        contentColor = NetPulseTextPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Internet Panel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                        } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Flight, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Airplane Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: DIAGNOSTICS & PROBING CONTENT (PHASE 2 & 6)
// -------------------------------------------------------------
@Composable
fun DiagnosticsTabContent(
    uiState: DashboardUiState,
    onSelectMode: (DiagnosticMode) -> Unit,
    onRunProbe: (DiagnosticMode) -> Unit,
    onRunPingMatrix: () -> Unit,
    onRunDualStackCheck: () -> Unit,
    onRunHopTrace: (String) -> Unit,
    onSelectCategoryFilter: (TargetCategory?) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }

        // Mode Selector Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Diagnostic Probe Profile",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiagnosticMode.values().forEach { mode ->
                            val isSelected = uiState.selectedMode == mode
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) NetPulseAccent else NetPulseSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectMode(mode) }
                                    .testTag("mode_${mode.name.lowercase()}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Three equal-weight columns leaves each one too narrow for
                                    // "Standard Probe"/"Deep Diagnostic" on one line - without a
                                    // cap they wrap, turning the button into the same tall,
                                    // narrow vertical bar this whole audit pass is fixing.
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else NetPulseTextPrimary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "~${mode.estimatedPayloadKb} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color.White.copy(alpha = 0.8f) else NetPulseTextTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onRunProbe(uiState.selectedMode) },
                        enabled = !uiState.isProbing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("run_diagnostics_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NetPulseAccent,
                            contentColor = Color.White
                        )
                    ) {
                        if (uiState.isProbing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Probing Endpoints...", fontSize = 13.sp)
                        } else {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run ${uiState.selectedMode.title}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Multi-Host Ping Matrix Card (Phase 6)
        item {
            MultiHostPingMatrixCard(
                results = uiState.pingMatrixResults,
                isProbing = uiState.isMatrixProbing,
                selectedCategory = uiState.selectedCategoryFilter,
                onSelectCategory = onSelectCategoryFilter,
                onRunMatrixProbe = onRunPingMatrix
            )
        }

        // Dual-Stack IPv4 / IPv6 Happy Eyeballs Card (Phase 6)
        item {
            DualStackDiagnosticCard(
                dualStack = uiState.dualStackResult,
                isProbing = uiState.isDualStackProbing,
                onRunCheck = onRunDualStackCheck
            )
        }

        // Visual Path Hop Tracer Card (Phase 6)
        item {
            PathHopTracerCard(
                hopTrace = uiState.hopTraceResult,
                isTracing = uiState.isHopTracing,
                onRunTrace = onRunHopTrace
            )
        }

        // Layer 4 & Layer 7 Forensic Endpoints Detail Card
        if (uiState.probeResult != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Layer 4 & Layer 7 Multi-Endpoint Telemetry",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NetPulseTextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        EndpointDetailSection(
                            title = "Primary Upstream (Google Anycast)",
                            detail = uiState.probeResult.primaryEndpoint
                        )

                        uiState.probeResult.secondaryEndpoint?.let { secondary ->
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = NetPulseBorderSubtle)
                            Spacer(modifier = Modifier.height(12.dp))

                            EndpointDetailSection(
                                title = "Secondary Upstream (Cloudflare Edge)",
                                detail = secondary
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// -------------------------------------------------------------
// TAB 3: RADAR & RF TELEMETRY CONTENT (PHASE 7)
// -------------------------------------------------------------
@Composable
fun RadarTabContent(
    uiState: DashboardUiState,
    onRequestLocationPermission: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }

        // Link Stability Index Card
        item {
            LinkStabilityCard(
                stabilityScore = uiState.networkComparison.stabilityScore,
                verdict = uiState.networkComparison.stabilityVerdict
            )
        }

        // Wi-Fi vs Cellular Head-to-Head Comparison Card
        item {
            NetworkComparisonHeadToHeadCard(summary = uiState.networkComparison)
        }

        // Wi-Fi RF Radar Card
        item {
            WifiRadarCard(radar = uiState.wifiRadar)
        }

        // Cellular Baseband & RF Telemetry Card
        item {
            CellularRfCard(
                cellular = uiState.cellularRf,
                onRequestLocationPermission = onRequestLocationPermission,
                onOpenLocationSettings = onOpenLocationSettings
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// -------------------------------------------------------------
// TAB 4: SPEED METER CONTENT (PHASE 5)
// -------------------------------------------------------------
@Composable
fun SpeedTestTabContent(
    speedState: SpeedTestState,
    isTesting: Boolean,
    onStartTest: () -> Unit,
    onCancelTest: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }

        // Hero Speedometer Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("speed_test_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Micro-Bandwidth Usable Speedometer",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                    Text(
                        text = "Safe ~2MB Data Cap • Active Socket Throughput",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetPulseTextTertiary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Speed readout
                    val currentSpeed = speedState.currentMbps
                    val currentPhase = speedState.stage.name

                    Text(
                        text = String.format(Locale.US, "%.1f", currentSpeed),
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        color = NetPulseAccent,
                        lineHeight = 54.sp
                    )
                    Text(
                        text = "Mbps ($currentPhase)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { speedState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NetPulseAccent,
                        trackColor = NetPulseBorderSubtle
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpeedStatCard(
                            title = "Latency",
                            value = if (speedState.pingMs != null) "${speedState.pingMs} ms" else "--",
                            icon = Icons.Default.Speed,
                            modifier = Modifier.weight(1f)
                        )
                        SpeedStatCard(
                            title = "Download",
                            value = if (speedState.downloadMbps != null) "${speedState.downloadMbps} Mbps" else "--",
                            icon = Icons.Default.Download,
                            modifier = Modifier.weight(1f)
                        )
                        SpeedStatCard(
                            title = "Upload",
                            value = if (speedState.uploadMbps != null) "${speedState.uploadMbps} Mbps" else "--",
                            icon = Icons.Default.Upload,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isTesting) {
                        Button(
                            onClick = onCancelTest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusUnusable,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cancel Speed Test", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = onStartTest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("start_speed_test_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NetPulseAccent,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Bandwidth Test", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Data-Friendly Measurement Policy",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Unlike heavy commercial speed tests that burn 50-100 MB per test, NetPulse uses dynamic micro-chunking capped to ~2.0 MB total. This gives accurate usable throughput without exhausting mobile data bundles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NetPulseTextSecondary
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun SpeedStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = NetPulseSurfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = NetPulseAccent, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = NetPulseTextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = NetPulseTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// -------------------------------------------------------------
// TAB 5: HEALER & REMEDIES CONTENT (PHASE 4 & 8)
// -------------------------------------------------------------
@Composable
fun HealerTabContent(
    uiState: DashboardUiState,
    onExecuteAction: (HealerActionItem) -> Unit,
    onSelectScenario: (SimulatedFaultScenario) -> Unit,
    onRunProbe: () -> Unit,
    context: Context
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }

        // Automated Smart Healer Actions (Phase 8)
        item {
            SmartHealerCard(
                actions = uiState.healerActions,
                onExecuteAction = onExecuteAction,
                policyDecision = uiState.policyDecision
            )
        }

        // Fault Sandbox Simulator - fabricates fake network conditions into the live UI state
        // for QA/demo purposes, which is exactly the "never show a fake reading" line this app
        // otherwise holds for real users. It has no purpose for someone just trying to fix
        // their Internet, and risks a real user thinking their connection is actually broken -
        // so it's a debug-build-only developer tool, never shipped to the Play Store build.
        if (BuildConfig.DEBUG) {
            item {
                Text(
                    text = "DEVELOPER TOOLS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = NetPulseTextTertiary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            item {
                FaultSandboxSimulatorCard(
                    currentScenario = uiState.activeSimulationScenario,
                    onSelectScenario = onSelectScenario
                )
            }
        }

        // Root Cause Findings & Interactive Remediation
        item {
            Text(
                text = "Root-Cause Findings & Interactive Remediation",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NetPulseTextPrimary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        items(uiState.troubleshootFindings, key = { it.id }) { finding ->
            TroubleshootFindingCard(
                finding = finding,
                onRunProbe = onRunProbe,
                context = context
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun TroubleshootFindingCard(
    finding: TroubleshootFinding,
    onRunProbe: () -> Unit,
    context: Context
) {
    val (sevColor, sevBg) = when (finding.severity) {
        IssueSeverity.CRITICAL -> Pair(StatusZombie, StatusZombieBg)
        IssueSeverity.HIGH -> Pair(StatusUnusable, StatusUnusableBg)
        IssueSeverity.MODERATE -> Pair(StatusDegraded, StatusDegradedBg)
        IssueSeverity.HEALTHY -> Pair(StatusOptimal, StatusOptimalBg)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(sevColor.copy(alpha = 0.3f))
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = finding.category.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = NetPulseTextTertiary
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = sevBg
                ) {
                    Text(
                        text = finding.severity.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = sevColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = finding.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NetPulseTextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = finding.technicalExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = NetPulseTextSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Impact: ${finding.likelyImpact}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = NetPulseTextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = NetPulseBorderSubtle)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Step-by-Step Remediation:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = NetPulseTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            finding.steps.forEach { step ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NetPulseSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${step.stepNumber}. ${step.title}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = NetPulseTextPrimary
                            )
                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = NetPulseTextSecondary
                            )
                        }

                        if (step.actionType != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    when (step.actionType) {
                                        "SETTINGS_INTERNET" -> {
                                            try {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                    context.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                                                } else {
                                                    context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                                }
                                            } catch (e: Exception) {
                                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                            }
                                        }
                                        "SETTINGS_AIRPLANE" -> {
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                                            } catch (e: Exception) {
                                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                            }
                                        }
                                        "RETRY_PROBE" -> {
                                            onRunProbe()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Open", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 6: ANALYTICS & LOGS (PHASE 3 & 8)
// -------------------------------------------------------------
@Composable
fun AnalyticsLogsTabContent(
    uiState: DashboardUiState,
    onExportCsv: () -> Unit,
    onExportMlDataset: () -> Unit = {},
    onClearHistory: () -> Unit,
    onDeleteLog: (Long) -> Unit,
    onGenerateReport: () -> Unit,
    onShareReport: () -> Unit
) {
    val analytics = uiState.analytics
    val logs = uiState.persistentLogs

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }

        // Technical Incident & Audit Report Generator Card (Phase 8)
        item {
            TechnicalIncidentReportCard(
                report = uiState.incidentReport,
                isGenerating = uiState.isGeneratingReport,
                onGenerateReport = onGenerateReport,
                onShareReport = onShareReport
            )
        }

        // Analytics KPI Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Historical Usability Analytics",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // A 2-per-row grid (not 3-4 equal-weight items crammed into one Row) gives
                    // each pill enough width for its label to stay on one line - cramming more
                    // in per row was causing labels to wrap into a tall, narrow strip.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MinimalistMetricPill(
                            label = "Avg Score",
                            value = "${analytics.averageScore}/100",
                            isSuccess = analytics.averageScore >= 60,
                            modifier = Modifier.weight(1f)
                        )
                        MinimalistMetricPill(
                            label = "Uptime",
                            value = "${analytics.uptimePercentage.toInt()}%",
                            isSuccess = analytics.uptimePercentage >= 80f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MinimalistMetricPill(
                            label = "Zombies",
                            value = "${analytics.zombieCount}",
                            isSuccess = analytics.zombieCount == 0,
                            modifier = Modifier.weight(1f)
                        )
                        MinimalistMetricPill(
                            label = "Probes",
                            value = "${analytics.totalProbes}",
                            isSuccess = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MinimalistMetricPill(
                            label = "Avg DNS",
                            value = "${analytics.averageDnsMs}ms",
                            isSuccess = analytics.averageDnsMs < 100,
                            modifier = Modifier.weight(1f)
                        )
                        MinimalistMetricPill(
                            label = "Avg TCP",
                            value = "${analytics.averageTcpMs}ms",
                            isSuccess = analytics.averageTcpMs < 150,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MinimalistMetricPill(
                            label = "Avg HTTP",
                            value = "${analytics.averageHttpMs}ms",
                            isSuccess = analytics.averageHttpMs < 250,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Export & Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onExportCsv,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetPulseAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_csv_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export CSV", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onClearHistory,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Logs", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ML Dataset Export (Part 13) - production (non-synthetic) telemetry + resolved
        // labels only, for offline PulsePredictor experimentation. Never trains anything
        // on-device.
        item {
            OutlinedButton(
                onClick = onExportMlDataset,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_ml_dataset_button")
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export ML Training Dataset (JSONL)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // History Log List
        item {
            Text(
                text = "Diagnostic Outage History (${logs.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NetPulseTextPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (logs.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NetPulseSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No diagnostic events stored yet. Run probes or enable background sentinel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NetPulseTextTertiary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(logs, key = { it.id }) { log ->
                DiagnosticLogItemCard(log = log, onDelete = { onDeleteLog(log.id) })
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun DiagnosticLogItemCard(log: DiagnosticLogEntity, onDelete: () -> Unit) {
    val ratingColor = when {
        log.isZombieConnection -> StatusZombie
        log.score >= 80 -> StatusOptimal
        log.score >= 60 -> StatusGood
        log.score >= 40 -> StatusDegraded
        else -> StatusUnusable
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = NetPulseSurface,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ratingColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${log.score}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ratingColor
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "${log.transport} • ${log.rating}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                    Text(
                        text = "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))} • DNS: ${log.dnsLatencyMs ?: -1}ms • TCP: ${log.tcpHandshakeMs ?: -1}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = NetPulseTextTertiary,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = NetPulseTextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 7: SETTINGS CONTENT (PHASE 5)
// -------------------------------------------------------------
@Composable
fun SettingsTabContent(
    appSettings: AppSettings,
    isSentinelRunning: Boolean,
    onToggleSentinel: () -> Unit,
    onUpdateZombieAlerts: (Boolean) -> Unit,
    onUpdateAlertThreshold: (Int) -> Unit,
    onUpdateSentinelInterval: (Long) -> Unit,
    onUpdateDnsProvider: (String) -> Unit,
    onUpdateDataSaver: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(6.dp)) }

        // Sentinel Frequency & Alert Settings Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sentinel Monitoring & Notification Rules",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Background Sentinel Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Background Sentinel Service",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = NetPulseTextPrimary
                            )
                            Text(
                                text = "Run non-blocking micro-probes periodically in background",
                                style = MaterialTheme.typography.bodySmall,
                                color = NetPulseTextSecondary
                            )
                        }
                        Switch(
                            checked = isSentinelRunning,
                            onCheckedChange = { onToggleSentinel() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = StatusOptimal
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = NetPulseBorderSubtle)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Zombie Alerts Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Zombie Connection Push Alerts",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = NetPulseTextPrimary
                            )
                            Text(
                                text = "Instantly notify when Wi-Fi/Cell is connected with 0 upstream throughput",
                                style = MaterialTheme.typography.bodySmall,
                                color = NetPulseTextSecondary
                            )
                        }
                        Switch(
                            checked = appSettings.zombieAlertsEnabled,
                            onCheckedChange = { onUpdateZombieAlerts(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NetPulseAccent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = NetPulseBorderSubtle)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Polling Frequency selector
                    Text(
                        text = "Sentinel Polling Interval",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(15L to "15s", 30L to "30s", 60L to "1m", 300L to "5m").forEach { (secs, label) ->
                            val isSelected = appSettings.sentinelIntervalSeconds == secs
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) NetPulseAccent else NetPulseSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onUpdateSentinelInterval(secs) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else NetPulseTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = NetPulseBorderSubtle)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Alert Score Threshold
                    Text(
                        text = "Alert Usability Threshold (Score < ${appSettings.alertThresholdScore})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(20, 40, 50, 60).forEach { threshold ->
                            val isSelected = appSettings.alertThresholdScore == threshold
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) NetPulseAccent else NetPulseSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onUpdateAlertThreshold(threshold) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "< $threshold",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else NetPulseTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Preferred DNS Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Preferred DNS & Probe Target",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Select upstream reference server used for DNS and socket RTT verification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NetPulseTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    listOf(
                        "Google (8.8.8.8)",
                        "Cloudflare (1.1.1.1)",
                        "Quad9 (9.9.9.9)",
                        "OpenDNS (208.67.222.222)"
                    ).forEach { provider ->
                        val isSelected = appSettings.preferredDnsProvider == provider
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) NetPulseAccent.copy(alpha = 0.08f) else NetPulseSurfaceVariant,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) NetPulseAccent else Color.Transparent)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { onUpdateDnsProvider(provider) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = provider,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) NetPulseAccent else NetPulseTextPrimary
                                )
                                if (isSelected) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = NetPulseAccent, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // About Architecture Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "NetPulse Engine Architecture",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Version 1.0 (Phases 1-8 Complete)\n• Multi-Host Ping Reachability Matrix & TLS Handshakes\n• Happy Eyeballs RFC 8305 Dual-Stack IPv4/IPv6 Validation\n• Visual Network Path Hop Traceroute\n• Wi-Fi & Cellular RF Baseband Signal Radar\n• Wi-Fi vs Cellular Path Arbitrator & Stability Index\n• Automated 1-Tap Network Healer & Fault Sandbox\n• Comprehensive Technical Incident & IT Ticket Generator\n• Micro-Bandwidth Speedometer & Room DB Analytics",
                        style = MaterialTheme.typography.bodySmall,
                        color = NetPulseTextSecondary
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// -------------------------------------------------------------
// HELPER COMPONENTS
// -------------------------------------------------------------
@Composable
fun EndpointDetailSection(title: String, detail: EndpointProbeDetail) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = NetPulseTextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        MinimalRow("Host", detail.endpointHost)
        MinimalRow("DNS Resolution", if (detail.dnsSuccess) "${detail.dnsLookupMs} ms" else "Failed", isSuccess = detail.dnsSuccess)
        MinimalRow("TCP Handshake", if (detail.tcpSuccess) "${detail.tcpHandshakeMs} ms" else "Failed", isSuccess = detail.tcpSuccess)
        MinimalRow("HTTP 204 TTFB", if (detail.httpSuccess) "${detail.httpLatencyMs} ms (Code ${detail.httpStatusCode})" else "Failed", isSuccess = detail.httpSuccess)
    }
}

@Composable
fun MinimalRow(label: String, value: String, isSuccess: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = NetPulseTextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = if (isSuccess) NetPulseTextPrimary else StatusUnusable
        )
    }
}

@Composable
fun MinimalPermissionBanner(
    onRequest: () -> Unit,
    title: String = "Telemetry Permissions Recommended",
    body: String = "Grant Location to read raw cellular signal dBm and carrier tower info.",
    buttonLabel: String = "Grant"
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NetPulseSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_banner")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = NetPulseAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = NetPulseTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onRequest,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = NetPulseAccent,
                    contentColor = Color.White
                )
            ) {
                Text(buttonLabel, fontSize = 12.sp)
            }
        }
    }
}
