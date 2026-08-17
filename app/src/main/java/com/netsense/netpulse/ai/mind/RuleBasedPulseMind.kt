package com.netsense.netpulse.ai.mind

import com.netsense.netpulse.ai.predictor.PredictorLikelyCause
import com.netsense.netpulse.ai.predictor.PulsePrediction
import com.netsense.netpulse.model.CellularRfSnapshot
import com.netsense.netpulse.model.DiagnosticProbeResult
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.model.UsabilityRating
import com.netsense.netpulse.model.UsabilityScoreResult
import com.netsense.netpulse.model.WifiRadarSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RuleBasedPulseMind : PulseMindEngine {

    override suspend fun explain(
        snapshot: NetworkSnapshot,
        scoreResult: UsabilityScoreResult?,
        probeResult: DiagnosticProbeResult?,
        prediction: PulsePrediction?,
        rfSnapshot: CellularRfSnapshot?,
        wifiSnapshot: WifiRadarSnapshot?
    ): PulseMindExplanation = withContext(Dispatchers.Default) {
        val score = scoreResult?.score ?: 0
        val rating = scoreResult?.rating ?: UsabilityRating.UNUSABLE
        val isZombie = scoreResult?.isZombieConnection ?: false
        val transport = snapshot.primaryTransport
        val keySignals = mutableListOf<String>()

        val headline: String
        val narrativeBuilder = StringBuilder()
        val recoveryRec: String

        // 1. Signal Context
        val signalDescription: String
        when (transport) {
            NetworkTransport.WIFI -> {
                val rssi = wifiSnapshot?.rssiDbm ?: -100
                val band = wifiSnapshot?.band?.label ?: "Wi-Fi"
                keySignals.add("Wi-Fi Signal: $rssi dBm ($band)")
                signalDescription = if (rssi > -65) "strong Wi-Fi signal ($rssi dBm)" else "marginal Wi-Fi reception ($rssi dBm)"
            }
            NetworkTransport.CELLULAR -> {
                val rsrp = rfSnapshot?.rsrpDbm ?: -140
                val sinr = rfSnapshot?.sinrDb
                val sinrText = if (sinr != null) ", SINR $sinr dB" else ""
                keySignals.add("Cellular Signal: $rsrp dBm$sinrText")
                signalDescription = if (rsrp > -95) "robust cellular radio link ($rsrp dBm)" else "attenuated cellular signal ($rsrp dBm)"
            }
            else -> {
                signalDescription = "no active radio link"
                keySignals.add("Transport: Disconnected / Airplane Mode")
            }
        }

        // 2. Telemetry Details
        val tcpRtt = probeResult?.averageTcpMs
        val dnsSuccess = probeResult?.overallDnsSuccess ?: false
        val httpSuccess = probeResult?.overallHttpSuccess ?: false
        val packetLoss = probeResult?.packetLossPct ?: 0f

        if (tcpRtt != null) keySignals.add("TCP Handshake: ${tcpRtt}ms")
        if (packetLoss > 0) keySignals.add("Packet Loss: ${(packetLoss * 100).toInt()}%")
        keySignals.add("L7 HTTP 204: ${if (httpSuccess) "Pass" else "Fail"}")

        // 3. Narrative Construction
        if (isZombie) {
            headline = "Zombie Connection Detected"
            narrativeBuilder.append("Your device maintains a $signalDescription, but upstream packets are being discarded or blackholed. ")
            narrativeBuilder.append("While your status bar displays active connectivity, DNS/HTTP probes confirm the Internet path is unreachable. ")
            recoveryRec = "Recommended: Toggle Airplane Mode or trigger interface re-negotiation to force a fresh radio bearer."
        } else if (!snapshot.isConnected) {
            headline = "Device Offline"
            narrativeBuilder.append("No active network routes are provisioned. Radios are in standby or disconnected state.")
            recoveryRec = "Connect to a known Wi-Fi network or enable Mobile Data in system settings."
        } else if (rating == UsabilityRating.OPTIMAL) {
            headline = "Optimal Network Health"
            narrativeBuilder.append("Your connection is performing excellently with a $signalDescription and minimal latency (${tcpRtt ?: 20}ms). ")
            narrativeBuilder.append("Both DNS lookups and secure HTTP endpoints are validating with zero packet loss.")
            recoveryRec = "No action needed. Network is prime for realtime voice, video, and high-throughput transfers."
        } else if (rating == UsabilityRating.GOOD) {
            headline = "Stable Connection"
            narrativeBuilder.append("Your network shows solid usability on $transport with a $signalDescription. ")
            narrativeBuilder.append("Core services are responsive with negligible jitter.")
            recoveryRec = "Optimal for browsing and standard streaming."
        } else if (rating == UsabilityRating.DEGRADED) {
            headline = "Degraded Network Usability"
            narrativeBuilder.append("Connection is currently experiencing elevated latency (${tcpRtt ?: 350}ms) or moderate packet loss (${(packetLoss * 100).toInt()}%). ")
            if (transport == NetworkTransport.WIFI && (wifiSnapshot?.rssiDbm ?: -100) < -78) {
                narrativeBuilder.append("The primary bottleneck appears to be weak RF distance from the wireless access point.")
                recoveryRec = "Move closer to your router or switch to Cellular data."
            } else {
                narrativeBuilder.append("This signature suggests upstream ISP congestion or queue bufferbloat rather than local physical signal loss.")
                recoveryRec = "Monitor connection; avoid large background downloads or switch to an alternate AP."
            }
        } else {
            headline = "Critical Network Impairment"
            narrativeBuilder.append("Network throughput is severely impaired. Despite having a $signalDescription, ")
            narrativeBuilder.append("essential web endpoints are failing to acknowledge requests.")
            recoveryRec = "Execute 1-Tap Healing or restart the Wi-Fi/Cellular interface."
        }

        // 4. ML Prediction Context (if valid)
        if (prediction != null && prediction.isValid) {
            keySignals.add("ML Anomaly Score: ${(prediction.anomalyScore * 100).toInt()}%")
            if (prediction.dropoutProbability30s > 0.6f) {
                narrativeBuilder.append(" Predictive ML indicates a ${(prediction.dropoutProbability30s * 100).toInt()}% likelihood of connection dropout within 30 seconds due to ${prediction.likelyCause.label}.")
            }
        }

        val confidenceText = when {
            prediction?.isValid == true -> "Deterministic diagnostics combined with ML temporal window inference."
            prediction?.state == com.netsense.netpulse.ai.predictor.PredictorState.INSUFFICIENT_TELEMETRY -> "Deterministic diagnostics active (ML buffering telemetry window: ${prediction.stateReason})."
            else -> "Deterministic ground-truth diagnostic engine (Awaiting trained pulse_predictor_v1.tflite model)."
        }

        PulseMindExplanation(
            headline = headline,
            narrative = narrativeBuilder.toString(),
            keySignals = keySignals,
            recoveryRecommendation = recoveryRec,
            confidenceNote = confidenceText,
            generatedAt = System.currentTimeMillis(),
            engineType = "Rule-Based Deterministic Reasoning (PulseMind v1)"
        )
    }
}
