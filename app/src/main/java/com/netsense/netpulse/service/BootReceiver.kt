package com.netsense.netpulse.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.netsense.netpulse.data.NetPulsePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Resumes the background Sentinel service after a device reboot, if and only if the user had
 * previously enabled it (NetPulsePreferences.sentinelEnabled). Without this, a reboot silently
 * stops monitoring - the app's most valuable feature per its own stated priority - with no way
 * for the user to know it stopped until they happen to reopen the app. This never starts
 * monitoring for a user who never opted in; it only resumes an existing, explicit choice.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = NetPulsePreferences(appContext).settingsFlow.first()
                if (settings.sentinelEnabled) {
                    NetPulseSentinelService.startService(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
