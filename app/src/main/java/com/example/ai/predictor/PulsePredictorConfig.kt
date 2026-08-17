package com.example.ai.predictor

object PulsePredictorConfig {
    const val FEATURE_NORMALIZATION_VERSION = 1
    const val WINDOW_SIZE = 15
    const val FEATURE_COUNT = 12
    const val MODEL_ASSET_PATH = "models/pulse_predictor_v1.tflite"

    // Normalization boundaries (Version 1)
    // Feature 1: DNS Latency (0 .. 1000 ms)
    const val DNS_LATENCY_MIN_MS = 0.0f
    const val DNS_LATENCY_MAX_MS = 1000.0f

    // Feature 2: TCP RTT (0 .. 1500 ms)
    const val TCP_RTT_MIN_MS = 0.0f
    const val TCP_RTT_MAX_MS = 1500.0f

    // Feature 3: TCP Jitter (0 .. 500 ms)
    const val TCP_JITTER_MIN_MS = 0.0f
    const val TCP_JITTER_MAX_MS = 500.0f

    // Feature 4: HTTP TTFB (0 .. 2000 ms)
    const val HTTP_TTFB_MIN_MS = 0.0f
    const val HTTP_TTFB_MAX_MS = 2000.0f

    // Feature 5: Packet Loss (0.0 .. 1.0)
    const val PACKET_LOSS_MIN = 0.0f
    const val PACKET_LOSS_MAX = 1.0f

    // Feature 6: Cellular RSRP (-140 .. -44 dBm)
    const val RSRP_MIN_DBM = -140.0f
    const val RSRP_MAX_DBM = -44.0f

    // Feature 7: Cellular SINR (-10 .. 30 dB)
    const val SINR_MIN_DB = -10.0f
    const val SINR_MAX_DB = 30.0f

    // Feature 8: Wi-Fi RSSI (-100 .. -30 dBm)
    const val WIFI_RSSI_MIN_DBM = -100.0f
    const val WIFI_RSSI_MAX_DBM = -30.0f

    // Feature 9: Latency Velocity (-500 .. 500 ms/sec)
    const val LATENCY_VELOCITY_MIN = -500.0f
    const val LATENCY_VELOCITY_MAX = 500.0f

    // Feature 10: Signal Velocity (-30 .. 30 dBm/sec)
    const val SIGNAL_VELOCITY_MIN = -30.0f
    const val SIGNAL_VELOCITY_MAX = 30.0f

    // Feature 11: Consecutive Failures (0 .. 5)
    const val MAX_CONSECUTIVE_FAILURES = 5.0f

    // Feature 12: Transport Type Encoded
    const val TRANSPORT_CELLULAR = 0.0f
    const val TRANSPORT_WIFI = 1.0f
    const val TRANSPORT_OTHER = 0.5f
    const val TRANSPORT_NONE = -1.0f

    // Missing value indicators
    const val VALUE_UNAVAILABLE = 0.0f
    const val TEMPORAL_GAP_THRESHOLD_MS = 10_000L // 10 seconds
}
