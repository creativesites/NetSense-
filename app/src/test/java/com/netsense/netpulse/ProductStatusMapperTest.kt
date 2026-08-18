package com.netsense.netpulse

import com.netsense.netpulse.model.CellularRfSnapshot
import com.netsense.netpulse.model.NetworkClassification
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.model.ProductStatus
import com.netsense.netpulse.model.ProductStatusMapper
import com.netsense.netpulse.model.UsabilityRating
import com.netsense.netpulse.model.UsabilityScoreResult
import com.netsense.netpulse.model.WifiBand
import com.netsense.netpulse.model.WifiRadarSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductStatusMapperTest {

    private fun score(
        rating: UsabilityRating = UsabilityRating.OPTIMAL,
        isZombie: Boolean = false
    ) = UsabilityScoreResult(
        score = 90,
        rating = rating,
        primaryDiagnosis = "test",
        rootCauseSummary = "test",
        explanatoryReasons = emptyList(),
        isZombieConnection = isZombie,
        scoreBreakdown = emptyMap()
    )

    private fun snapshot(
        connected: Boolean = true,
        transport: NetworkTransport = NetworkTransport.WIFI,
        captivePortal: Boolean = false
    ) = NetworkSnapshot(
        isConnected = connected,
        primaryTransport = transport,
        activeTransports = setOf(transport),
        isCaptivePortal = captivePortal
    )

    @Test
    fun `disconnected snapshot maps to UNAVAILABLE, not a fabricated status`() {
        val status = ProductStatusMapper.map(
            snapshot = snapshot(connected = false),
            scoreResult = score(),
            classification = NetworkClassification.NO_NETWORK,
            wifiRadar = WifiRadarSnapshot(),
            cellularRf = CellularRfSnapshot(),
            isProbing = false,
            isRecoveryPending = false
        )
        assertEquals(ProductStatus.UNAVAILABLE, status)
    }

    @Test
    fun `zombie connection maps to NO_INTERNET even though radio is connected`() {
        val status = ProductStatusMapper.map(
            snapshot = snapshot(),
            scoreResult = score(isZombie = true),
            classification = NetworkClassification.RADIO_ONLY_NO_INTERNET,
            wifiRadar = WifiRadarSnapshot(),
            cellularRf = CellularRfSnapshot(),
            isProbing = false,
            isRecoveryPending = false
        )
        assertEquals(ProductStatus.NO_INTERNET, status)
    }

    @Test
    fun `captive portal takes priority over a plain degraded rating`() {
        val status = ProductStatusMapper.map(
            snapshot = snapshot(captivePortal = true),
            scoreResult = score(rating = UsabilityRating.DEGRADED),
            classification = NetworkClassification.INTERNET_UNVALIDATED,
            wifiRadar = WifiRadarSnapshot(),
            cellularRf = CellularRfSnapshot(),
            isProbing = false,
            isRecoveryPending = false
        )
        assertEquals(ProductStatus.CAPTIVE_PORTAL, status)
    }

    @Test
    fun `weak measured wifi RSSI maps to WEAK_SIGNAL`() {
        val status = ProductStatusMapper.map(
            snapshot = snapshot(),
            scoreResult = score(),
            classification = NetworkClassification.INTERNET_VALIDATED,
            wifiRadar = WifiRadarSnapshot(isWifiConnected = true, isRssiMeasured = true, rssiDbm = -90),
            cellularRf = CellularRfSnapshot(),
            isProbing = false,
            isRecoveryPending = false
        )
        assertEquals(ProductStatus.WEAK_SIGNAL, status)
    }

    @Test
    fun `unmeasured wifi RSSI never triggers WEAK_SIGNAL from a placeholder value`() {
        // isRssiMeasured=false with the default rssiDbm=-100 placeholder must NOT be treated
        // as a real weak-signal reading - this is exactly the "never fabricate" invariant.
        val status = ProductStatusMapper.map(
            snapshot = snapshot(),
            scoreResult = score(),
            classification = NetworkClassification.INTERNET_VALIDATED,
            wifiRadar = WifiRadarSnapshot(isWifiConnected = true, isRssiMeasured = false, rssiDbm = -100),
            cellularRf = CellularRfSnapshot(),
            isProbing = false,
            isRecoveryPending = false
        )
        assertEquals(ProductStatus.ONLINE, status)
    }

    @Test
    fun `recovery pending maps to RECOVERING even when score already looks fine`() {
        val status = ProductStatusMapper.map(
            snapshot = snapshot(),
            scoreResult = score(),
            classification = NetworkClassification.INTERNET_VALIDATED,
            wifiRadar = WifiRadarSnapshot(),
            cellularRf = CellularRfSnapshot(),
            isProbing = false,
            isRecoveryPending = true
        )
        assertEquals(ProductStatus.RECOVERING, status)
    }

    @Test
    fun `justRecovered takes priority and reports RECOVERED`() {
        val status = ProductStatusMapper.map(
            snapshot = snapshot(),
            scoreResult = score(),
            classification = NetworkClassification.INTERNET_VALIDATED,
            wifiRadar = WifiRadarSnapshot(),
            cellularRf = CellularRfSnapshot(),
            isProbing = false,
            isRecoveryPending = false,
            justRecovered = true
        )
        assertEquals(ProductStatus.RECOVERED, status)
    }

    @Test
    fun `healthy optimal validated connection maps to ONLINE`() {
        val status = ProductStatusMapper.map(
            snapshot = snapshot(),
            scoreResult = score(rating = UsabilityRating.OPTIMAL),
            classification = NetworkClassification.INTERNET_OPTIMAL,
            wifiRadar = WifiRadarSnapshot(isWifiConnected = true, isRssiMeasured = true, rssiDbm = -55),
            cellularRf = CellularRfSnapshot(),
            isProbing = false,
            isRecoveryPending = false
        )
        assertEquals(ProductStatus.ONLINE, status)
    }
}
