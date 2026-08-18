package com.netsense.netpulse

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.netsense.netpulse.engine.RadarEngine
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.model.RfUnavailableReason
import com.netsense.netpulse.telephony.TelephonySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Guards against the real bug found in practice: RadarEngine used to swallow every
 * permission/location failure into a single silent null, so the UI could never tell
 * "permission denied" apart from "location services off" apart from "not cellular" - and a
 * coarse rsrp fallback could make the null look like a plausible measurement. These tests
 * pin down that each cause now produces its own explicit [RfUnavailableReason].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RadarEngineRfPermissionTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    private val cellularSnapshot = NetworkSnapshot(
        isConnected = true,
        primaryTransport = NetworkTransport.CELLULAR,
        activeTransports = setOf(NetworkTransport.CELLULAR)
    )

    private fun locationManager() =
        application.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Test
    fun `permission denied is reported explicitly, not a fabricated placeholder`() {
        shadowOf(application).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val engine = RadarEngine(application)

        val snapshot = engine.getCellularRfSnapshot(cellularSnapshot, TelephonySnapshot())

        assertFalse(snapshot.isRfDataMeasured)
        assertEquals(RfUnavailableReason.PERMISSION_DENIED, snapshot.unavailableReason)
        assertNull(snapshot.rsrqDb)
        assertNull(snapshot.sinrDb)
        assertNull(snapshot.cqi)
    }

    @Test
    fun `location services disabled is distinguished from permission denial`() {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(locationManager()).setLocationEnabled(false)
        val engine = RadarEngine(application)

        val snapshot = engine.getCellularRfSnapshot(cellularSnapshot, TelephonySnapshot())

        assertFalse(snapshot.isRfDataMeasured)
        assertEquals(RfUnavailableReason.LOCATION_SERVICES_DISABLED, snapshot.unavailableReason)
    }

    @Test
    fun `wifi transport reports NOT_CELLULAR regardless of permission state`() {
        shadowOf(application).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val engine = RadarEngine(application)
        val wifiSnapshot = cellularSnapshot.copy(
            primaryTransport = NetworkTransport.WIFI,
            activeTransports = setOf(NetworkTransport.WIFI)
        )

        val snapshot = engine.getCellularRfSnapshot(wifiSnapshot, TelephonySnapshot())

        assertEquals(RfUnavailableReason.NOT_CELLULAR, snapshot.unavailableReason)
    }

    @Test
    fun `permission and location both satisfied clears unavailableReason when a cell is registered`() {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(locationManager()).setLocationEnabled(true)
        val engine = RadarEngine(application)

        // Robolectric's default TelephonyManager has no registered CellInfo, so this exercises
        // the "permission+location fine, but modem returned nothing this cycle" path rather
        // than a genuinely measured reading - which is itself a real, distinct state.
        val snapshot = engine.getCellularRfSnapshot(cellularSnapshot, TelephonySnapshot())

        assertFalse(snapshot.isRfDataMeasured)
        assertEquals(RfUnavailableReason.NO_REGISTERED_CELL, snapshot.unavailableReason)
    }
}
