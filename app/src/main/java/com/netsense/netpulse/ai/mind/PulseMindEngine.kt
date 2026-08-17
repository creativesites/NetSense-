package com.netsense.netpulse.ai.mind

import com.netsense.netpulse.ai.predictor.PulsePrediction
import com.netsense.netpulse.model.CellularRfSnapshot
import com.netsense.netpulse.model.DiagnosticProbeResult
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.UsabilityScoreResult
import com.netsense.netpulse.model.WifiRadarSnapshot

data class PulseMindExplanation(
    val headline: String,
    val narrative: String,
    val keySignals: List<String>,
    val recoveryRecommendation: String,
    val confidenceNote: String,
    val generatedAt: Long = System.currentTimeMillis(),
    val engineType: String = "Rule-Based Deterministic Explainer"
)

interface PulseMindEngine {
    suspend fun explain(
        snapshot: NetworkSnapshot,
        scoreResult: UsabilityScoreResult?,
        probeResult: DiagnosticProbeResult?,
        prediction: PulsePrediction?,
        rfSnapshot: CellularRfSnapshot?,
        wifiSnapshot: WifiRadarSnapshot?
    ): PulseMindExplanation
}
