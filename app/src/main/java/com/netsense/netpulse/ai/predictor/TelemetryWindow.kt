package com.netsense.netpulse.ai.predictor

import com.netsense.netpulse.model.NetworkTransport

data class RawTelemetryObservation(
    val timestamp: Long = System.currentTimeMillis(),
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

class TelemetryWindow(private val windowSize: Int = PulsePredictorConfig.WINDOW_SIZE) {

    private val lock = Any()
    private val observations = ArrayDeque<RawTelemetryObservation>(windowSize + 2)
    private var currentTransport: NetworkTransport = NetworkTransport.NONE
    private var lastObservationTimestamp: Long = 0L

    val size: Int
        get() = synchronized(lock) { observations.size }

    val isReady: Boolean
        get() = synchronized(lock) { observations.size >= windowSize }

    fun addObservation(obs: RawTelemetryObservation): Boolean {
        synchronized(lock) {
            // Check for transport changes (e.g., Cellular <-> Wi-Fi)
            if (currentTransport != NetworkTransport.NONE && obs.transport != NetworkTransport.NONE && currentTransport != obs.transport) {
                observations.clear()
                currentTransport = obs.transport
                lastObservationTimestamp = obs.timestamp
                observations.addLast(obs)
                return false // Triggered transport reset
            }

            // Check for large temporal gaps (> 10s without measurements)
            if (lastObservationTimestamp > 0 && (obs.timestamp - lastObservationTimestamp) > PulsePredictorConfig.TEMPORAL_GAP_THRESHOLD_MS) {
                // If there's a huge gap, reset window to avoid invalid velocity derivations
                observations.clear()
            }

            currentTransport = obs.transport
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
            lastObservationTimestamp = 0L
        }
    }
}
