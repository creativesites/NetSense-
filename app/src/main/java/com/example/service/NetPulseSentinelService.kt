package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.connectivity.ConnectivityMonitor
import com.example.data.DiagnosticLogEntity
import com.example.data.NetPulseDatabase
import com.example.engine.DiagnosticEngine
import com.example.engine.UsabilityEngine
import com.example.model.DiagnosticMode
import com.example.model.NetworkSnapshot
import com.example.model.UsabilityRating
import com.example.telephony.TelephonyObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Phase 3 Foreground Sentinel Service:
 * Continuous background network sentinel providing periodic micro-probes (ultra-low bandwidth),
 * instant zombie connection alerts, and historical event persistence.
 */
class NetPulseSentinelService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var connectivityMonitor: ConnectivityMonitor
    private lateinit var telephonyObserver: TelephonyObserver
    private val diagnosticEngine = DiagnosticEngine()
    private lateinit var database: NetPulseDatabase

    private var lastObservedZombie = false

    companion object {
        const val CHANNEL_ID = "netpulse_sentinel_channel"
        const val ALERT_CHANNEL_ID = "netpulse_zombie_alert_channel"
        const val NOTIFICATION_ID = 1001
        const val ZOMBIE_ALERT_NOTIFICATION_ID = 1002

        const val ACTION_START = "ACTION_START_SENTINEL"
        const val ACTION_STOP = "ACTION_STOP_SENTINEL"

        fun startService(context: Context) {
            val intent = Intent(context, NetPulseSentinelService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, NetPulseSentinelService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityMonitor = ConnectivityMonitor(applicationContext)
        telephonyObserver = TelephonyObserver(applicationContext)
        database = NetPulseDatabase.getDatabase(applicationContext)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildOngoingNotification("NetPulse Sentinel Active", "Monitoring connection stability..."))
                startSentinelLoop()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun startSentinelLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val netSnapshot = connectivityMonitor.observeNetwork().first()
                    val telSnapshot = telephonyObserver.observeTelephony().first()

                    val enrichedSnapshot = netSnapshot.copy(
                        carrierName = telSnapshot.carrierName,
                        cellularDataNetworkType = telSnapshot.networkType,
                        signalLevel = telSnapshot.signalLevel,
                        signalDbm = telSnapshot.signalDbm
                    )

                    // Execute lightweight micro probe (~0.8 KB)
                    val probe = diagnosticEngine.runProbe(DiagnosticMode.MICRO)
                    val scoreResult = UsabilityEngine.calculateScore(enrichedSnapshot, probe)
                    val classification = UsabilityEngine.classify(enrichedSnapshot, scoreResult)

                    // Update persistent notification status
                    val notifTitle = "NetPulse: ${scoreResult.rating.label} (${scoreResult.score}/100)"
                    val notifText = "${enrichedSnapshot.primaryTransport.name} • ${enrichedSnapshot.carrierName ?: "Network"} • Latency: ${probe.averageHttpMs ?: probe.averageDnsMs ?: 0}ms"
                    updateOngoingNotification(notifTitle, notifText)

                    // Persist record into Room Database
                    val log = DiagnosticLogEntity(
                        timestamp = System.currentTimeMillis(),
                        score = scoreResult.score,
                        rating = scoreResult.rating.name,
                        classification = classification.name,
                        isZombieConnection = scoreResult.isZombieConnection,
                        primaryDiagnosis = scoreResult.primaryDiagnosis,
                        rootCauseSummary = scoreResult.rootCauseSummary,
                        transport = enrichedSnapshot.primaryTransport.name,
                        carrierName = enrichedSnapshot.carrierName,
                        cellularNetworkType = enrichedSnapshot.cellularDataNetworkType,
                        signalLevel = enrichedSnapshot.signalLevel,
                        signalDbm = enrichedSnapshot.signalDbm,
                        dnsLatencyMs = probe.averageDnsMs,
                        dnsSuccess = probe.overallDnsSuccess,
                        tcpHandshakeMs = probe.averageTcpMs,
                        tcpSuccess = probe.overallTcpSuccess,
                        tcpJitterMs = probe.tcpJitterMs,
                        httpLatencyMs = probe.averageHttpMs,
                        httpSuccess = probe.overallHttpSuccess,
                        httpStatusCode = probe.primaryEndpoint.httpStatusCode,
                        packetLossPct = probe.packetLossPct,
                        durationMs = probe.durationMs,
                        probeMode = DiagnosticMode.MICRO.name,
                        notes = "Background Sentinel Periodic Micro-Probe"
                    )
                    database.diagnosticLogDao().insertLog(log)

                    // Trigger instant high-priority alert on transition to Zombie state
                    if (scoreResult.isZombieConnection && !lastObservedZombie) {
                        triggerZombieAlert(scoreResult.primaryDiagnosis)
                    }
                    lastObservedZombie = scoreResult.isZombieConnection

                } catch (e: Exception) {
                    // Fail silently to keep sentinel resilient
                }

                // Sentinel interval: 45 seconds interval for low resource/battery impact
                delay(45_000L)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val ongoingChannel = NotificationChannel(
                CHANNEL_ID,
                "NetPulse Sentinel Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time connection usability status"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "NetPulse Zombie Connection Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when cellular link is active but upstream Internet is dead"
                enableVibration(true)
            }

            manager.createNotificationChannel(ongoingChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun buildOngoingNotification(title: String, message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateOngoingNotification(title: String, message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildOngoingNotification(title, message))
    }

    private fun triggerZombieAlert(message: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alert = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("⚠ Zombie Connection Detected!")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ZOMBIE_ALERT_NOTIFICATION_ID, alert)
    }
}
