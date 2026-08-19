package com.netsense.netpulse

import com.netsense.netpulse.model.CellularRfSnapshot
import com.netsense.netpulse.model.ConnectionPresentationMapper
import com.netsense.netpulse.model.NetworkClassification
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.model.ProductStatusMapper
import com.netsense.netpulse.model.UsabilityRating
import com.netsense.netpulse.model.UsabilityScoreResult
import com.netsense.netpulse.model.WifiRadarSnapshot
import com.netsense.netpulse.state.AuthoritativeNetworkState
import com.netsense.netpulse.state.NetworkStateStore
import com.netsense.netpulse.state.StateSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for the Trust pass' Section 1 fix: Home and the Sentinel notification
 * must never independently compute and display two different scores/statuses for "right now".
 * These tests exercise [NetworkStateStore] directly rather than the full ViewModel/Service, but
 * they build each candidate reading exactly the way NetPulseViewModel and
 * NetPulseSentinelService now do - via the same [ProductStatusMapper] / deterministic
 * UsabilityEngine-shaped inputs - so a regression in the sharing mechanism itself (not just the
 * pure mapping functions, which [ProductStatusMapperTest] already covers) would be caught here.
 */
class NetworkStateStoreTest {

    @Before
    fun resetStore() {
        NetworkStateStore.resetForTest()
    }

    @After
    fun tearDown() {
        NetworkStateStore.resetForTest()
    }

    private fun snapshot() = NetworkSnapshot(
        isConnected = true,
        isValidated = true,
        primaryTransport = NetworkTransport.CELLULAR,
        activeTransports = setOf(NetworkTransport.CELLULAR),
        carrierName = "Test Carrier",
        cellularDataNetworkType = "4G LTE",
        signalLevel = 4
    )

    private fun score(value: Int, rating: UsabilityRating) = UsabilityScoreResult(
        score = value,
        rating = rating,
        primaryDiagnosis = "test diagnosis $value",
        rootCauseSummary = "test",
        explanatoryReasons = emptyList(),
        isZombieConnection = false,
        scoreBreakdown = emptyMap()
    )

    private fun readingFor(
        value: Int,
        rating: UsabilityRating,
        timestampMs: Long,
        source: StateSource
    ): AuthoritativeNetworkState {
        val snap = snapshot()
        val scoreResult = score(value, rating)
        val classification = NetworkClassification.INTERNET_VALIDATED
        val status = ProductStatusMapper.map(
            snapshot = snap,
            scoreResult = scoreResult,
            classification = classification,
            wifiRadar = WifiRadarSnapshot(),
            cellularRf = CellularRfSnapshot(),
            isProbing = false,
            isRecoveryPending = false
        )
        return AuthoritativeNetworkState(
            scoreResult = scoreResult,
            classification = classification,
            status = status,
            snapshot = snap,
            presentation = ConnectionPresentationMapper.map(status, snap, scoreResult),
            isRecoveryPending = false,
            timestampMs = timestampMs,
            source = source
        )
    }

    @Test
    fun `store starts empty until either surface publishes a real reading`() {
        assertEquals(null, NetworkStateStore.state.value)
    }

    @Test
    fun `a fresh Sentinel reading is adopted as the shared authoritative state`() {
        val reading = readingFor(74, UsabilityRating.GOOD, timestampMs = 1_000L, source = StateSource.SENTINEL_PROBE)
        NetworkStateStore.publish(reading)

        assertEquals(74, NetworkStateStore.state.value?.scoreResult?.score)
    }

    @Test
    fun `Home and Sentinel reading the store after either publishes see the identical score`() {
        // Home computes its live tick first...
        val homeReading = readingFor(82, UsabilityRating.GOOD, timestampMs = 1_000L, source = StateSource.HOME_LIVE)
        NetworkStateStore.publish(homeReading)

        // ...whatever "Home's UI" reads and whatever "Sentinel's notification" reads at this
        // instant must be the exact same score - never 82 on one surface and something else on
        // the other, which is the exact bug this store exists to prevent.
        val homeSideScore = NetworkStateStore.state.value?.scoreResult?.score
        val sentinelSideScore = NetworkStateStore.state.value?.scoreResult?.score
        assertEquals(homeSideScore, sentinelSideScore)
        assertEquals(82, homeSideScore)
    }

    @Test
    fun `a newer Sentinel probe replaces an older Home reading - the freshest measurement always wins`() {
        val staleHome = readingFor(82, UsabilityRating.GOOD, timestampMs = 1_000L, source = StateSource.HOME_LIVE)
        val freshSentinel = readingFor(74, UsabilityRating.GOOD, timestampMs = 46_000L, source = StateSource.SENTINEL_PROBE)

        NetworkStateStore.publish(staleHome)
        NetworkStateStore.publish(freshSentinel)

        // The whole point: after Sentinel's newer background probe, nobody - Home included -
        // should still be shown the older 82. There is exactly one current answer.
        assertEquals(74, NetworkStateStore.state.value?.scoreResult?.score)
        assertEquals(46_000L, NetworkStateStore.state.value?.timestampMs)
    }

    @Test
    fun `an out-of-order stale publish can never clobber a reading that is already fresher`() {
        val freshSentinel = readingFor(74, UsabilityRating.GOOD, timestampMs = 46_000L, source = StateSource.SENTINEL_PROBE)
        val lateArrivingButOlderHomeTick = readingFor(82, UsabilityRating.GOOD, timestampMs = 20_000L, source = StateSource.HOME_LIVE)

        NetworkStateStore.publish(freshSentinel)
        NetworkStateStore.publish(lateArrivingButOlderHomeTick)

        // A slow Home tick that started before Sentinel's probe but happens to complete after
        // it must not overwrite the genuinely fresher Sentinel reading with stale data.
        assertEquals(74, NetworkStateStore.state.value?.scoreResult?.score)
    }

    @Test
    fun `resetForTest clears the store back to empty`() {
        NetworkStateStore.publish(readingFor(90, UsabilityRating.OPTIMAL, timestampMs = 1_000L, source = StateSource.HOME_LIVE))
        assertNotNull(NetworkStateStore.state.value)

        NetworkStateStore.resetForTest()

        assertEquals(null, NetworkStateStore.state.value)
    }
}
