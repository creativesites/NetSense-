package com.netsense.netpulse.ai.predictor

import com.netsense.netpulse.model.NetworkTransport

data class RawTelemetryObservation(
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val transport: NetworkTransport = NetworkTransport.NONE,
    val dnsLatencyMs: Long? = null,
    val tcpRttMs: Long? = null,
    val tcpJitterMs: Long? = null,
    val httpTtfbMs: Long? = null,
    val packetLossPct: Float = 0.0f,
    val rsrpDbm: Int? = null,
    val sinrDb: Int? = null,
    val wifiRssiDbm: Int? = null,
    val consecutiveProbeFailures: Int = 0,
    val usabilityScore: Int = 0,
    val isValidated: Boolean = false,
    val isZombie: Boolean = false
)

/**
 * In-memory sliding window feeding PulsePredictor inference. This is a *finer-grained*,
 * ML-input-specific notion of contiguity than [com.netsense.netpulse.dataset.NetworkSessionManager]'s
 * persisted `sessionId` (2 minute gap threshold): a window must stay valid as a contiguous
 * `[1, WINDOW_SIZE, FEATURE_COUNT]` CNN input, so it also resets on a much shorter
 * [PulsePredictorConfig.TEMPORAL_GAP_THRESHOLD_MS] gap and on a bare transport change.
 * It additionally resets whenever the observation's `sessionId` differs from the session
 * the window was built from, so it can never straddle a session boundary even in cases the
 * coarser checks below wouldn't catch on their own (e.g. Wi-Fi BSSID change).
 */
class TelemetryWindow(private val windowSize: Int = PulsePredictorConfig.WINDOW_SIZE) {

    private val lock = Any()
    private val observations = ArrayDeque<RawTelemetryObservation>(windowSize + 2)
    private var currentTransport: NetworkTransport = NetworkTransport.NONE
    private var currentSessionId: String = ""
    private var lastObservationTimestamp: Long = 0L

    val size: Int
        get() = synchronized(lock) { observations.size }

    val isReady: Boolean
        get() = synchronized(lock) { observations.size >= windowSize }

    fun addObservation(obs: RawTelemetryObservation): Boolean {
        synchronized(lock) {
            // Never straddle a session boundary (network session identity changed).
            val sessionChanged = currentSessionId.isNotEmpty() && obs.sessionId.isNotEmpty() && currentSessionId != obs.sessionId

            // Check for transport changes (e.g., Cellular <-> Wi-Fi)
            val transportChanged = currentTransport != NetworkTransport.NONE && obs.transport != NetworkTransport.NONE && currentTransport != obs.transport

            if (sessionChanged || transportChanged) {
                observations.clear()
                currentTransport = obs.transport
                currentSessionId = obs.sessionId
                lastObservationTimestamp = obs.timestamp
                observations.addLast(obs)
                return false // Triggered a window reset
            }

            // Check for large temporal gaps (> 10s without measurements)
            if (lastObservationTimestamp > 0 && (obs.timestamp - lastObservationTimestamp) > PulsePredictorConfig.TEMPORAL_GAP_THRESHOLD_MS) {
                // If there's a huge gap, reset window to avoid invalid velocity derivations
                observations.clear()
            }

            currentTransport = obs.transport
            currentSessionId = obs.sessionId
            lastObservationTimestamp = obs.timestamp

            if (observations.size >= windowSize) {
                observations.removeFirst()
            }
            observations.addLast(obs)
            return true
        }
    }

    fun getSnapshot(): List<RawTelemetryObservation> {
        synchronized(lock) {
            return observations.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            observations.clear()
            currentTransport = NetworkTransport.NONE
            currentSessionId = ""
            lastObservationTimestamp = 0L
        }
    }
}
