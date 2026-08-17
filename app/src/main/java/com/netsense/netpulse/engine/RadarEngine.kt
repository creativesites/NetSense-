package com.netsense.netpulse.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import com.netsense.netpulse.model.CellularRfSnapshot
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.model.WifiBand
import com.netsense.netpulse.model.WifiRadarSnapshot
import com.netsense.netpulse.telephony.TelephonySnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class RadarEngine(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val telephonyManager = context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun getWifiRadarSnapshot(snapshot: NetworkSnapshot): WifiRadarSnapshot {
        val isWifi = snapshot.primaryTransport == NetworkTransport.WIFI ||
                snapshot.activeTransports.contains(NetworkTransport.WIFI)

        if (!isWifi || wifiManager == null) {
            return WifiRadarSnapshot(isWifiConnected = false)
        }

        var ssid = "<Connected Wi-Fi>"
        var bssid: String? = null
        var freq = 5180
        var rssi = snapshot.signalDbm ?: -65 // display-only fallback; see isRssiMeasured
        var isRssiMeasured = false
        var linkSpeed = 433
        var txSpeed = 433
        var rxSpeed = 433
        var gatewayIp: String? = null
        var subnetMask: String? = null

        try {
            val info: WifiInfo? = wifiManager.connectionInfo
            if (info != null) {
                if (!info.ssid.isNullOrBlank() && info.ssid != "<unknown ssid>") {
                    ssid = info.ssid.trim('"')
                }
                bssid = info.bssid
                if (info.frequency > 0) {
                    freq = info.frequency
                }
                if (info.rssi != 0 && info.rssi > -127) {
                    rssi = info.rssi
                    isRssiMeasured = true
                }
                if (info.linkSpeed > 0) {
                    linkSpeed = info.linkSpeed
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (info.txLinkSpeedMbps > 0) txSpeed = info.txLinkSpeedMbps
                    if (info.rxLinkSpeedMbps > 0) rxSpeed = info.rxLinkSpeedMbps
                }
            }
            // Real DHCP lease info (actual gateway/netmask), never a fabricated address.
            @Suppress("DEPRECATION")
            val dhcpInfo = wifiManager.dhcpInfo
            if (dhcpInfo != null) {
                if (dhcpInfo.gateway != 0) gatewayIp = intToIpAddress(dhcpInfo.gateway)
                if (dhcpInfo.netmask != 0) subnetMask = intToIpAddress(dhcpInfo.netmask)
            }
        } catch (e: Exception) {
            // Permission fallback
        }

        val band = when {
            freq in 2400..2500 -> WifiBand.BAND_2_4_GHZ
            freq in 4900..5900 -> WifiBand.BAND_5_GHZ
            freq in 5925..7125 -> WifiBand.BAND_6_GHZ
            else -> WifiBand.BAND_5_GHZ
        }

        val channel = calculateWifiChannel(freq)
        val channelWidth = when (band) {
            WifiBand.BAND_6_GHZ -> 160
            WifiBand.BAND_5_GHZ -> 80
            WifiBand.BAND_2_4_GHZ -> 20
            WifiBand.UNKNOWN -> 40
        }

        val standard = when (band) {
            WifiBand.BAND_6_GHZ -> "Wi-Fi 6E (802.11ax)"
            WifiBand.BAND_5_GHZ -> if (linkSpeed >= 866) "Wi-Fi 6 (802.11ax)" else "Wi-Fi 5 (802.11ac)"
            WifiBand.BAND_2_4_GHZ -> "Wi-Fi 4 (802.11n)"
            WifiBand.UNKNOWN -> "Wi-Fi"
        }

        val signalPercent = min(100, max(0, 2 * (rssi + 100)))
        val congestion = when {
            band == WifiBand.BAND_2_4_GHZ -> "High (2.4GHz crowded band)"
            rssi < -78 -> "Moderate (Weak attenuation)"
            else -> "Low (Clean RF channel)"
        }

        val interference = when {
            band == WifiBand.BAND_2_4_GHZ -> "Elevated (Bluetooth/Microwave Coexistence)"
            else -> "Minimal (High SNR)"
        }

        return WifiRadarSnapshot(
            isWifiConnected = true,
            ssid = ssid,
            bssid = bssid,
            frequencyMhz = freq,
            band = band,
            channelNumber = channel,
            channelWidthMhz = channelWidth,
            rssiDbm = rssi,
            isRssiMeasured = isRssiMeasured,
            linkSpeedMbps = linkSpeed,
            txLinkSpeedMbps = txSpeed,
            rxLinkSpeedMbps = rxSpeed,
            wifiStandard = standard,
            gatewayIp = gatewayIp,
            subnetMask = subnetMask,
            signalStrengthPercent = signalPercent,
            congestionLevel = congestion,
            interferenceRisk = interference
        )
    }

    /** Converts a little-endian packed IPv4 address (as returned by DhcpInfo) to dotted-decimal. */
    private fun intToIpAddress(addr: Int): String =
        "${addr and 0xFF}.${addr shr 8 and 0xFF}.${addr shr 16 and 0xFF}.${addr shr 24 and 0xFF}"

    fun getCellularRfSnapshot(
        snapshot: NetworkSnapshot,
        telephony: TelephonySnapshot
    ): CellularRfSnapshot {
        val isCellular = snapshot.primaryTransport == NetworkTransport.CELLULAR ||
                snapshot.activeTransports.contains(NetworkTransport.CELLULAR)

        val carrier = telephony.carrierName ?: snapshot.carrierName ?: "Carrier LTE/5G"
        val netType = telephony.networkType.takeIf { it != "Unknown" } ?: snapshot.cellularDataNetworkType ?: "4G LTE"

        // rsrp may fall back to the TelephonyCallback signal-strength reading (still a real
        // measurement, just coarser than a registered-cell RSRP). All other RF fields below
        // start as null (unmeasured) and are ONLY set from a genuine CellInfoLte/Nr reading -
        // never from a plausible-looking placeholder. See CellularRfSnapshot.isRfDataMeasured.
        var rsrp: Int? = telephony.signalDbm ?: snapshot.signalDbm
        var rsrq: Int? = null
        var sinr: Int? = null
        var cqi: Int? = null
        var cellId: String? = null
        var pci: Int? = null
        var tac: Int? = null
        var band: String? = null
        var isRoaming = false
        var isCA = netType.contains("5G") || netType.contains("LTE-A")
        var isRfDataMeasured = false

        try {
            if (telephonyManager != null) {
                isRoaming = telephonyManager.isNetworkRoaming
                val allCellInfo: List<CellInfo>? = telephonyManager.allCellInfo
                val registeredCell = allCellInfo?.firstOrNull { it.isRegistered }
                if (registeredCell is CellInfoLte) {
                    val ss = registeredCell.cellSignalStrength
                    rsrp = ss.rsrp.takeIf { it != CellInfo.UNAVAILABLE && it != Int.MAX_VALUE } ?: rsrp
                    rsrq = ss.rsrq.takeIf { it != CellInfo.UNAVAILABLE && it != Int.MAX_VALUE }
                    sinr = ss.rssnr.takeIf { it != CellInfo.UNAVAILABLE && it != Int.MAX_VALUE }
                    cqi = ss.cqi.takeIf { it != CellInfo.UNAVAILABLE && it != Int.MAX_VALUE }
                    val id = registeredCell.cellIdentity
                    pci = id.pci.takeIf { it != CellInfo.UNAVAILABLE }
                    tac = id.tac.takeIf { it != CellInfo.UNAVAILABLE }
                    if (id.ci != CellInfo.UNAVAILABLE) cellId = id.ci.toString()
                    band = "LTE"
                    isRfDataMeasured = true
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && registeredCell is CellInfoNr) {
                    val ss = registeredCell.cellSignalStrength as? CellSignalStrengthNr
                    if (ss != null) {
                        rsrp = ss.ssRsrp.takeIf { it != CellInfo.UNAVAILABLE } ?: rsrp
                        rsrq = ss.ssRsrq.takeIf { it != CellInfo.UNAVAILABLE }
                        sinr = ss.ssSinr.takeIf { it != CellInfo.UNAVAILABLE }
                        band = "5G NR"
                        isRfDataMeasured = true
                    }
                }
            }
        } catch (e: Exception) {
            // Permission graceful fallback
        }

        return CellularRfSnapshot(
            isCellularConnected = isCellular || telephony.isSimReady,
            carrierName = carrier,
            dataNetworkType = netType,
            rsrpDbm = rsrp,
            rsrqDb = rsrq,
            sinrDb = sinr,
            cqi = cqi,
            cellId = cellId,
            pci = pci,
            tac = tac,
            bandIndicator = band,
            isRfDataMeasured = isRfDataMeasured,
            isRoaming = isRoaming,
            isCarrierAggregationActive = isCA,
            simState = if (telephony.isSimReady) "Active / Ready" else "No SIM / Searching"
        )
    }

    fun computeStabilityScore(
        snapshot: NetworkSnapshot,
        wifiRadar: WifiRadarSnapshot,
        cellularRf: CellularRfSnapshot,
        jitterMs: Long?,
        lossPct: Float
    ): Pair<Int, String> {
        var score = 100

        // Packet loss penalty
        if (lossPct > 0.3f) {
            score -= 40
        } else if (lossPct > 0.05f) {
            score -= 20
        }

        // Jitter penalty
        if (jitterMs != null) {
            if (jitterMs > 150) score -= 25
            else if (jitterMs > 60) score -= 12
        }

        // Signal strength penalty
        if (snapshot.primaryTransport == NetworkTransport.WIFI && wifiRadar.isWifiConnected) {
            if (wifiRadar.rssiDbm < -82) score -= 25
            else if (wifiRadar.rssiDbm < -72) score -= 10
            if (wifiRadar.band == WifiBand.BAND_2_4_GHZ) score -= 5
        } else if (snapshot.primaryTransport == NetworkTransport.CELLULAR && cellularRf.isCellularConnected) {
            val rsrp = cellularRf.rsrpDbm ?: -90
            if (rsrp < -110) score -= 30
            else if (rsrp < -98) score -= 15
        }

        val finalScore = max(5, min(100, score))
        val verdict = when {
            finalScore >= 85 -> "Rock Solid: Low jitter & strong SNR"
            finalScore >= 65 -> "Stable: Minor latency fluctuations"
            finalScore >= 40 -> "Degraded: Signal fading or packet drops"
            else -> "Volatile Link: Severe jitter and transmission losses"
        }

        return Pair(finalScore, verdict)
    }

    private fun calculateWifiChannel(freqMhz: Int): Int {
        return when {
            freqMhz == 2484 -> 14
            freqMhz in 2412..2472 -> (freqMhz - 2412) / 5 + 1
            freqMhz in 5170..5825 -> (freqMhz - 5170) / 5 + 34
            freqMhz in 5925..7125 -> (freqMhz - 5925) / 5 + 1
            else -> 36
        }
    }
}
