package com.netsense.netpulse

import com.netsense.netpulse.ai.predictor.PulseFeatureExtractor
import com.netsense.netpulse.ai.predictor.PulseFeatureSchema
import com.netsense.netpulse.ai.predictor.RawTelemetryObservation
import com.netsense.netpulse.model.NetworkTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseFeatureExtractorTest {

    private val extractor = PulseFeatureExtractor()

    // 9. A missing DNS measurement must not be encoded the same as a genuine 0ms reading.
    @Test
    fun `missing dns latency is not encoded as zero milliseconds`() {
        val missing = RawTelemetryObservation(dnsLatencyMs = null)
        val zeroMs = RawTelemetryObservation(dnsLatencyMs = 0L)

        val missingFeature = extractor.extractSingleStepFeatures(missing, prev = null)[0]
        val zeroFeature = extractor.extractSingleStepFeatures(zeroMs, prev = null)[0]

        assertEquals(PulseFeatureSchema.MISSING_VALUE_SENTINEL, missingFeature)
        assertEquals(0.0f, zeroFeature) // a genuine 0ms reading normalizes to the bottom of [0,1]
        assertNotEquals("missing must never collide with a real best-case reading", missingFeature, zeroFeature)
    }

    // 10. A missing RF reading must not be encoded as a "perfect" or "zero" signal.
    @Test
    fun `missing cellular rsrp is not encoded as a perfect or zero signal`() {
        val missing = RawTelemetryObservation(rsrpDbm = null)
        val worstCase = RawTelemetryObservation(rsrpDbm = -140) // the actual bottom of the real range
        val bestCase = RawTelemetryObservation(rsrpDbm = -44) // the actual top of the real range

        val missingFeature = extractor.extractSingleStepFeatures(missing, prev = null)[5]
        val worstFeature = extractor.extractSingleStepFeatures(worstCase, prev = null)[5]
        val bestFeature = extractor.extractSingleStepFeatures(bestCase, prev = null)[5]

        assertEquals(PulseFeatureSchema.MISSING_VALUE_SENTINEL, missingFeature)
        assertTrue("missing sentinel must fall outside the real [0,1] measured range", missingFeature < 0f || missingFeature > 1f)
        assertNotEquals(missingFeature, worstFeature)
        assertNotEquals(missingFeature, bestFeature)
    }

    @Test
    fun `missing wifi rssi is not encoded as a real reading`() {
        val missing = RawTelemetryObservation(transport = NetworkTransport.WIFI, wifiRssiDbm = null)
        val feature = extractor.extractSingleStepFeatures(missing, prev = null)[7]
        assertEquals(PulseFeatureSchema.MISSING_VALUE_SENTINEL, feature)
    }

    @Test
    fun `velocity features are missing not zero when there is no previous observation`() {
        val curr = RawTelemetryObservation(tcpRttMs = 100L, rsrpDbm = -90)
        val latencyVelocity = extractor.extractSingleStepFeatures(curr, prev = null)[8]
        val signalVelocity = extractor.extractSingleStepFeatures(curr, prev = null)[9]

        assertEquals(PulseFeatureSchema.MISSING_VALUE_SENTINEL, latencyVelocity)
        assertEquals(PulseFeatureSchema.MISSING_VALUE_SENTINEL, signalVelocity)
    }

    @Test
    fun `velocity features are computed when a previous observation exists`() {
        val prev = RawTelemetryObservation(timestamp = 0L, tcpRttMs = 100L, rsrpDbm = -90)
        val curr = RawTelemetryObservation(timestamp = 1_000L, tcpRttMs = 200L, rsrpDbm = -80)

        val latencyVelocity = extractor.extractSingleStepFeatures(curr, prev)[8]
        val signalVelocity = extractor.extractSingleStepFeatures(curr, prev)[9]

        assertNotEquals(PulseFeatureSchema.MISSING_VALUE_SENTINEL, latencyVelocity)
        assertNotEquals(PulseFeatureSchema.MISSING_VALUE_SENTINEL, signalVelocity)
        assertTrue("latency increased, so velocity should encode above the centered 0.5", latencyVelocity > 0.5f)
        assertTrue("signal improved, so velocity should encode above the centered 0.5", signalVelocity > 0.5f)
    }

    @Test
    fun `feature schema documents exactly FEATURE_COUNT features in stable order`() {
        assertEquals(
            com.netsense.netpulse.ai.predictor.PulsePredictorConfig.FEATURE_COUNT,
            PulseFeatureSchema.FEATURES.size
        )
        PulseFeatureSchema.FEATURES.forEachIndexed { index, descriptor ->
            assertEquals(index, descriptor.index)
        }
    }
}
