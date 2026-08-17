package com.example.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class TelephonySnapshot(
    val carrierName: String? = null,
    val networkType: String = "Unknown",
    val signalLevel: Int = 0, // 0..4
    val signalDbm: Int? = null,
    val isSimReady: Boolean = false
)

class TelephonyObserver(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    fun observeTelephony(): Flow<TelephonySnapshot> = callbackFlow {
        if (telephonyManager == null) {
            trySend(TelephonySnapshot())
            awaitClose { }
            return@callbackFlow
        }

        fun getCarrierName(): String? {
            return telephonyManager.networkOperatorName.takeIf { it.isNotBlank() }
                ?: telephonyManager.simOperatorName.takeIf { it.isNotBlank() }
        }

        fun getDataNetworkTypeString(): String {
            val hasPhonePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            return if (hasPhonePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                when (telephonyManager.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
                    TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                    TelephonyManager.NETWORK_TYPE_HSPAP,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_HSDPA,
                    TelephonyManager.NETWORK_TYPE_HSUPA,
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G HSPA/UMTS"
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_GPRS -> "2G EDGE/GPRS"
                    else -> "Cellular"
                }
            } else {
                "Cellular"
            }
        }

        fun getSimReady(): Boolean {
            return telephonyManager.simState == TelephonyManager.SIM_STATE_READY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    val level = signalStrength.level // 0..4
                    val dbm = signalStrength.cellSignalStrengths.firstOrNull()?.dbm
                    trySend(
                        TelephonySnapshot(
                            carrierName = getCarrierName(),
                            networkType = getDataNetworkTypeString(),
                            signalLevel = level,
                            signalDbm = dbm,
                            isSimReady = getSimReady()
                        )
                    )
                }
            }

            try {
                telephonyManager.registerTelephonyCallback(
                    context.mainExecutor,
                    callback
                )
            } catch (e: Exception) {
                // In case of permission restriction, send fallback
                trySend(
                    TelephonySnapshot(
                        carrierName = getCarrierName(),
                        networkType = getDataNetworkTypeString(),
                        signalLevel = 2,
                        isSimReady = getSimReady()
                    )
                )
            }

            awaitClose {
                try {
                    telephonyManager.unregisterTelephonyCallback(callback)
                } catch (ignored: Exception) {}
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                    val level = signalStrength?.level ?: 0
                    trySend(
                        TelephonySnapshot(
                            carrierName = getCarrierName(),
                            networkType = getDataNetworkTypeString(),
                            signalLevel = level,
                            isSimReady = getSimReady()
                        )
                    )
                }
            }

            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)

            awaitClose {
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
            }
        }
    }
}
