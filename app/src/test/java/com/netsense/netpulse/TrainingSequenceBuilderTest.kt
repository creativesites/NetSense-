package com.netsense.netpulse

import com.netsense.netpulse.data.TelemetryObservationEntity
import com.netsense.netpulse.dataset.LabelResolver
import com.netsense.netpulse.dataset.TrainingSequenceBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingSequenceBuilderTest {

    private fun obs(
        id: Long,
        timestampMs: Long,
        sessionId: String = "session-1",
        isSynthetic: Boolean = false,
        quality: String = "VALID"
    ) = TelemetryObservationEntity(
        id = id,
        timestamp = timestampMs,
        sessionId = sessionId,
        transport = "WIFI",
        dnsLatencyMs = 40L,
        dnsSuccess = true,
        tcpRttMs = 60L,
        tcpSuccess = true,
        httpTtfbMs = 120L,
        httpSuccess = true,
        packetLossPct = 0f,
        consecutiveProbeFailures = 0,
        usabilityScore = 90,
        isValidated = true,
        isZombie = false,
        primaryDiagnosis = "test",
        observationQuality = quality,
        isSynthetic = isSynthetic
    )

    private fun window(sessionId: String = "session-1", isSynthetic: Boolean = false, count: Int = 15) =
        (0 until count).map { obs(it.toLong() + 1, it * 1_000L, sessionId, isSynthetic) }

    // 8. Synthetic observations must never enter a built training sequence.
    @Test
    fun `synthetic observations are excluded from training sequences`() {
        val rows = window(isSynthetic = true)
        val labels = LabelResolver.resolveSession(rows, nowMs = 200_000L)

        val sequences = TrainingSequenceBuilder().buildSequences(rows, labels)

        assertTrue("a window built purely from synthetic rows must never produce a sequence", sequences.isEmpty())
    }

    @Test
    fun `invalid observations are excluded from training sequences`() {
        val validRows = window(count = 14)
        val invalidRow = obs(15, 14_000L, quality = "INVALID")
        val rows = validRows + invalidRow
        val labels = LabelResolver.resolveSession(rows, nowMs = 200_000L)

        // 15 rows exist, but one is INVALID and must be dropped, leaving only 14 usable rows -
        // not enough for one full WINDOW_SIZE=15 sequence.
        val sequences = TrainingSequenceBuilder().buildSequences(rows, labels)

        assertTrue(sequences.isEmpty())
    }

    @Test
    fun `a full valid production window produces exactly one sequence with matching schema versions`() {
        val rows = window()
        val labels = LabelResolver.resolveSession(rows, nowMs = 200_000L)

        val sequences = TrainingSequenceBuilder().buildSequences(rows, labels)

        assertEquals(1, sequences.size)
        val sequence = sequences.first()
        assertEquals("session-1", sequence.sessionId)
        assertEquals(
            com.netsense.netpulse.ai.predictor.PulsePredictorConfig.WINDOW_SIZE,
            sequence.featureMatrix.size
        )
        assertEquals(
            com.netsense.netpulse.ai.predictor.PulsePredictorConfig.FEATURE_COUNT,
            sequence.featureMatrix.first().size
        )
        assertEquals(
            com.netsense.netpulse.dataset.LabelSemantics.LABEL_SCHEMA_VERSION,
            sequence.labelSchemaVersion
        )
    }

    @Test
    fun `a window mixing two sessions never produces a sequence`() {
        val rows = window("session-A", count = 8) + window("session-B", count = 7).map {
            it.copy(id = it.id + 100, timestamp = it.timestamp + 8_000L)
        }
        val labels = LabelResolver.resolveSession(rows, nowMs = 200_000L)

        val sequences = TrainingSequenceBuilder().buildSequences(rows, labels)

        assertTrue("a 15-row window spanning two sessionIds must be rejected, not silently stitched together", sequences.isEmpty())
    }
}
