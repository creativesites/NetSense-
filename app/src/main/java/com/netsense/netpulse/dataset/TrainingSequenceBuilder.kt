package com.netsense.netpulse.dataset

import com.netsense.netpulse.ai.predictor.PulseFeatureExtractor
import com.netsense.netpulse.ai.predictor.PulsePredictorConfig
import com.netsense.netpulse.ai.predictor.RawTelemetryObservation
import com.netsense.netpulse.data.TelemetryObservationEntity
import com.netsense.netpulse.model.NetworkTransport

/**
 * One windowed, labeled training example (Part 16). Purely a data-assembly artifact - this
 * is NOT model input at runtime and nothing here trains anything. It exists so an offline
 * training environment (outside this Android app) has a stable, self-describing shape to
 * consume instead of hand-assembling windows from raw Room rows.
 */
data class TrainingSequence(
    val sessionId: String,
    val sequenceStart: Long,
    val sequenceEnd: Long,
    /** [PulsePredictorConfig.WINDOW_SIZE] rows of [PulsePredictorConfig.FEATURE_COUNT] values, in PulseFeatureSchema order. */
    val featureMatrix: List<List<Float>>,
    val degradation15s: ResolvedLabel,
    val dropout30s: ResolvedLabel,
    val likelyCause: ResolvedLikelyCause,
    /** Quality of each of the WINDOW_SIZE rows that make up this sequence, in order. */
    val observationQualities: List<ObservationQuality>,
    val featureSchemaVersion: Int,
    val labelSchemaVersion: Int
) {
    /** True only when both look-ahead labels were fully resolved - the only sequences a training run should actually use. */
    val isFullyLabeled: Boolean
        get() = degradation15s.status == LabelResolutionStatus.RESOLVED && dropout30s.status == LabelResolutionStatus.RESOLVED
}

/**
 * Assembles [TrainingSequence]s from a session's persisted observations and their resolved
 * labels (from [LabelResolver]/[LabelResolutionService]). Data assembly only - does not run
 * PulsePredictorEngine, does not touch TFLite, does not train anything (Part 18).
 */
class TrainingSequenceBuilder(
    private val featureExtractor: PulseFeatureExtractor = PulseFeatureExtractor()
) {

    /**
     * Builds every valid WINDOW_SIZE-length sequence from one session's chronological
     * observations. Excludes INVALID and SYNTHETIC rows entirely (Part 12) and refuses to
     * build a window that straddles a session boundary (rows must share [sessionId] since
     * the DAO already scopes the query to one session, but this is defended anyway in case
     * of a bug upstream that mixed sessions in the caller's argument).
     *
     * @param observations one session's rows, any order.
     * @param resolvedLabels output of LabelResolver.resolveSession for the SAME observations.
     */
    fun buildSequences(
        observations: List<TelemetryObservationEntity>,
        resolvedLabels: Map<Long, ResolvedLabels>
    ): List<TrainingSequence> {
        val usable = observations
            .filter { it.observationQuality != ObservationQuality.INVALID.name && !it.isSynthetic }
            .sortedBy { it.timestamp }

        val windowSize = PulsePredictorConfig.WINDOW_SIZE
        if (usable.size < windowSize) return emptyList()

        val sequences = mutableListOf<TrainingSequence>()
        for (endIndex in windowSize - 1 until usable.size) {
            val windowRows = usable.subList(endIndex - windowSize + 1, endIndex + 1)
            if (windowRows.map { it.sessionId }.distinct().size > 1) continue

            val rawObservations = windowRows.map { it.toRawTelemetryObservation() }
            val tensor = featureExtractor.extractTensor(rawObservations) ?: continue

            val defining = windowRows.last()
            val labels = resolvedLabels[defining.id] ?: continue

            sequences += TrainingSequence(
                sessionId = defining.sessionId,
                sequenceStart = windowRows.first().timestamp,
                sequenceEnd = defining.timestamp,
                featureMatrix = tensor[0].map { it.toList() },
                degradation15s = labels.degradation15s,
                dropout30s = labels.dropout30s,
                likelyCause = labels.likelyCause,
                observationQualities = windowRows.map { ObservationQuality.valueOf(it.observationQuality) },
                featureSchemaVersion = PulsePredictorConfig.FEATURE_NORMALIZATION_VERSION,
                labelSchemaVersion = labels.schemaVersion
            )
        }
        return sequences
    }
}

/** Reconstructs the same shape PulseFeatureExtractor expects from a persisted Room row. */
internal fun TelemetryObservationEntity.toRawTelemetryObservation(): RawTelemetryObservation =
    RawTelemetryObservation(
        timestamp = timestamp,
        sessionId = sessionId,
        transport = runCatching { NetworkTransport.valueOf(transport) }.getOrDefault(NetworkTransport.NONE),
        dnsLatencyMs = dnsLatencyMs,
        tcpRttMs = tcpRttMs,
        tcpJitterMs = tcpJitterMs,
        httpTtfbMs = httpTtfbMs,
        packetLossPct = packetLossPct,
        rsrpDbm = rsrpDbm,
        sinrDb = sinrDb,
        wifiRssiDbm = wifiRssiDbm,
        consecutiveProbeFailures = consecutiveProbeFailures,
        usabilityScore = usabilityScore,
        isValidated = isValidated,
        isZombie = isZombie
    )
