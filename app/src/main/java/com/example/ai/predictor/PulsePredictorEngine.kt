package com.example.ai.predictor

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class PulsePredictorEngine(
    private val context: Context,
    private val featureExtractor: PulseFeatureExtractor = PulseFeatureExtractor()
) {
    companion object {
        private const val TAG = "PulsePredictorEngine"
        const val MODEL_VERSION = "pulse_predictor_v1_1dcnn"
    }

    private var interpreter: Interpreter? = null
    var isModelInstalled: Boolean = false
        private set
    var modelStatusReason: String = "Initializing..."
        private set
    private var modelLoadDurationMs: Long = 0L
    private var modelSizeBytes: Long = 0L

    init {
        loadModelIfAvailable()
    }

    private fun loadModelIfAvailable() {
        val start = System.currentTimeMillis()
        try {
            val assetManager = context.assets
            val assetFileDescriptor = try {
                assetManager.openFd(PulsePredictorConfig.MODEL_ASSET_PATH)
            } catch (e: Exception) {
                null
            }

            if (assetFileDescriptor != null) {
                val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
                val fileChannel = fileInputStream.channel
                val startOffset = assetFileDescriptor.startOffset
                val declaredLength = assetFileDescriptor.declaredLength
                val modelBuffer: ByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

                val options = Interpreter.Options().apply {
                    setNumThreads(2) // Low CPU multi-threading
                    setUseNNAPI(false) // CPU only for micro-inferences
                }

                interpreter = Interpreter(modelBuffer, options)
                isModelInstalled = true
                modelSizeBytes = declaredLength
                modelStatusReason = "Active (TFLite 1D-CNN Model Loaded)"
                modelLoadDurationMs = System.currentTimeMillis() - start
                Log.i(TAG, "Successfully loaded $MODEL_VERSION ($modelSizeBytes bytes) in $modelLoadDurationMs ms")
            } else {
                isModelInstalled = false
                modelStatusReason = "Awaiting trained pulse_predictor_v1.tflite. Telemetry logging active for future dataset generation."
                Log.d(TAG, modelStatusReason)
            }
        } catch (e: Exception) {
            isModelInstalled = false
            modelStatusReason = "Model load failed: ${e.localizedMessage}. Operating in baseline logging mode."
            Log.w(TAG, "Failed to initialize TFLite interpreter", e)
        }
    }

    suspend fun predict(window: TelemetryWindow): PulsePrediction = withContext(Dispatchers.Default) {
        val observations = window.getSnapshot()

        // Guard 1: Model existence
        if (!isModelInstalled || interpreter == null) {
            return@withContext PulsePrediction(
                anomalyScore = 0.0f,
                dropoutProbability30s = 0.0f,
                degradationProbability15s = 0.0f,
                likelyCause = PredictorLikelyCause.UNKNOWN,
                modelConfidence = 0.0f,
                modelVersion = MODEL_VERSION,
                isValid = false,
                state = PredictorState.PREDICTOR_UNAVAILABLE,
                stateReason = modelStatusReason
            )
        }

        // Guard 2: Window fullness
        if (observations.size < PulsePredictorConfig.WINDOW_SIZE) {
            return@withContext PulsePrediction(
                anomalyScore = 0.0f,
                dropoutProbability30s = 0.0f,
                degradationProbability15s = 0.0f,
                likelyCause = PredictorLikelyCause.UNKNOWN,
                modelConfidence = 0.0f,
                modelVersion = MODEL_VERSION,
                isValid = false,
                state = PredictorState.INSUFFICIENT_TELEMETRY,
                stateReason = "Buffering telemetry observations (${observations.size} / ${PulsePredictorConfig.WINDOW_SIZE} steps)"
            )
        }

        // Feature extraction
        val inputBuffer = featureExtractor.extractByteBuffer(observations)
        if (inputBuffer == null) {
            return@withContext PulsePrediction(
                isValid = false,
                state = PredictorState.ERROR,
                stateReason = "Failed to construct input tensor"
            )
        }

        try {
            // Multi-task output heads:
            // 0: Anomaly Score (1, 1)
            // 1: Dropout 30s Probability (1, 1)
            // 2: Degradation 15s Probability (1, 1)
            // 3: Likely Cause Softmax (1, 5)
            val anomalyOut = Array(1) { FloatArray(1) }
            val dropoutOut = Array(1) { FloatArray(1) }
            val degradationOut = Array(1) { FloatArray(1) }
            val causeOut = Array(1) { FloatArray(5) }

            val outputs = mutableMapOf<Int, Any>(
                0 to anomalyOut,
                1 to dropoutOut,
                2 to degradationOut,
                3 to causeOut
            )

            interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

            val anomalyScore = anomalyOut[0][0].coerceIn(0.0f, 1.0f)
            val dropout30s = dropoutOut[0][0].coerceIn(0.0f, 1.0f)
            val degradation15s = degradationOut[0][0].coerceIn(0.0f, 1.0f)

            val causeProbabilities = mapOf(
                PredictorLikelyCause.RF_FADING to causeOut[0][0].coerceIn(0.0f, 1.0f),
                PredictorLikelyCause.UPSTREAM_CONGESTION to causeOut[0][1].coerceIn(0.0f, 1.0f),
                PredictorLikelyCause.DNS_BLACKHOLE to causeOut[0][2].coerceIn(0.0f, 1.0f),
                PredictorLikelyCause.CAPTIVE_PORTAL to causeOut[0][3].coerceIn(0.0f, 1.0f),
                PredictorLikelyCause.GATEWAY_DEAD to causeOut[0][4].coerceIn(0.0f, 1.0f)
            )

            val likelyCause = causeProbabilities.maxByOrNull { it.value }?.key ?: PredictorLikelyCause.UNKNOWN
            val confidence = causeProbabilities[likelyCause] ?: 0.0f

            PulsePrediction(
                anomalyScore = anomalyScore,
                dropoutProbability30s = dropout30s,
                degradationProbability15s = degradation15s,
                likelyCause = likelyCause,
                causeProbabilities = causeProbabilities,
                modelConfidence = confidence,
                modelVersion = MODEL_VERSION,
                timestamp = System.currentTimeMillis(),
                isValid = true,
                state = PredictorState.VALID_PREDICTION,
                stateReason = "Inference completed successfully"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing TFLite inference", e)
            PulsePrediction(
                isValid = false,
                state = PredictorState.ERROR,
                stateReason = "Inference error: ${e.localizedMessage}"
            )
        }
    }

    suspend fun benchmark(sampleWindow: List<RawTelemetryObservation>): PredictorBenchmarkResult = withContext(Dispatchers.Default) {
        val preStart = System.nanoTime()
        val byteBuffer = featureExtractor.extractByteBuffer(sampleWindow)
        val preEnd = System.nanoTime()
        val preDurationNs = preEnd - preStart

        var inferenceMs = 0.0
        if (isModelInstalled && interpreter != null && byteBuffer != null) {
            val runs = 5
            var totalDuration = 0L
            for (i in 0 until runs) {
                byteBuffer.rewind()
                val anomalyOut = Array(1) { FloatArray(1) }
                val dropoutOut = Array(1) { FloatArray(1) }
                val degradationOut = Array(1) { FloatArray(1) }
                val causeOut = Array(1) { FloatArray(5) }
                val outputs = mapOf(
                    0 to anomalyOut,
                    1 to dropoutOut,
                    2 to degradationOut,
                    3 to causeOut
                )
                val infStart = System.nanoTime()
                interpreter?.runForMultipleInputsOutputs(arrayOf(byteBuffer), outputs)
                val infEnd = System.nanoTime()
                totalDuration += (infEnd - infStart)
            }
            inferenceMs = (totalDuration.toDouble() / runs) / 1_000_000.0
        }

        PredictorBenchmarkResult(
            isModelInstalled = isModelInstalled,
            modelVersion = MODEL_VERSION,
            loadDurationMs = modelLoadDurationMs,
            preprocessingDurationNs = preDurationNs,
            inferenceDurationMs = inferenceMs,
            memoryFootprintBytes = modelSizeBytes
        )
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
