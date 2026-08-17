package com.example.ai.predictor

data class PredictorBenchmarkResult(
    val isModelInstalled: Boolean,
    val modelVersion: String,
    val loadDurationMs: Long,
    val preprocessingDurationNs: Long,
    val inferenceDurationMs: Double,
    val memoryFootprintBytes: Long,
    val featureCount: Int = PulsePredictorConfig.FEATURE_COUNT,
    val windowSize: Int = PulsePredictorConfig.WINDOW_SIZE
)
