package com.netsense.netpulse

import com.netsense.netpulse.dataset.NetworkSessionManager
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.telephony.TelephonySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NetworkSessionManagerTest {

    private fun snapshot(transport: NetworkTransport, connected: Boolean = true, iface: String? = "wlan0") =
        NetworkSnapshot(isConnected = connected, isValidated = connected, primaryTransport = transport, interfaceName = iface)

    private fun telephony(carrier: String? = null, networkType: String = "Unknown") =
        TelephonySnapshot(carrierName = carrier, networkType = networkType)

    // 7. Transport changes between observations -> a new session boundary.
    @Test
    fun `transport change starts a new session`() {
        val manager = NetworkSessionManager()
        val id1 = manager.resolveSessionId(snapshot(NetworkTransport.CELLULAR), telephony("Carrier A"), timestamp = 0)
        val id2 = manager.resolveSessionId(snapshot(NetworkTransport.CELLULAR), telephony("Carrier A"), timestamp = 1_000)
        val id3 = manager.resolveSessionId(snapshot(NetworkTransport.WIFI), telephony("Carrier A"), wifiBssid = "aa:bb", timestamp = 2_000)

        assertEquals("same transport, same identity -> same session", id1, id2)
        assertNotEquals("transport changed -> new session", id2, id3)
    }

    @Test
    fun `wifi bssid change starts a new session while staying on wifi`() {
        val manager = NetworkSessionManager()
        val id1 = manager.resolveSessionId(snapshot(NetworkTransport.WIFI), telephony(), wifiBssid = "aa:aa", timestamp = 0)
        val id2 = manager.resolveSessionId(snapshot(NetworkTransport.WIFI), telephony(), wifiBssid = "aa:aa", timestamp = 1_000)
        val id3 = manager.resolveSessionId(snapshot(NetworkTransport.WIFI), telephony(), wifiBssid = "bb:bb", timestamp = 2_000)

        assertEquals(id1, id2)
        assertNotEquals(id2, id3)
    }

    @Test
    fun `cellular carrier or RAT change starts a new session`() {
        val manager = NetworkSessionManager()
        val id1 = manager.resolveSessionId(snapshot(NetworkTransport.CELLULAR), telephony("Carrier A", "4G LTE"), timestamp = 0)
        val id2 = manager.resolveSessionId(snapshot(NetworkTransport.CELLULAR), telephony("Carrier A", "5G NR"), timestamp = 1_000)

        assertNotEquals("LTE -> 5G is a meaningful identity change", id1, id2)
    }

    @Test
    fun `reconnecting after a disconnect starts a new session`() {
        val manager = NetworkSessionManager()
        val id1 = manager.resolveSessionId(snapshot(NetworkTransport.WIFI), telephony(), timestamp = 0)
        val idDisconnected = manager.resolveSessionId(snapshot(NetworkTransport.NONE, connected = false), telephony(), timestamp = 1_000)
        val idReconnected = manager.resolveSessionId(snapshot(NetworkTransport.WIFI), telephony(), timestamp = 2_000)

        assertNotEquals(id1, idReconnected)
        assertNotEquals(idDisconnected, idReconnected)
    }

    // A large gap (device slept / app backgrounded) must roll a new session even with no other change.
    @Test
    fun `a large temporal gap starts a new session`() {
        val manager = NetworkSessionManager()
        val id1 = manager.resolveSessionId(snapshot(NetworkTransport.WIFI), telephony(), timestamp = 0)
        val id2 = manager.resolveSessionId(
            snapshot(NetworkTransport.WIFI),
            telephony(),
            timestamp = NetworkSessionManager.SESSION_GAP_THRESHOLD_MS + 1_000
        )

        assertNotEquals(id1, id2)
    }

    @Test
    fun `small gaps and unchanged context keep the same session`() {
        val manager = NetworkSessionManager()
        val id1 = manager.resolveSessionId(snapshot(NetworkTransport.WIFI), telephony("Carrier A"), wifiBssid = "aa:aa", timestamp = 0)
        val id2 = manager.resolveSessionId(
            snapshot(NetworkTransport.WIFI),
            telephony("Carrier A"),
            wifiBssid = "aa:aa",
            timestamp = NetworkSessionManager.SESSION_GAP_THRESHOLD_MS - 1_000
        )

        assertEquals(id1, id2)
    }
}
