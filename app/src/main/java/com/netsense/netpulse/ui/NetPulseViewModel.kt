package com.netsense.netpulse.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netsense.netpulse.ai.gemini.GeminiAdvisorEngine
import com.netsense.netpulse.ai.gemini.GeminiAiConsultation
import com.netsense.netpulse.ai.mind.PulseMindEngine
import com.netsense.netpulse.ai.mind.PulseMindExplanation
import com.netsense.netpulse.ai.mind.RuleBasedPulseMind
import com.netsense.netpulse.ai.predictor.PredictorBenchmarkResult
import com.netsense.netpulse.ai.predictor.PulseFeatureExtractor
import com.netsense.netpulse.ai.predictor.PulsePrediction
import com.netsense.netpulse.ai.predictor.PulsePredictorEngine
import com.netsense.netpulse.ai.predictor.RawTelemetryObservation
import com.netsense.netpulse.ai.predictor.TelemetryWindow
import com.netsense.netpulse.analytics.AnalyticsEngine
import com.netsense.netpulse.analytics.DiagnosticExporter
import com.netsense.netpulse.analytics.UsabilityAnalyticsSummary
import com.netsense.netpulse.connectivity.ConnectivityMonitor
import com.netsense.netpulse.data.AppSettings
import com.netsense.netpulse.data.DiagnosticLogEntity
import com.netsense.netpulse.data.DiagnosticRepository
import com.netsense.netpulse.data.NetPulseDatabase
import com.netsense.netpulse.data.NetPulsePreferences
import com.netsense.netpulse.data.TelemetryObservationEntity
import com.netsense.netpulse.dataset.DatasetExportFormat
import com.netsense.netpulse.dataset.DatasetExportService
import com.netsense.netpulse.dataset.LabelResolutionService
import com.netsense.netpulse.dataset.NetworkSessionManager
import com.netsense.netpulse.dataset.ObservationQualityClassifier
import com.netsense.netpulse.dataset.RecoveryOutcomeTracker
import com.netsense.netpulse.engine.DiagnosticEngine
import com.netsense.netpulse.engine.DualStackEngine
import com.netsense.netpulse.engine.FaultSimulator
import com.netsense.netpulse.engine.HopTracerEngine
import com.netsense.netpulse.engine.IncidentReportEngine
import com.netsense.netpulse.engine.NetworkComparisonEngine
import com.netsense.netpulse.engine.NetworkHealerEngine
import com.netsense.netpulse.engine.PingMatrixEngine
import com.netsense.netpulse.engine.RadarEngine
import com.netsense.netpulse.engine.SimulatedFaultScenario
import com.netsense.netpulse.engine.SpeedTestEngine
import com.netsense.netpulse.engine.SpeedTestState
import com.netsense.netpulse.engine.TroubleshootEngine
import com.netsense.netpulse.engine.TroubleshootFinding
import com.netsense.netpulse.engine.UsabilityEngine
import com.netsense.netpulse.model.CellularRfSnapshot
import com.netsense.netpulse.model.DiagnosticMode
import com.netsense.netpulse.model.DiagnosticProbeResult
import com.netsense.netpulse.model.DualStackResult
import com.netsense.netpulse.model.HealerActionItem
import com.netsense.netpulse.model.HealerActionType
import com.netsense.netpulse.model.HopTraceResult
import com.netsense.netpulse.model.IncidentReport
import com.netsense.netpulse.model.NetworkComparisonSummary
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.PingTargetResult
import com.netsense.netpulse.model.ProductStatus
import com.netsense.netpulse.model.TargetCategory
import com.netsense.netpulse.model.UsabilityRating
import com.netsense.netpulse.model.UsabilityScoreResult
import com.netsense.netpulse.model.WifiRadarSnapshot
import com.netsense.netpulse.policy.PolicyDecision
import com.netsense.netpulse.policy.PulsePolicyEngine
import com.netsense.netpulse.service.NetPulseSentinelService
import com.netsense.netpulse.telephony.TelephonyObserver
import com.netsense.netpulse.telephony.TelephonySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class DashboardUiState(
    val snapshot: NetworkSnapshot = NetworkSnapshot(),
    val telephony: TelephonySnapshot = TelephonySnapshot(),
    val probeResult: DiagnosticProbeResult? = null,
    val selectedMode: DiagnosticMode = DiagnosticMode.STANDARD,
    val selectedPrimaryTab: PrimaryTab = PrimaryTab.HOME,
    val selectedTab: DashboardTab = DashboardTab.OVERVIEW,
    val scoreResult: UsabilityScoreResult = UsabilityScoreResult(
        score = 0,
        rating = UsabilityRating.UNUSABLE,
        primaryDiagnosis = "Initializing network monitors...",
        rootCauseSummary = "Connecting to telemetry...",
        explanatoryReasons = emptyList(),
        isZombieConnection = false,
        scoreBreakdown = emptyMap()
    ),
    val isProbing: Boolean = false,
    val isSentinelRunning: Boolean = false,
    val lastCheckedTimestamp: Long = System.currentTimeMillis(),
    val persistentLogs: List<DiagnosticLogEntity> = emptyList(),
    val analytics: UsabilityAnalyticsSummary = UsabilityAnalyticsSummary(),

    // Phase 4: Root Cause Troubleshooting & Fault Simulation
    val troubleshootFindings: List<TroubleshootFinding> = emptyList(),
    val activeSimulationScenario: SimulatedFaultScenario = SimulatedFaultScenario.NONE,

    // Phase 5: Bandwidth Speed Test & Settings
    val speedTestState: SpeedTestState = SpeedTestState(),
    val isSpeedTesting: Boolean = false,
    val appSettings: AppSettings = AppSettings(),

    // Phase 6: Multi-Host Ping Matrix, Dual-Stack & Hop Tracer
    val pingMatrixResults: List<PingTargetResult> = emptyList(),
    val isMatrixProbing: Boolean = false,
    val dualStackResult: DualStackResult? = null,
    val isDualStackProbing: Boolean = false,
    val hopTraceResult: HopTraceResult? = null,
    val isHopTracing: Boolean = false,
    val selectedCategoryFilter: TargetCategory? = null,

    // Phase 7: RF Radar, Cell Telemetry & Wi-Fi vs Cellular Comparison
    val wifiRadar: WifiRadarSnapshot = WifiRadarSnapshot(),
    val cellularRf: CellularRfSnapshot = CellularRfSnapshot(),
    val networkComparison: NetworkComparisonSummary = NetworkComparisonSummary(),

    // Phase 8: Smart Healer Actions & Technical Incident Report
    val healerActions: List<HealerActionItem> = emptyList(),
    val incidentReport: IncidentReport? = null,
    val isGeneratingReport: Boolean = false,

    // Productization: PulsePolicy's deterministic status + recommended-action decision
    val policyDecision: PolicyDecision = PolicyDecision(
        state = ProductStatus.CHECKING,
        recommendedAction = null,
        reason = "Initializing...",
        predictionAvailable = false
    ),

    // Phase 9: Pulse Intelligence & Gemini Copilot
    val prediction: PulsePrediction = PulsePrediction.unavailable(),
    val pulseMindExplanation: PulseMindExplanation? = null,
    val mlTelemetryCount: Int = 0,
    val predictorBenchmark: PredictorBenchmarkResult? = null,
    val geminiConsultation: GeminiAiConsultation? = null,
    val isConsultingAi: Boolean = false,

    // Consumer redesign: real healing-in-progress state and the last resolved outcome, both
    // driven by RecoveryOutcomeTracker's real before/after validation - never a fake timer.
    val isHealing: Boolean = false,
    val healingOutcome: HealingOutcome? = null
)

/** Result of the most recently resolved recovery attempt, for the Home screen's post-healing
 *  card ("You're back online" / "We couldn't restore your connection"). */
data class HealingOutcome(
    val succeeded: Boolean,
    val durationMs: Long,
    val message: String
)

/** Top-level consumer navigation. Advanced hosts every existing engineering screen (see
 *  [DashboardTab]) unchanged - this layer only decides what's visible by default. */
enum class PrimaryTab(val label: String) {
    HOME("Home"),
    HISTORY("History"),
    ADVANCED("Advanced"),
    SETTINGS("Settings")
}

/** Sub-navigation inside the Advanced tab - the same engineering screens that used to be
 *  top-level tabs, now nested one level deeper behind the consumer Home experience. OVERVIEW
 *  is the original detailed dashboard page, kept intact for users who want the full technical
 *  picture in one place instead of the simplified consumer Home screen. */
enum class DashboardTab(val label: String) {
    OVERVIEW("Overview"),
    DIAGNOSTICS("Diagnostics"),
    RADAR("RF & Radar"),
    SPEED_TEST("Speed Meter"),
    HEALER("Healer & Fixes"),
    ANALYTICS("Logs & Audit"),
    COPILOT("AI Copilot")
}

sealed class UiEvent {
    data class ShareCsv(val file: File) : UiEvent()
    data class ShareFile(val file: File, val mimeType: String) : UiEvent()
    data class ShareText(val text: String, val title: String) : UiEvent()
    data class ShowToast(val message: String) : UiEvent()
}

class NetPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val connectivityMonitor = ConnectivityMonitor(application)
    private val telephonyObserver = TelephonyObserver(application)
    private val diagnosticEngine = DiagnosticEngine()
    private val speedTestEngine = SpeedTestEngine()
    private val pingMatrixEngine = PingMatrixEngine()
    private val dualStackEngine = DualStackEngine()
    private val hopTracerEngine = HopTracerEngine()
    private val radarEngine = RadarEngine(application)
    private val comparisonEngine = NetworkComparisonEngine()
    private val healerEngine = NetworkHealerEngine()
    private val reportEngine = IncidentReportEngine()
    private val pulsePredictorEngine = PulsePredictorEngine(application)
    private val telemetryWindow = TelemetryWindow()
    private val pulseMindEngine: PulseMindEngine = RuleBasedPulseMind()
    private val geminiAdvisorEngine = GeminiAdvisorEngine()

    private val database = NetPulseDatabase.getDatabase(application)
    private val repository = DiagnosticRepository(database.diagnosticLogDao(), database.telemetryObservationDao())
    private val preferences = NetPulsePreferences(application)
    private val sessionManager = NetworkSessionManager()
    private val labelResolutionService = LabelResolutionService(database.telemetryObservationDao())
    private val recoveryOutcomeTracker = RecoveryOutcomeTracker(database.recoveryOutcomeDao())

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private var speedTestJob: Job? = null

    init {
        // Observe Settings DataStore
        viewModelScope.launch {
            preferences.settingsFlow.collect { settings ->
                _uiState.update { it.copy(appSettings = settings) }
            }
        }

        // Observe persistent diagnostic logs from Room
        viewModelScope.launch {
            repository.allLogs.collect { logs ->
                val analytics = AnalyticsEngine.generateAnalytics(logs)
                _uiState.update { current ->
                    current.copy(
                        persistentLogs = logs,
                        analytics = analytics
                    )
                }
            }
        }

        // Live telemetry updates
        viewModelScope.launch {
            combine(
                connectivityMonitor.observeNetwork(),
                telephonyObserver.observeTelephony()
            ) { netSnapshot, telSnapshot ->
                val enrichedSnapshot = netSnapshot.copy(
                    carrierName = telSnapshot.carrierName,
                    cellularDataNetworkType = telSnapshot.networkType,
                    signalLevel = telSnapshot.signalLevel,
                    signalDbm = telSnapshot.signalDbm
                )
                Pair(enrichedSnapshot, telSnapshot)
            }.collect { (enrichedSnapshot, telSnapshot) ->
                val scenario = _uiState.value.activeSimulationScenario
                val effectiveSnapshot = if (scenario != SimulatedFaultScenario.NONE) {
                    FaultSimulator.generateSimulatedSnapshot(scenario, enrichedSnapshot)
                } else enrichedSnapshot

                val currentProbe = if (scenario != SimulatedFaultScenario.NONE) {
                    FaultSimulator.generateSimulatedProbe(scenario, _uiState.value.probeResult)
                } else _uiState.value.probeResult

                val score = UsabilityEngine.calculateScore(effectiveSnapshot, currentProbe)
                val finalClassification = UsabilityEngine.classify(effectiveSnapshot, score)
                val findings = TroubleshootEngine.analyze(effectiveSnapshot, currentProbe, score)

                // Refresh RF Radar & Head-to-Head Comparison
                val wifiRadar = radarEngine.getWifiRadarSnapshot(effectiveSnapshot)
                val cellularRf = radarEngine.getCellularRfSnapshot(effectiveSnapshot, telSnapshot)
                val (stabilityScore, stabilityVerdict) = radarEngine.computeStabilityScore(
                    effectiveSnapshot,
                    wifiRadar,
                    cellularRf,
                    currentProbe?.tcpJitterMs,
                    currentProbe?.packetLossPct ?: 0f
                )
                val comparison = comparisonEngine.generateComparison(
                    effectiveSnapshot,
                    wifiRadar,
                    cellularRf,
                    currentProbe,
                    stabilityScore,
                    stabilityVerdict
                )

                // Refresh Smart Healing Plan
                val healingActions = healerEngine.generateHealingPlan(
                    effectiveSnapshot,
                    score,
                    currentProbe,
                    _uiState.value.pingMatrixResults,
                    _uiState.value.dualStackResult
                )

                // Recovery outcomes must be judged against real (never simulated) network
                // state, so this always uses the un-simulated enrichedSnapshot/score.
                val realScoreForRecovery = if (scenario != SimulatedFaultScenario.NONE) {
                    UsabilityEngine.calculateScore(enrichedSnapshot, _uiState.value.probeResult)
                } else score
                val recoveryResolution = recoveryOutcomeTracker.resolvePending(enrichedSnapshot, realScoreForRecovery)

                val policyDecision = PulsePolicyEngine.decide(
                    snapshot = effectiveSnapshot,
                    scoreResult = score,
                    classification = finalClassification,
                    wifiRadar = wifiRadar,
                    cellularRf = cellularRf,
                    healerActions = healingActions,
                    isProbing = _uiState.value.isProbing,
                    isRecoveryPending = recoveryResolution.hasPending,
                    justRecovered = recoveryResolution.justRecovered
                )

                val (prediction, explanation, telemetryCount) = processTelemetryAndInference(
                    snapshot = effectiveSnapshot,
                    telSnapshot = telSnapshot,
                    score = score,
                    probe = currentProbe,
                    wifiRadar = wifiRadar,
                    cellularRf = cellularRf,
                    isSynthetic = scenario != SimulatedFaultScenario.NONE
                )

                _uiState.update { current ->
                    val resolved = recoveryResolution.justResolved
                    current.copy(
                        snapshot = effectiveSnapshot.copy(classification = finalClassification),
                        telephony = telSnapshot,
                        scoreResult = score,
                        troubleshootFindings = findings,
                        wifiRadar = wifiRadar,
                        cellularRf = cellularRf,
                        networkComparison = comparison,
                        healerActions = healingActions,
                        policyDecision = policyDecision,
                        prediction = prediction,
                        pulseMindExplanation = explanation,
                        mlTelemetryCount = telemetryCount,
                        lastCheckedTimestamp = System.currentTimeMillis(),
                        // isHealing tracks ANY pending recovery attempt, whichever screen
                        // triggered it (Home's Fix It button or Advanced's manual healer),
                        // so the Home hero always reflects real PulsePolicy/RecoveryOutcome
                        // state rather than a per-screen flag that could drift out of sync.
                        // Cleared the instant an attempt resolves (success or failure).
                        isHealing = resolved == null && (current.isHealing || recoveryResolution.hasPending),
                        healingOutcome = if (resolved != null) {
                            HealingOutcome(
                                succeeded = resolved.succeeded,
                                durationMs = resolved.timeToRecoveryMs,
                                message = resolved.postDiagnosis
                            )
                        } else current.healingOutcome
                    )
                }
            }
        }

        // Periodic future-outcome label resolution (Part 9/16). Deliberately infrequent and
        // cheap - only sessions with an UNRESOLVED label are rescanned (Part 20: high-value
        // data over maximum churn, no need to do this on every telemetry tick).
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                labelResolutionService.resolvePendingLabels()
            }
        }

        // Initial diagnostic routines
        runActiveProbe(DiagnosticMode.STANDARD)
        runPingMatrix()
        runDualStackCheck()
    }

    fun setTab(tab: DashboardTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setPrimaryTab(tab: PrimaryTab) {
        _uiState.update { it.copy(selectedPrimaryTab = tab) }
    }

    /** The Home screen's single primary action. Dispatches PulsePolicy's currently recommended
     *  action through the existing executeHealerAction path - the same real recovery mechanics
     *  (and RecoveryOutcomeTracker attempt-recording) already used by the Advanced Healer tab.
     *  No-op if PulsePolicy currently has nothing to recommend. */
    fun fixIt(context: Context) {
        val action = _uiState.value.policyDecision.recommendedAction ?: return
        _uiState.update { it.copy(isHealing = true, healingOutcome = null) }
        executeHealerAction(action, context)
    }

    fun dismissHealingOutcome() {
        _uiState.update { it.copy(healingOutcome = null) }
    }

    fun setProbeMode(mode: DiagnosticMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun setCategoryFilter(category: TargetCategory?) {
        _uiState.update { it.copy(selectedCategoryFilter = category) }
    }

    fun setSimulationScenario(scenario: SimulatedFaultScenario) {
        _uiState.update { it.copy(activeSimulationScenario = scenario) }
        recalculateStateWithSimulation(scenario)
    }

    private fun recalculateStateWithSimulation(scenario: SimulatedFaultScenario) {
        val baseSnapshot = _uiState.value.snapshot
        val effectiveSnapshot = FaultSimulator.generateSimulatedSnapshot(scenario, baseSnapshot)
        val effectiveProbe = FaultSimulator.generateSimulatedProbe(scenario, _uiState.value.probeResult)
        val score = UsabilityEngine.calculateScore(effectiveSnapshot, effectiveProbe)
        val classification = UsabilityEngine.classify(effectiveSnapshot, score)
        val findings = TroubleshootEngine.analyze(effectiveSnapshot, effectiveProbe, score)

        val wifiRadar = radarEngine.getWifiRadarSnapshot(effectiveSnapshot)
        val cellularRf = radarEngine.getCellularRfSnapshot(effectiveSnapshot, _uiState.value.telephony)
        val (stabilityScore, stabilityVerdict) = radarEngine.computeStabilityScore(
            effectiveSnapshot,
            wifiRadar,
            cellularRf,
            effectiveProbe?.tcpJitterMs,
            effectiveProbe?.packetLossPct ?: 0f
        )
        val comparison = comparisonEngine.generateComparison(
            effectiveSnapshot,
            wifiRadar,
            cellularRf,
            effectiveProbe,
            stabilityScore,
            stabilityVerdict
        )
        val healingActions = healerEngine.generateHealingPlan(
            effectiveSnapshot,
            score,
            effectiveProbe,
            _uiState.value.pingMatrixResults,
            _uiState.value.dualStackResult
        )
        val policyDecision = PulsePolicyEngine.decide(
            snapshot = effectiveSnapshot,
            scoreResult = score,
            classification = classification,
            wifiRadar = wifiRadar,
            cellularRf = cellularRf,
            healerActions = healingActions,
            isProbing = _uiState.value.isProbing
        )

        _uiState.update { current ->
            current.copy(
                snapshot = effectiveSnapshot.copy(classification = classification),
                probeResult = effectiveProbe,
                scoreResult = score,
                troubleshootFindings = findings,
                wifiRadar = wifiRadar,
                cellularRf = cellularRf,
                networkComparison = comparison,
                healerActions = healingActions,
                policyDecision = policyDecision
            )
        }
    }

    fun toggleSentinelService(context: Context) {
        val nextState = !_uiState.value.isSentinelRunning
        if (nextState) {
            NetPulseSentinelService.startService(context)
        } else {
            NetPulseSentinelService.stopService(context)
        }
        _uiState.update { it.copy(isSentinelRunning = nextState) }
    }

    fun runActiveProbe(mode: DiagnosticMode = _uiState.value.selectedMode) {
        if (_uiState.value.isProbing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProbing = true, selectedMode = mode) }

            val probe = diagnosticEngine.runProbe(mode)
            val currentSnapshot = _uiState.value.snapshot
            val score = UsabilityEngine.calculateScore(currentSnapshot, probe)
            val classification = UsabilityEngine.classify(currentSnapshot, score)
            val findings = TroubleshootEngine.analyze(currentSnapshot, probe, score)

            // Save to Room database
            val entity = DiagnosticLogEntity(
                timestamp = System.currentTimeMillis(),
                score = score.score,
                rating = score.rating.name,
                classification = classification.name,
                isZombieConnection = score.isZombieConnection,
                primaryDiagnosis = score.primaryDiagnosis,
                rootCauseSummary = score.rootCauseSummary,
                transport = currentSnapshot.primaryTransport.name,
                carrierName = currentSnapshot.carrierName,
                cellularNetworkType = currentSnapshot.cellularDataNetworkType,
                signalLevel = currentSnapshot.signalLevel,
                signalDbm = currentSnapshot.signalDbm,
                dnsLatencyMs = probe.averageDnsMs,
                dnsSuccess = probe.overallDnsSuccess,
                tcpHandshakeMs = probe.averageTcpMs,
                tcpSuccess = probe.overallTcpSuccess,
                tcpJitterMs = probe.tcpJitterMs,
                httpLatencyMs = probe.averageHttpMs,
                httpSuccess = probe.overallHttpSuccess,
                httpStatusCode = probe.primaryEndpoint.httpStatusCode,
                packetLossPct = probe.packetLossPct,
                durationMs = probe.durationMs,
                probeMode = mode.name,
                notes = "Manual ${mode.title}"
            )
            repository.saveLog(entity)

            val healingActions = healerEngine.generateHealingPlan(
                currentSnapshot,
                score,
                probe,
                _uiState.value.pingMatrixResults,
                _uiState.value.dualStackResult
            )

            val (prediction, explanation, telemetryCount) = processTelemetryAndInference(
                snapshot = currentSnapshot,
                telSnapshot = _uiState.value.telephony,
                score = score,
                probe = probe,
                wifiRadar = _uiState.value.wifiRadar,
                cellularRf = _uiState.value.cellularRf,
                isSynthetic = _uiState.value.activeSimulationScenario != SimulatedFaultScenario.NONE
            )

            _uiState.update { current ->
                current.copy(
                    isProbing = false,
                    probeResult = probe,
                    scoreResult = score,
                    troubleshootFindings = findings,
                    healerActions = healingActions,
                    prediction = prediction,
                    pulseMindExplanation = explanation,
                    mlTelemetryCount = telemetryCount,
                    snapshot = currentSnapshot.copy(classification = classification),
                    lastCheckedTimestamp = System.currentTimeMillis()
                )
            }
        }
    }

    // Phase 6: Run Multi-Host Ping Matrix
    fun runPingMatrix() {
        if (_uiState.value.isMatrixProbing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMatrixProbing = true) }
            val results = pingMatrixEngine.executeMatrixProbe()
            val healingActions = healerEngine.generateHealingPlan(
                _uiState.value.snapshot,
                _uiState.value.scoreResult,
                _uiState.value.probeResult,
                results,
                _uiState.value.dualStackResult
            )
            _uiState.update {
                it.copy(
                    pingMatrixResults = results,
                    isMatrixProbing = false,
                    healerActions = healingActions
                )
            }
        }
    }

    // Phase 6: Run Dual-Stack IPv4 / IPv6 Diagnostic
    fun runDualStackCheck() {
        if (_uiState.value.isDualStackProbing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDualStackProbing = true) }
            val dualStack = dualStackEngine.diagnoseDualStack()
            val healingActions = healerEngine.generateHealingPlan(
                _uiState.value.snapshot,
                _uiState.value.scoreResult,
                _uiState.value.probeResult,
                _uiState.value.pingMatrixResults,
                dualStack
            )
            _uiState.update {
                it.copy(
                    dualStackResult = dualStack,
                    isDualStackProbing = false,
                    healerActions = healingActions
                )
            }
        }
    }

    // Phase 6: Run Path Hop Traceroute
    fun runHopTrace(targetHost: String = "connectivitycheck.gstatic.com") {
        if (_uiState.value.isHopTracing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isHopTracing = true) }
            val gateway = _uiState.value.wifiRadar.gatewayIp
            val result = hopTracerEngine.tracePathToHost(targetHost, gateway)
            _uiState.update {
                it.copy(
                    hopTraceResult = result,
                    isHopTracing = false
                )
            }
        }
    }

    // Phase 8: Generate Technical Incident Report
    fun generateIncidentReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingReport = true) }
            val report = reportEngine.generateTechnicalReport(
                snapshot = _uiState.value.snapshot,
                telephony = _uiState.value.telephony,
                scoreResult = _uiState.value.scoreResult,
                probe = _uiState.value.probeResult,
                wifiRadar = _uiState.value.wifiRadar,
                cellularRf = _uiState.value.cellularRf,
                pingResults = _uiState.value.pingMatrixResults,
                dualStack = _uiState.value.dualStackResult,
                hopTrace = _uiState.value.hopTraceResult
            )
            _uiState.update {
                it.copy(
                    incidentReport = report,
                    isGeneratingReport = false
                )
            }
        }
    }

    fun shareIncidentReport() {
        val report = _uiState.value.incidentReport ?: return
        viewModelScope.launch {
            _uiEvents.emit(
                UiEvent.ShareText(
                    text = report.markdownContent,
                    title = "Share NetPulse Incident Report (${report.reportId})"
                )
            )
        }
    }

    fun executeHealerAction(action: HealerActionItem, context: Context) {
        // Record the attempt with its pre-recovery state immediately. The eventual
        // SUCCEEDED/FAILED verdict is decided later, from real PulseCore validation, by
        // RecoveryOutcomeTracker.resolvePending - never from this action merely completing.
        viewModelScope.launch {
            recoveryOutcomeTracker.recordAttempt(
                sessionId = sessionManager.currentSessionId,
                recoveryType = action.actionType.name,
                snapshot = _uiState.value.snapshot,
                score = _uiState.value.scoreResult
            )
        }

        when (action.actionType) {
            HealerActionType.AIRPLANE_CYCLE -> {
                try {
                    val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    viewModelScope.launch {
                        _uiEvents.emit(UiEvent.ShowToast("Toggle Airplane Mode ON for 5s then OFF to re-bind radio."))
                    }
                } catch (e: Exception) {
                    viewModelScope.launch {
                        _uiEvents.emit(UiEvent.ShowToast("Please open Airplane Mode in system settings."))
                    }
                }
            }
            HealerActionType.SWITCH_NETWORK -> {
                try {
                    val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    viewModelScope.launch {
                        _uiEvents.emit(UiEvent.ShowToast("Please open Network settings."))
                    }
                }
            }
            HealerActionType.OPEN_CAPTIVE_PORTAL -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://connectivitycheck.gstatic.com/generate_204"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    viewModelScope.launch {
                        _uiEvents.emit(UiEvent.ShowToast("Unable to open browser portal."))
                    }
                }
            }
            HealerActionType.OPTIMIZE_DNS -> {
                try {
                    val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    viewModelScope.launch {
                        _uiEvents.emit(UiEvent.ShowToast("Set Private DNS Provider Hostname to: ${action.recommendedValue ?: "one.one.one.one"}"))
                    }
                } catch (e: Exception) {
                    viewModelScope.launch {
                        _uiEvents.emit(UiEvent.ShowToast("Recommended Private DNS: ${action.recommendedValue ?: "one.one.one.one"}"))
                    }
                }
            }
            HealerActionType.FLUSH_SOCKET_CACHE -> {
                viewModelScope.launch {
                    runActiveProbe(_uiState.value.selectedMode)
                    runPingMatrix()
                    _uiEvents.emit(UiEvent.ShowToast("Socket connection pools and DNS cache refreshed successfully!"))
                }
            }
            else -> {
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    viewModelScope.launch {
                        _uiEvents.emit(UiEvent.ShowToast("Action triggered."))
                    }
                }
            }
        }
    }

    fun startSpeedTest() {
        if (_uiState.value.isSpeedTesting) return

        speedTestJob?.cancel()
        speedTestJob = viewModelScope.launch {
            _uiState.update { it.copy(isSpeedTesting = true) }

            speedTestEngine.executeSpeedTest().collect { state ->
                _uiState.update { it.copy(speedTestState = state) }
            }

            _uiState.update { it.copy(isSpeedTesting = false) }
        }
    }

    fun cancelSpeedTest() {
        speedTestJob?.cancel()
        _uiState.update {
            it.copy(
                isSpeedTesting = false,
                speedTestState = it.speedTestState.copy(statusMessage = "Test cancelled")
            )
        }
    }

    fun updateZombieAlertSetting(enabled: Boolean) {
        viewModelScope.launch { preferences.updateZombieAlerts(enabled) }
    }

    fun updateAlertThresholdSetting(threshold: Int) {
        viewModelScope.launch { preferences.updateAlertThreshold(threshold) }
    }

    fun updateSentinelIntervalSetting(seconds: Long) {
        viewModelScope.launch { preferences.updateSentinelInterval(seconds) }
    }

    fun updateDnsProviderSetting(provider: String) {
        viewModelScope.launch { preferences.updateDnsProvider(provider) }
    }

    fun updateDataSaverSetting(enabled: Boolean) {
        viewModelScope.launch { preferences.updateDataSaver(enabled) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { preferences.setOnboardingCompleted(true) }
    }

    fun clearLogHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            repository.deleteLog(id)
        }
    }

    fun exportDiagnosticsCsv(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val logs = _uiState.value.persistentLogs
            if (logs.isEmpty()) {
                _uiEvents.emit(UiEvent.ShowToast("No logs recorded to export."))
                return@launch
            }
            try {
                val file = DiagnosticExporter.exportLogsToCsv(context, logs)
                _uiEvents.emit(UiEvent.ShareCsv(file))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowToast("Export failed: ${e.localizedMessage}"))
            }
        }
    }

    /**
     * Exports the PRODUCTION (non-synthetic) ML telemetry dataset for offline
     * PulsePredictor experimentation (Part 13). Runs a final label-resolution pass first so
     * the export reflects the freshest labels currently resolvable.
     */
    fun exportMlDataset(context: Context, format: DatasetExportFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            labelResolutionService.resolvePendingLabels()
            val rows = database.telemetryObservationDao().getProductionObservations()
            if (rows.isEmpty()) {
                _uiEvents.emit(UiEvent.ShowToast("No production telemetry recorded to export yet."))
                return@launch
            }
            try {
                val file = DatasetExportService.export(context, rows, format)
                _uiEvents.emit(UiEvent.ShareFile(file, format.mimeType))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowToast("Dataset export failed: ${e.localizedMessage}"))
            }
        }
    }

    private suspend fun processTelemetryAndInference(
        snapshot: NetworkSnapshot,
        telSnapshot: TelephonySnapshot,
        score: UsabilityScoreResult,
        probe: DiagnosticProbeResult?,
        wifiRadar: WifiRadarSnapshot,
        cellularRf: CellularRfSnapshot,
        isSynthetic: Boolean
    ): Triple<PulsePrediction, PulseMindExplanation, Int> {
        val timestamp = System.currentTimeMillis()

        // A synthetic (FaultSimulator) tick must not roll the real session forward or mix
        // into it - only advance the real session manager for genuine device telemetry.
        val sessionId = if (isSynthetic) {
            sessionManager.currentSessionId
        } else {
            sessionManager.resolveSessionId(snapshot, telSnapshot, wifiBssid = wifiRadar.bssid, timestamp = timestamp)
        }

        val isCellular = snapshot.primaryTransport == com.netsense.netpulse.model.NetworkTransport.CELLULAR
        val isWifi = snapshot.primaryTransport == com.netsense.netpulse.model.NetworkTransport.WIFI
        // Only trust RF/Wi-Fi signal fields when RadarEngine actually measured them this
        // cycle - never let RadarEngine's display-only fallback numbers leak into training
        // data as if they were real readings.
        val isRfSignalMeasured = when {
            isCellular -> cellularRf.isRfDataMeasured
            isWifi -> wifiRadar.isRssiMeasured
            else -> false
        }

        val obs = RawTelemetryObservation(
            timestamp = timestamp,
            sessionId = sessionId,
            transport = snapshot.primaryTransport,
            dnsLatencyMs = probe?.averageDnsMs,
            tcpRttMs = probe?.averageTcpMs,
            tcpJitterMs = probe?.tcpJitterMs,
            httpTtfbMs = probe?.averageHttpMs,
            packetLossPct = probe?.packetLossPct ?: 0f,
            rsrpDbm = if (isCellular && cellularRf.isRfDataMeasured) cellularRf.rsrpDbm ?: telSnapshot.signalDbm else null,
            sinrDb = if (isCellular && cellularRf.isRfDataMeasured) cellularRf.sinrDb else null,
            wifiRssiDbm = if (isWifi && wifiRadar.isRssiMeasured) wifiRadar.rssiDbm else null,
            consecutiveProbeFailures = if (probe?.overallHttpSuccess == false) 1 else 0,
            usabilityScore = score.score,
            isValidated = snapshot.isValidated,
            isZombie = score.isZombieConnection
        )

        telemetryWindow.addObservation(obs)

        val observationQuality = ObservationQualityClassifier.classify(
            snapshot = snapshot,
            probe = probe,
            isRfSignalMeasured = isRfSignalMeasured,
            isSynthetic = isSynthetic
        )

        // Asynchronously record to Room for ML training dataset
        val entity = TelemetryObservationEntity(
            timestamp = obs.timestamp,
            sessionId = sessionId,
            transport = obs.transport.name,
            networkId = snapshot.interfaceName ?: telSnapshot.carrierName,
            rsrpDbm = obs.rsrpDbm,
            rsrqDb = if (isCellular && cellularRf.isRfDataMeasured) cellularRf.rsrqDb else null,
            sinrDb = obs.sinrDb,
            cqi = if (isCellular && cellularRf.isRfDataMeasured) cellularRf.cqi else null,
            wifiRssiDbm = obs.wifiRssiDbm,
            dnsLatencyMs = obs.dnsLatencyMs,
            dnsSuccess = probe?.overallDnsSuccess ?: false,
            tcpRttMs = obs.tcpRttMs,
            tcpSuccess = probe?.overallTcpSuccess ?: false,
            tcpJitterMs = obs.tcpJitterMs,
            httpTtfbMs = obs.httpTtfbMs,
            httpSuccess = probe?.overallHttpSuccess ?: false,
            isCaptivePortal = snapshot.isCaptivePortal,
            httpStatusCode = probe?.primaryEndpoint?.httpStatusCode,
            packetLossPct = obs.packetLossPct,
            consecutiveProbeFailures = obs.consecutiveProbeFailures,
            usabilityScore = obs.usabilityScore,
            isValidated = obs.isValidated,
            isZombie = obs.isZombie,
            primaryDiagnosis = score.primaryDiagnosis,
            observationQuality = observationQuality.name,
            isSynthetic = isSynthetic
        )
        repository.recordTelemetry(entity)

        val prediction = pulsePredictorEngine.predict(telemetryWindow)
        val explanation = pulseMindEngine.explain(
            snapshot = snapshot,
            scoreResult = score,
            probeResult = probe,
            prediction = prediction,
            rfSnapshot = cellularRf,
            wifiSnapshot = wifiRadar
        )
        val totalRecorded = repository.getTelemetryCount()

        return Triple(prediction, explanation, totalRecorded)
    }

    fun runPredictorBenchmark() {
        viewModelScope.launch {
            val sampleObs = telemetryWindow.getSnapshot()
            val benchmark = pulsePredictorEngine.benchmark(sampleObs)
            _uiState.update { it.copy(predictorBenchmark = benchmark) }
            _uiEvents.emit(UiEvent.ShowToast("Benchmark: ${benchmark.inferenceDurationMs}ms inference (${benchmark.modelVersion})"))
        }
    }

    fun consultAi(prompt: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConsultingAi = true) }
            val consultation = geminiAdvisorEngine.consult(
                prompt = prompt,
                snapshot = _uiState.value.snapshot,
                scoreResult = _uiState.value.scoreResult,
                probeResult = _uiState.value.probeResult,
                prediction = _uiState.value.prediction,
                rfSnapshot = _uiState.value.cellularRf,
                wifiSnapshot = _uiState.value.wifiRadar
            )
            _uiState.update {
                it.copy(
                    isConsultingAi = false,
                    geminiConsultation = consultation
                )
            }
        }
    }

    fun clearAiConsultation() {
        _uiState.update { it.copy(geminiConsultation = null) }
    }
}
