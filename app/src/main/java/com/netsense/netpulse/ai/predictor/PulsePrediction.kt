package com.netsense.netpulse.ai.predictor

enum class PredictorLikelyCause(val label: String, val description: String) {
    RF_FADING("RF Signal Fading", "Physical radio signal attenuation or path loss"),
    UPSTREAM_CONGESTION("Upstream Congestion", "Network queue bufferbloat or ISP peering saturation"),
    DNS_BLACKHOLE("DNS Blackhole / Poisoning", "DNS resolution failure or recursive resolver timeout"),
    CAPTIVE_PORTAL("Captive Portal Interception", "Gateway intercepting HTTP/HTTPS traffic for authentication"),
    GATEWAY_DEAD("Gateway / Default Route Dead", "Local router or baseband gateway unreachable"),
    UNKNOWN("Unknown / Inconclusive", "No distinct failure signature matched")
}

enum class PredictorState(val displayTitle: String) {
    VALID_PREDICTION("Active Inference"),
    PREDICTOR_UNAVAILABLE("Model Not Installed"),
    INSUFFICIENT_TELEMETRY("Buffering Telemetry Window"),
    COLLECTING_TRAINING_DATA("Collecting Training Dataset"),
    TRANSPORT_TRANSITION("Transport Re-syncing"),
    ERROR("Inference Error")
}

data class PulsePrediction(
    val anomalyScore: Float = 0.0f,
    val dropoutProbability30s: Float = 0.0f,
    val degradationProbability15s: Float = 0.0f,
    val likelyCause: PredictorLikelyCause = PredictorLikelyCause.UNKNOWN,
    val causeProbabilities: Map<PredictorLikelyCause, Float> = emptyMap(),
    val modelConfidence: Float = 0.0f,
    val modelVersion: String = "v1-untrained",
    val timestamp: Long = System.currentTimeMillis(),
    val isValid: Boolean = false,
    val state: PredictorState = PredictorState.PREDICTOR_UNAVAILABLE,
    val stateReason: String = "Awaiting trained model weights."
) {
    companion object {
        fun unavailable(reason: String = "No trained TFLite model packaged in assets/models/pulse_predictor_v1.tflite"): PulsePrediction {
            return PulsePrediction(
                isValid = false,
                state = PredictorState.PREDICTOR_UNAVAILABLE,
                stateReason = reason
            )
        }

        fun buffering(currentCount: Int, requiredCount: Int): PulsePrediction {
            return PulsePrediction(
                isValid = false,
                state = PredictorState.INSUFFICIENT_TELEMETRY,
                stateReason = "Buffering telemetry observations ($currentCount / $requiredCount steps ready)"
            )
        }

        fun transportTransition(): PulsePrediction {
            return PulsePrediction(
                isValid = false,
                state = PredictorState.TRANSPORT_TRANSITION,
                stateReason = "Network interface changed. Resetting temporal telemetry window."
            )
        }
    }
}
