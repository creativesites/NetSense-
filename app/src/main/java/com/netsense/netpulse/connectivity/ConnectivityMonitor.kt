package com.netsense.netpulse.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import com.netsense.netpulse.model.NetworkClassification
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.model.NoConnectivityReason
import com.netsense.netpulse.model.determineNoConnectivityReason
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ConnectivityMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun isAirplaneModeOn(): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    } catch (e: Exception) {
        false
    }

    private fun isWifiEnabled(): Boolean = try {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiManager?.isWifiEnabled ?: true
    } catch (e: Exception) {
        true
    }

    /**
     * Whether the user has mobile data switched on. TelephonyManager.isDataEnabled() is only
     * public from API 28 - below that there's no reliable, permission-free way to read this
     * toggle, so this defaults to "enabled" on API 26/27 rather than guessing it's off.
     */
    private fun isCellularDataEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        return try {
            val telephonyManager = context.applicationContext
                .getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            telephonyManager?.isDataEnabled ?: true
        } catch (e: Exception) {
            true
        }
    }

    fun observeNetwork(): Flow<NetworkSnapshot> = callbackFlow {
        var currentNetwork: Network? = connectivityManager.activeNetwork
        var currentCapabilities: NetworkCapabilities? =
            currentNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        var currentLinkProperties: LinkProperties? =
            currentNetwork?.let { connectivityManager.getLinkProperties(it) }

        fun emitCurrentState() {
            val net = currentNetwork
            val caps = currentCapabilities
            val link = currentLinkProperties

            if (net == null || caps == null) {
                val reason = determineNoConnectivityReason(
                    isAirplaneModeOn = isAirplaneModeOn(),
                    isWifiEnabled = isWifiEnabled(),
                    isCellularDataEnabled = isCellularDataEnabled()
                )
                trySend(
                    NetworkSnapshot(
                        isConnected = false,
                        isValidated = false,
                        primaryTransport = NetworkTransport.NONE,
                        activeTransports = emptySet(),
                        isAirplaneModeOn = reason == NoConnectivityReason.AIRPLANE_MODE,
                        classification = NetworkClassification.NO_NETWORK,
                        noConnectivityReason = reason
                    )
                )
                return
            }

            val transports = mutableSetOf<NetworkTransport>()
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                transports.add(NetworkTransport.CELLULAR)
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                transports.add(NetworkTransport.WIFI)
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                transports.add(NetworkTransport.ETHERNET)
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                transports.add(NetworkTransport.BLUETOOTH)
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                transports.add(NetworkTransport.VPN)
            }

            val primaryTransport = when {
                transports.contains(NetworkTransport.WIFI) -> NetworkTransport.WIFI
                transports.contains(NetworkTransport.CELLULAR) -> NetworkTransport.CELLULAR
                transports.contains(NetworkTransport.ETHERNET) -> NetworkTransport.ETHERNET
                transports.contains(NetworkTransport.VPN) -> NetworkTransport.VPN
                transports.isNotEmpty() -> transports.first()
                else -> NetworkTransport.OTHER
            }

            val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

            val dnsList = link?.dnsServers?.map { it.hostAddress ?: it.toString() } ?: emptyList()

            val classification = when {
                !hasInternet -> NetworkClassification.RADIO_ONLY_NO_INTERNET
                isValidated -> NetworkClassification.INTERNET_VALIDATED
                else -> NetworkClassification.INTERNET_UNVALIDATED
            }

            trySend(
                NetworkSnapshot(
                    timestamp = System.currentTimeMillis(),
                    isConnected = true,
                    isValidated = isValidated,
                    isMetered = isMetered,
                    primaryTransport = primaryTransport,
                    activeTransports = transports,
                    downstreamBandwidthKbps = caps.linkDownstreamBandwidthKbps,
                    upstreamBandwidthKbps = caps.linkUpstreamBandwidthKbps,
                    interfaceName = link?.interfaceName,
                    dnsServers = dnsList,
                    domains = link?.domains,
                    classification = classification
                )
            )
        }

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                currentNetwork = network
                currentCapabilities = connectivityManager.getNetworkCapabilities(network)
                currentLinkProperties = connectivityManager.getLinkProperties(network)
                emitCurrentState()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                if (network == currentNetwork || currentNetwork == null) {
                    currentNetwork = network
                    currentCapabilities = networkCapabilities
                    currentLinkProperties = connectivityManager.getLinkProperties(network)
                    emitCurrentState()
                }
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties
            ) {
                if (network == currentNetwork || currentNetwork == null) {
                    currentNetwork = network
                    currentLinkProperties = linkProperties
                    currentCapabilities = connectivityManager.getNetworkCapabilities(network)
                    emitCurrentState()
                }
            }

            override fun onLost(network: Network) {
                if (network == currentNetwork) {
                    currentNetwork = connectivityManager.activeNetwork
                    currentCapabilities = currentNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
                    currentLinkProperties = currentNetwork?.let { connectivityManager.getLinkProperties(it) }
                    emitCurrentState()
                }
            }
        }

        // Trigger initial state
        emitCurrentState()

        val request = NetworkRequest.Builder().build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (ignored: Exception) {}
        }
    }
}
