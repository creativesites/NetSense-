package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.analytics.DiagnosticExporter
import com.example.ui.NetPulseDashboard
import com.example.ui.NetPulseViewModel
import com.example.ui.UiEvent
import com.example.ui.theme.NetPulseTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NetPulseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetPulseTheme {
                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.uiEvents.collect { event ->
                        when (event) {
                            is UiEvent.ShareCsv -> {
                                val shareIntent = DiagnosticExporter.shareCsvIntent(this@MainActivity, event.file)
                                startActivity(Intent.createChooser(shareIntent, "Share NetPulse Diagnostics CSV"))
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
                    NetPulseDashboard(
                        uiState = uiState,
                        onRunProbe = { mode -> viewModel.runActiveProbe(mode) },
                        onSelectTab = { tab -> viewModel.setTab(tab) },
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
