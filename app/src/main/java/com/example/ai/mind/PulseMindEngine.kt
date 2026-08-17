package com.example.ai.mind

import com.example.ai.predictor.PulsePrediction
import com.example.model.CellularRfSnapshot
import com.example.model.DiagnosticProbeResult
import com.example.model.NetworkSnapshot
import com.example.model.UsabilityScoreResult
import com.example.model.WifiRadarSnapshot

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
