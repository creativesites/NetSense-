package com.netsense.netpulse

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.netsense.netpulse.analytics.DiagnosticExporter
import com.netsense.netpulse.dataset.DatasetExportFormat
import com.netsense.netpulse.dataset.DatasetExportService
import com.netsense.netpulse.service.NetPulseSentinelService
import com.netsense.netpulse.ui.NetPulseDashboard
import com.netsense.netpulse.ui.NetPulseViewModel
import com.netsense.netpulse.ui.OnboardingScreen
import com.netsense.netpulse.ui.PrimaryTab
import com.netsense.netpulse.ui.UiEvent
import com.netsense.netpulse.ui.theme.NetPulseTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NetPulseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetPulseTheme {
                val uiState by viewModel.uiState.collectAsState()

                val onboardingPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { /* re-checked reactively wherever it's read */ }

                // The Sentinel notification always taps through to Home (Section 18) - it
                // already shows whatever state (healthy/degraded/down/recovering) prompted it.
                LaunchedEffect(intent) {
                    if (intent?.getBooleanExtra(NetPulseSentinelService.EXTRA_OPEN_HOME, false) == true) {
                        viewModel.setPrimaryTab(PrimaryTab.HOME)
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.uiEvents.collect { event ->
                        when (event) {
                            is UiEvent.ShareCsv -> {
                                val shareIntent = DiagnosticExporter.shareCsvIntent(this@MainActivity, event.file)
                                startActivity(Intent.createChooser(shareIntent, "Share NetPulse Diagnostics CSV"))
                            }
                            is UiEvent.ShareFile -> {
                                val shareIntent = DatasetExportService.shareIntent(this@MainActivity, event.file, event.mimeType)
                                startActivity(Intent.createChooser(shareIntent, "Share NetPulse ML Dataset"))
                            }
                            is UiEvent.ShareText -> {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, event.text)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, event.title)
                                startActivity(shareIntent)
                            }
                            is UiEvent.ShowToast -> {
                                Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                  if (!uiState.appSettings.onboardingCompleted) {
                    OnboardingScreen(
                        onRequestPermissions = {
                            val perms = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.READ_PHONE_STATE
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            onboardingPermissionLauncher.launch(perms.toTypedArray())
                        },
                        onEnableSentinel = { viewModel.toggleSentinelService(this@MainActivity) },
                        onFinish = { viewModel.completeOnboarding() }
                    )
                  } else {
                    NetPulseDashboard(
                        uiState = uiState,
                        onRunProbe = { mode -> viewModel.runActiveProbe(mode) },
                        onSelectTab = { tab -> viewModel.setTab(tab) },
                        onSelectPrimaryTab = { tab -> viewModel.setPrimaryTab(tab) },
                        onFixIt = { viewModel.fixIt(this@MainActivity) },
                        onRetryFix = { viewModel.retryFix(this@MainActivity) },
                        onDismissHealingOutcome = { viewModel.dismissHealingOutcome() },
                        onSelectMode = { mode -> viewModel.setProbeMode(mode) },
                        onToggleSentinel = { viewModel.toggleSentinelService(this@MainActivity) },
                        onStartSpeedTest = { viewModel.startSpeedTest() },
                        onCancelSpeedTest = { viewModel.cancelSpeedTest() },
                        onSelectSimulation = { scenario -> viewModel.setSimulationScenario(scenario) },
                        onUpdateZombieAlerts = { enabled -> viewModel.updateZombieAlertSetting(enabled) },
                        onUpdateAlertThreshold = { threshold -> viewModel.updateAlertThresholdSetting(threshold) },
                        onUpdateSentinelInterval = { secs -> viewModel.updateSentinelIntervalSetting(secs) },
                        onUpdateDnsProvider = { dns -> viewModel.updateDnsProviderSetting(dns) },
                        onUpdateDataSaver = { enabled -> viewModel.updateDataSaverSetting(enabled) },
                        onExportCsv = { viewModel.exportDiagnosticsCsv(this@MainActivity) },
                        onExportMlDataset = { viewModel.exportMlDataset(this@MainActivity, DatasetExportFormat.JSONL) },
                        onClearHistory = { viewModel.clearLogHistory() },
                        onDeleteLog = { id -> viewModel.deleteLog(id) },
                        onRunPingMatrix = { viewModel.runPingMatrix() },
                        onRunDualStackCheck = { viewModel.runDualStackCheck() },
                        onRunHopTrace = { target -> viewModel.runHopTrace(target) },
                        onSelectCategoryFilter = { cat -> viewModel.setCategoryFilter(cat) },
                        onExecuteHealerAction = { action -> viewModel.executeHealerAction(action, this@MainActivity) },
                        onGenerateIncidentReport = { viewModel.generateIncidentReport() },
                        onShareIncidentReport = { viewModel.shareIncidentReport() },
                        onRunBenchmark = { viewModel.runPredictorBenchmark() },
                        onConsultAi = { prompt -> viewModel.consultAi(prompt) },
                        onClearAiConsultation = { viewModel.clearAiConsultation() }
                    )
                  }
                }
            }
        }
    }
}
