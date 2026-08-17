package com.example.ai.predictor

import com.example.model.NetworkTransport
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PulseFeatureExtractor {

    companion object {
        const val VERSION = PulsePredictorConfig.FEATURE_NORMALIZATION_VERSION
        const val WINDOW_SIZE = PulsePredictorConfig.WINDOW_SIZE
        const val FEATURE_COUNT = PulsePredictorConfig.FEATURE_COUNT
    }

    /**
     * Extracts and normalizes the [1, 15, 12] feature tensor from a 15-step sequence of observations.
     * Returns null if the window contains fewer than 15 observations.
     */
    fun extractTensor(window: List<RawTelemetryObservation>): Array<Array<FloatArray>>? {
        if (window.size < WINDOW_SIZE) return null

        val subList = window.takeLast(WINDOW_SIZE)
        val matrix = Array(WINDOW_SIZE) { FloatArray(FEATURE_COUNT) }

        for (i in 0 until WINDOW_SIZE) {
            val curr = subList[i]
            val prev = if (i > 0) subList[i - 1] else null
            matrix[i] = extractSingleStepFeatures(curr, prev)
        }

        return arrayOf(matrix)
    }

    /**
     * Extracts features as a Direct ByteBuffer suitable for TFLite C++ execution.
     */
    fun extractByteBuffer(window: List<RawTelemetryObservation>): ByteBuffer? {
        val tensor = extractTensor(window) ?: return null
        val byteBuffer = ByteBuffer.allocateDirect(1 * WINDOW_SIZE * FEATURE_COUNT * 4) // 4 bytes per float
        byteBuffer.order(ByteOrder.nativeOrder())

        for (step in 0 until WINDOW_SIZE) {
            for (feature in 0 until FEATURE_COUNT) {
                byteBuffer.putFloat(tensor[0][step][feature])
            }
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    /**
     * Extracts 12 normalized features for a single step.
     */
    fun extractSingleStepFeatures(
        curr: RawTelemetryObservation,
        prev: RawTelemetryObservation?
    ): FloatArray {
        val features = FloatArray(FEATURE_COUNT)

        // 1. DNS Latency (0 .. 1000 ms)
        features[0] = normalize(
            curr.dnsLatencyMs?.toFloat(),
            PulsePredictorConfig.DNS_LATENCY_MIN_MS,
            PulsePredictorConfig.DNS_LATENCY_MAX_MS,
            defaultValue = 0.0f
        )

        // 2. TCP RTT (0 .. 1500 ms)
        features[1] = normalize(
            curr.tcpRttMs?.toFloat(),
            PulsePredictorConfig.TCP_RTT_MIN_MS,
            PulsePredictorConfig.TCP_RTT_MAX_MS,
            defaultValue = 0.0f
        )

        // 3. TCP Jitter (0 .. 500 ms)
        features[2] = normalize(
            curr.tcpJitterMs?.toFloat(),
            PulsePredictorConfig.TCP_JITTER_MIN_MS,
            PulsePredictorConfig.TCP_JITTER_MAX_MS,
            defaultValue = 0.0f
        )

        // 4. HTTP TTFB (0 .. 2000 ms)
        features[3] = normalize(
            curr.httpTtfbMs?.toFloat(),
            PulsePredictorConfig.HTTP_TTFB_MIN_MS,
            PulsePredictorConfig.HTTP_TTFB_MAX_MS,
            defaultValue = 0.0f
        )

        // 5. Packet Loss Ratio (0.0 .. 1.0)
        features[4] = curr.packetLossPct.coerceIn(0.0f, 1.0f)

        // 6. Cellular RSRP (-140 .. -44 dBm)
        features[5] = normalize(
            curr.rsrpDbm?.toFloat(),
            PulsePredictorConfig.RSRP_MIN_DBM,
            PulsePredictorConfig.RSRP_MAX_DBM,
            defaultValue = 0.0f
        )

        // 7. Cellular SINR (-10 .. 30 dB)
        features[6] = normalize(
            curr.sinrDb?.toFloat(),
            PulsePredictorConfig.SINR_MIN_DB,
            PulsePredictorConfig.SINR_MAX_DB,
            defaultValue = 0.0f
        )

        // 8. Wi-Fi RSSI (-100 .. -30 dBm)
        features[7] = normalize(
            curr.wifiRssiDbm?.toFloat(),
            PulsePredictorConfig.WIFI_RSSI_MIN_DBM,
            PulsePredictorConfig.WIFI_RSSI_MAX_DBM,
            defaultValue = 0.0f
        )

        // 9. Latency Velocity (ms/s change compared to previous observation)
        val latencyVelocity = if (prev != null && curr.tcpRttMs != null && prev.tcpRttMs != null) {
            val dtSec = ((curr.timestamp - prev.timestamp).coerceAtLeast(500L)) / 1000.0f
            ((curr.tcpRttMs - prev.tcpRttMs).toFloat()) / dtSec
        } else {
            0.0f
        }
        features[8] = normalize(
            latencyVelocity,
            PulsePredictorConfig.LATENCY_VELOCITY_MIN,
            PulsePredictorConfig.LATENCY_VELOCITY_MAX,
            defaultValue = 0.5f // 0.5 = 0 velocity in centered range
        )

        // 10. Signal Velocity (dBm/s change compared to previous observation)
        val signalVelocity = if (prev != null) {
            val currSig = curr.rsrpDbm ?: curr.wifiRssiDbm
            val prevSig = prev.rsrpDbm ?: prev.wifiRssiDbm
            if (currSig != null && prevSig != null) {
                val dtSec = ((curr.timestamp - prev.timestamp).coerceAtLeast(500L)) / 1000.0f
                (currSig - prevSig).toFloat() / dtSec
            } else {
                0.0f
            }
        } else {
            0.0f
        }
        features[9] = normalize(
            signalVelocity,
            PulsePredictorConfig.SIGNAL_VELOCITY_MIN,
            PulsePredictorConfig.SIGNAL_VELOCITY_MAX,
            defaultValue = 0.5f // 0.5 = 0 velocity
        )

        // 11. Consecutive Probe Failures (0 .. 5)
        features[10] = (curr.consecutiveProbeFailures.toFloat() / PulsePredictorConfig.MAX_CONSECUTIVE_FAILURES).coerceIn(0.0f, 1.0f)

        // 12. Transport Type Encoded
        features[11] = when (curr.transport) {
            NetworkTransport.CELLULAR -> PulsePredictorConfig.TRANSPORT_CELLULAR
            NetworkTransport.WIFI -> PulsePredictorConfig.TRANSPORT_WIFI
            NetworkTransport.ETHERNET, NetworkTransport.BLUETOOTH, NetworkTransport.VPN, NetworkTransport.OTHER -> PulsePredictorConfig.TRANSPORT_OTHER
            NetworkTransport.NONE -> PulsePredictorConfig.TRANSPORT_NONE
        }

        return features
    }

    private fun normalize(value: Float?, min: Float, max: Float, defaultValue: Float): Float {
        if (value == null) return defaultValue
        val clamped = value.coerceIn(min, max)
        return (clamped - min) / (max - min)
    }
}
