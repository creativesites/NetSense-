package com.netsense.netpulse.ai.predictor

/**
 * Versioned documentation of PulseFeatureExtractor's per-step feature vector, so a future
 * consumer (dataset export, an offline training script, or the eventual TFLite model) always
 * knows exactly what each of the [PulsePredictorConfig.FEATURE_COUNT] values in a
 * `[1, WINDOW_SIZE, FEATURE_COUNT]` input tensor means, without reading Kotlin source.
 *
 * The feature order below is the stable, documented contract. Do not reorder or resize
 * without bumping [PulsePredictorConfig.FEATURE_NORMALIZATION_VERSION] and updating this
 * table to match - the app must never silently change feature ordering out from under a
 * model trained against a specific version.
 */
object PulseFeatureSchema {

    /**
     * Sentinel written in place of any feature whose underlying measurement was
     * unavailable this step. Every measurement-based feature's real normalized range is
     * clamped to [0.0, 1.0], so -1.0 can never collide with a genuine reading in that
     * column - unlike schema v1, which defaulted missing values to 0.0, indistinguishable
     * from an actual best-case reading (e.g. "0ms DNS latency"). The transport-type column
     * (index 11) separately uses -1.0 to mean the real category "NONE"; that is a distinct
     * column with its own documented meaning, not a collision.
     */
    const val MISSING_VALUE_SENTINEL = -1.0f

    data class FeatureDescriptor(
        val index: Int,
        val featureName: String,
        val source: String,
        val unit: String,
        val normalization: String,
        val validRange: String,
        val missingValueBehavior: String,
        val meaning: String
    )

    val FEATURES: List<FeatureDescriptor> = listOf(
        FeatureDescriptor(
            0, "dns_latency", "DiagnosticEngine active probe", "ms",
            "min-max [0, 1000] -> [0, 1]", "[0.0, 1.0]",
            "MISSING_VALUE_SENTINEL if no DNS probe ran this step or resolution failed",
            "DNS resolution time for the active probe"
        ),
        FeatureDescriptor(
            1, "tcp_rtt", "DiagnosticEngine active probe", "ms",
            "min-max [0, 1500] -> [0, 1]", "[0.0, 1.0]",
            "MISSING_VALUE_SENTINEL if no TCP probe ran this step or the handshake failed",
            "Layer 4 TCP handshake round-trip time"
        ),
        FeatureDescriptor(
            2, "tcp_jitter", "DiagnosticEngine DEEP-mode probe", "ms",
            "min-max [0, 500] -> [0, 1]", "[0.0, 1.0]",
            "MISSING_VALUE_SENTINEL if jitter wasn't measured this step",
            "Variance between successive TCP RTT samples"
        ),
        FeatureDescriptor(
            3, "http_ttfb", "DiagnosticEngine active probe", "ms",
            "min-max [0, 2000] -> [0, 1]", "[0.0, 1.0]",
            "MISSING_VALUE_SENTINEL if no HTTP probe ran this step or it failed",
            "Layer 7 HTTP time-to-first-byte"
        ),
        FeatureDescriptor(
            4, "packet_loss", "DiagnosticEngine multi-socket probe", "ratio",
            "already [0, 1], clamped", "[0.0, 1.0]",
            "Always computable (DiagnosticEngine returns a ratio, never null); no sentinel needed",
            "Estimated packet loss across probe sockets"
        ),
        FeatureDescriptor(
            5, "cellular_rsrp", "RadarEngine registered-cell reading", "dBm",
            "min-max [-140, -44] -> [0, 1]", "[0.0, 1.0]",
            "MISSING_VALUE_SENTINEL unless RadarEngine's isRfDataMeasured was true this step",
            "Cellular reference signal received power"
        ),
        FeatureDescriptor(
            6, "cellular_sinr", "RadarEngine registered-cell reading", "dB",
            "min-max [-10, 30] -> [0, 1]", "[0.0, 1.0]",
            "MISSING_VALUE_SENTINEL unless RadarEngine's isRfDataMeasured was true this step",
            "Cellular signal-to-interference-plus-noise ratio"
        ),
        FeatureDescriptor(
            7, "wifi_rssi", "RadarEngine WifiInfo reading", "dBm",
            "min-max [-100, -30] -> [0, 1]", "[0.0, 1.0]",
            "MISSING_VALUE_SENTINEL unless RadarEngine's isRssiMeasured was true this step",
            "Wi-Fi received signal strength"
        ),
        FeatureDescriptor(
            8, "latency_velocity", "Derived: this step vs previous step", "ms/s",
            "min-max [-500, 500] -> [0, 1], 0.5 = no change", "[0.0, 1.0]",
            "MISSING_VALUE_SENTINEL if there is no previous step or either TCP RTT is missing",
            "Rate of change of TCP RTT"
        ),
        FeatureDescriptor(
            9, "signal_velocity", "Derived: this step vs previous step", "dBm/s",
            "min-max [-30, 30] -> [0, 1], 0.5 = no change", "[0.0, 1.0]",
            "MISSING_VALUE_SENTINEL if there is no previous step or either signal reading is missing",
            "Rate of change of RF signal (cellular RSRP or Wi-Fi RSSI, whichever applies)"
        ),
        FeatureDescriptor(
            10, "consecutive_failures", "Derived: ViewModel probe history", "count",
            "linear [0, MAX_CONSECUTIVE_FAILURES] -> [0, 1]", "[0.0, 1.0]",
            "Always computable (defaults to 0); no sentinel needed",
            "Consecutive probe failures leading into this step"
        ),
        FeatureDescriptor(
            11, "transport_type", "ConnectivityMonitor direct measurement", "categorical",
            "fixed encoding, see PulsePredictorConfig.TRANSPORT_*", "{-1.0, 0.0, 0.5, 1.0}",
            "Always computable; -1.0 here means the real category NONE, not \"missing\"",
            "Active transport: cellular / wifi / other / none"
        )
    )
}
