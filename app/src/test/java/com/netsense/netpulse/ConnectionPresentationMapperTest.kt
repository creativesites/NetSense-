package com.netsense.netpulse

import com.netsense.netpulse.model.ConnectionPresentationMapper
import com.netsense.netpulse.model.ConnectionVisual
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.model.ProductStatus
import com.netsense.netpulse.model.UsabilityRating
import com.netsense.netpulse.model.UsabilityScoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins down the copy both the Home screen and the Sentinel notification read from - the two
 * surfaces must never disagree, so this is tested once here rather than duplicated per-screen.
 */
class ConnectionPresentationMapperTest {

    private val score = UsabilityScoreResult(
        score = 80,
        rating = UsabilityRating.GOOD,
        primaryDiagnosis = "test",
        rootCauseSummary = "test",
        explanatoryReasons = emptyList(),
        isZombieConnection = false,
        scoreBreakdown = emptyMap()
    )

    private val snapshot = NetworkSnapshot(
        isConnected = true,
        primaryTransport = NetworkTransport.CELLULAR,
        activeTransports = setOf(NetworkTransport.CELLULAR),
        carrierName = "Airtel Zambia",
        cellularDataNetworkType = "4G LTE",
        signalLevel = 4
    )

    @Test
    fun `every ProductStatus maps to a non-blank presentation`() {
        for (status in ProductStatus.values()) {
            val presentation = ConnectionPresentationMapper.map(status, snapshot, score)
            assertTrue("headline blank for $status", presentation.headline.isNotBlank())
            assertTrue("supportingText blank for $status", presentation.supportingText.isNotBlank())
            assertTrue("statusLine blank for $status", presentation.statusLine.isNotBlank())
        }
    }

    @Test
    fun `ONLINE never shows a fix action`() {
        val presentation = ConnectionPresentationMapper.map(ProductStatus.ONLINE, snapshot, score)
        assertFalse(presentation.showFixAction)
        assertEquals(ConnectionVisual.CHECK, presentation.visual)
    }

    @Test
    fun `NO_INTERNET shows a fix action and an alert visual`() {
        val presentation = ConnectionPresentationMapper.map(ProductStatus.NO_INTERNET, snapshot, score)
        assertTrue(presentation.showFixAction)
        assertEquals("Fix It", presentation.fixActionLabel)
        assertEquals(ConnectionVisual.ALERT, presentation.visual)
    }

    @Test
    fun `NO_INTERNET mentions strong signal only when signal is actually strong`() {
        val strongSignal = ConnectionPresentationMapper.map(ProductStatus.NO_INTERNET, snapshot, score)
        assertTrue(strongSignal.supportingText.contains("strong signal"))

        val weakSignalSnapshot = snapshot.copy(signalLevel = 1)
        val weakSignal = ConnectionPresentationMapper.map(ProductStatus.NO_INTERNET, weakSignalSnapshot, score)
        assertFalse(weakSignal.supportingText.contains("strong signal"))
    }

    @Test
    fun `CAPTIVE_PORTAL uses Sign In as its action label, not Fix It`() {
        val presentation = ConnectionPresentationMapper.map(ProductStatus.CAPTIVE_PORTAL, snapshot, score)
        assertTrue(presentation.showFixAction)
        assertEquals("Sign In", presentation.fixActionLabel)
    }

    @Test
    fun `RECOVERING and RECOVERED never show a fix action - healing is already in progress or done`() {
        val recovering = ConnectionPresentationMapper.map(ProductStatus.RECOVERING, snapshot, score)
        val recovered = ConnectionPresentationMapper.map(ProductStatus.RECOVERED, snapshot, score)
        assertFalse(recovering.showFixAction)
        assertFalse(recovered.showFixAction)
        assertEquals(ConnectionVisual.RECOVERING, recovering.visual)
        assertEquals(ConnectionVisual.RECOVERED, recovered.visual)
    }

    @Test
    fun `notification statusLine never contains raw technical metrics`() {
        for (status in ProductStatus.values()) {
            val presentation = ConnectionPresentationMapper.map(status, snapshot, score)
            val line = presentation.statusLine.lowercase()
            assertFalse("statusLine leaked a metric for $status: ${presentation.statusLine}", line.contains("dbm"))
            assertFalse("statusLine leaked a metric for $status: ${presentation.statusLine}", line.contains("ms"))
            assertFalse("statusLine leaked a metric for $status: ${presentation.statusLine}", line.contains("rsrp"))
        }
    }
}
