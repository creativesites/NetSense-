package com.netsense.netpulse

import com.netsense.netpulse.model.NoConnectivityReason
import com.netsense.netpulse.model.determineNoConnectivityReason
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins down the priority order NetPulse uses to explain *why* a device has no network
 * transport at all - airplane mode beats every other toggle since it explains both radios
 * being off at once, and a single radio being off is reported over a generic "no signal"
 * message since it's the one thing the user can immediately act on.
 */
class NoConnectivityReasonTest {

    @Test
    fun `airplane mode takes priority even if radios also read as off`() {
        val reason = determineNoConnectivityReason(
            isAirplaneModeOn = true,
            isWifiEnabled = false,
            isCellularDataEnabled = false
        )
        assertEquals(NoConnectivityReason.AIRPLANE_MODE, reason)
    }

    @Test
    fun `both radios off without airplane mode reports WIFI_AND_DATA_OFF`() {
        val reason = determineNoConnectivityReason(
            isAirplaneModeOn = false,
            isWifiEnabled = false,
            isCellularDataEnabled = false
        )
        assertEquals(NoConnectivityReason.WIFI_AND_DATA_OFF, reason)
    }

    @Test
    fun `wifi off with data on reports WIFI_OFF`() {
        val reason = determineNoConnectivityReason(
            isAirplaneModeOn = false,
            isWifiEnabled = false,
            isCellularDataEnabled = true
        )
        assertEquals(NoConnectivityReason.WIFI_OFF, reason)
    }

    @Test
    fun `data off with wifi on reports CELLULAR_DATA_OFF`() {
        val reason = determineNoConnectivityReason(
            isAirplaneModeOn = false,
            isWifiEnabled = true,
            isCellularDataEnabled = false
        )
        assertEquals(NoConnectivityReason.CELLULAR_DATA_OFF, reason)
    }

    @Test
    fun `both radios enabled but still disconnected reports NO_SIGNAL, never fabricating a toggle cause`() {
        val reason = determineNoConnectivityReason(
            isAirplaneModeOn = false,
            isWifiEnabled = true,
            isCellularDataEnabled = true
        )
        assertEquals(NoConnectivityReason.NO_SIGNAL, reason)
    }
}
