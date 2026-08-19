package com.netsense.netpulse.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.netsense.netpulse.MainActivity
import com.netsense.netpulse.connectivity.ConnectivityMonitor
import com.netsense.netpulse.data.DiagnosticLogEntity
import com.netsense.netpulse.data.NetPulseDatabase
import com.netsense.netpulse.data.NetPulsePreferences
import com.netsense.netpulse.dataset.DataLifecycleManager
import com.netsense.netpulse.engine.DiagnosticEngine
import com.netsense.netpulse.engine.UsabilityEngine
import com.netsense.netpulse.model.CellularRfSnapshot
import com.netsense.netpulse.model.ConnectionPresentationMapper
import com.netsense.netpulse.model.DiagnosticMode
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.ProductStatus
import com.netsense.netpulse.model.ProductStatusMapper
import com.netsense.netpulse.model.UsabilityRating
import com.netsense.netpulse.model.WifiRadarSnapshot
import com.netsense.netpulse.telephony.TelephonyObserver
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
    private lateinit var preferences: NetPulsePreferences
    private lateinit var dataLifecycleManager: DataLifecycleManager

    private var lastObservedZombie = false
    private var lastNotifiedTitle: String? = null
    private var lastNotifiedText: String? = null

    companion object {
        // _v2: a NotificationChannel's importance is frozen by Android the moment it's first
        // created and can only be changed by the user afterwards, never by the app re-creating
        // it - bumping to DEFAULT (see createNotificationChannels) needs a fresh channel id to
        // actually take effect on devices that already have the old LOW-importance channel.
        const val CHANNEL_ID = "netpulse_sentinel_channel_v2"
        const val ALERT_CHANNEL_ID = "netpulse_zombie_alert_channel"
        const val NOTIFICATION_ID = 1001
        const val ZOMBIE_ALERT_NOTIFICATION_ID = 1002

        const val ACTION_START = "ACTION_START_SENTINEL"
        const val ACTION_STOP = "ACTION_STOP_SENTINEL"

        /** Set on the notification's tap intent so MainActivity opens straight to Home -
         *  the one screen that already shows whatever state (healthy/degraded/down/
         *  recovering) prompted the notification, without needing per-state deep links. */
        const val EXTRA_OPEN_HOME = "open_home"

        /** Set on the notification's "Heal Now" action intent. MainActivity reads this and
         *  immediately calls the same viewModel.fixIt() the Home screen's Fix It button uses -
         *  Android gives apps no way to toggle airplane mode / Wi-Fi / open a captive portal
         *  without an Activity in the loop, so this can't act silently in the background; it
         *  saves the user the trip of opening the app and finding the button themselves. */
        const val EXTRA_HEAL_NOW = "heal_now"

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
        preferences = NetPulsePreferences(applicationContext)
        dataLifecycleManager = DataLifecycleManager(
            database.diagnosticLogDao(),
            database.telemetryObservationDao(),
            database.dailyUsabilityAggregateDao()
        )
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
                startForeground(NOTIFICATION_ID, buildOngoingNotification(null, "Checking your connection", "NetPulse just started monitoring"))
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

                    // A recovery attempt may currently be in progress from the foreground app
                    // (Home's Fix It, or a manual Advanced Healer action) - reuse the same
                    // RecoveryOutcomeDao truth the ViewModel does so the notification never
                    // disagrees with what Home is showing.
                    val isRecoveryPending = database.recoveryOutcomeDao().getPending().isNotEmpty()
                    val status = ProductStatusMapper.map(
                        snapshot = enrichedSnapshot,
                        scoreResult = scoreResult,
                        classification = classification,
                        wifiRadar = WifiRadarSnapshot(),
                        cellularRf = CellularRfSnapshot(),
                        isProbing = false,
                        isRecoveryPending = isRecoveryPending
                    )
                    val presentation = ConnectionPresentationMapper.map(status, enrichedSnapshot, scoreResult)

                    // The score number is the whole point of this notification - it's what
                    // lets a user judge "how good" at a glance instead of just "good or bad".
                    // The subtitle carries the same informative diagnosis PulseCore already
                    // computed (unchanged from what used to show here), not a vaguer rewrite.
                    val notifTitle = "${scoreResult.score}/100 · ${scoreResult.rating.label}"
                    val notifText = if (status == ProductStatus.ONLINE) {
                        listOfNotNull(enrichedSnapshot.carrierName, enrichedSnapshot.cellularDataNetworkType)
                            .joinToString(" · ")
                            .ifBlank { "NetPulse is monitoring your connection" }
                    } else if (isRecoveryPending) {
                        "NetPulse is working on it - ${scoreResult.primaryDiagnosis}"
                    } else {
                        scoreResult.primaryDiagnosis
                    }
                    // Reuses the exact same showFixAction/fixActionLabel Home already computed
                    // from this same presentation - the notification's Heal Now button can
                    // never appear in a state where Home wouldn't also show Fix It.
                    val canHealNow = presentation.showFixAction && !isRecoveryPending
                    updateOngoingNotification(
                        score = scoreResult.score,
                        title = notifTitle,
                        message = notifText,
                        openHome = status != ProductStatus.ONLINE,
                        canHealNow = canHealNow,
                        healActionLabel = presentation.fixActionLabel
                    )

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

                    runDataMaintenanceIfDue()

                    // Trigger instant high-priority alert on transition to Zombie state
                    if (scoreResult.isZombieConnection && !lastObservedZombie) {
                        triggerZombieAlert(scoreResult.score, presentation.supportingText)
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

    /**
     * The Sentinel loop is the one thing reliably running in the background, so it's the
     * natural home for the data lifecycle job - gated to actually do work at most once every
     * 24h (tracked via NetPulsePreferences.lastDataPurgeTimestamp) rather than re-aggregating
     * and re-scanning for purge candidates on every 45s tick.
     */
    private suspend fun runDataMaintenanceIfDue() {
        val settings = preferences.settingsFlow.first()
        val now = System.currentTimeMillis()
        if (now - settings.lastDataPurgeTimestamp < 24 * 60 * 60 * 1000L) return
        try {
            dataLifecycleManager.runMaintenance(
                diagnosticRetentionDays = settings.diagnosticLogRetentionDays,
                telemetryRetentionDays = settings.rawTelemetryRetentionDays,
                nowMs = now
            )
            preferences.setLastDataPurgeTimestamp(now)
        } catch (e: Exception) {
            // Never let a maintenance failure take down the sentinel loop - it'll retry on
            // the next tick since lastDataPurgeTimestamp wasn't updated.
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // DEFAULT (not LOW) so this doesn't get collapsed into the OEM "silent"/minimized
            // notification group (e.g. Samsung One UI) - this is the always-visible connection
            // status the user asked to always be able to see, so it needs to stay in the main
            // notification list. No sound/vibration either way since setOngoing notifications
            // never alert on update, only on first post.
            val ongoingChannel = NotificationChannel(
                CHANNEL_ID,
                "NetPulse Connection Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Always-on Internet health score, shown as the status bar icon"
                setShowBadge(false)
                setSound(null, null)
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

    private fun buildOngoingNotification(
        score: Int?,
        title: String,
        message: String,
        openHome: Boolean = false,
        canHealNow: Boolean = false,
        healActionLabel: String = "Fix It"
    ): Notification {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            if (openHome) putExtra(EXTRA_OPEN_HOME, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(scoreStatusBarIcon(score))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // The score is not sensitive, so show the full notification on the lock screen
            // instead of Android's default "hidden" redaction - this is the value the user
            // said they most want visible without unlocking the phone.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (canHealNow) {
            val healIntent = Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_HOME, true)
                putExtra(EXTRA_HEAL_NOW, true)
            }
            // A distinct request code from the content intent (0) so FLAG_UPDATE_CURRENT
            // doesn't overwrite one PendingIntent's extras with the other's.
            val healPendingIntent = PendingIntent.getActivity(
                this,
                1,
                healIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, healActionLabel, healPendingIntent)
        }

        return builder.build()
    }

    /** Skips re-posting when nothing user-visible actually changed, so the notification
     *  doesn't churn every 45s while the connection is quietly healthy (Section 17: "do not
     *  create notification spam"). */
    private fun updateOngoingNotification(
        score: Int,
        title: String,
        message: String,
        openHome: Boolean = false,
        canHealNow: Boolean = false,
        healActionLabel: String = "Fix It"
    ) {
        if (title == lastNotifiedTitle && message == lastNotifiedText) return
        lastNotifiedTitle = title
        lastNotifiedText = message
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildOngoingNotification(score, title, message, openHome, canHealNow, healActionLabel))
    }

    private fun triggerZombieAlert(score: Int, message: String) {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_HOME, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alert = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("No Internet · $score/100")
            .setContentText(message)
            .setSmallIcon(scoreStatusBarIcon(score))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // This is a one-shot heads-up alert on top of the always-present ongoing
            // notification, which already updates to show the same bad score the moment this
            // fires - without a timeout the two would sit stacked in the shade indefinitely,
            // showing the same information twice. Self-clearing after 20s keeps its job to
            // "interrupt once" without leaving a second, redundant card behind.
            .setTimeoutAfter(20_000L)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ZOMBIE_ALERT_NOTIFICATION_ID, alert)
    }

    /**
     * Draws the usability score directly onto the small icon, so the number is visible in the
     * status bar itself without pulling down the shade - the same technique battery/signal
     * meter apps use (a plain first-party Notification.setSmallIcon call, not a hack). Status
     * bar icons are rendered as a system-tinted silhouette from the alpha channel, so this
     * draws solid white text on a transparent background; the system colors it appropriately
     * for light/dark status bars on its own. Null (startup, before the first probe) draws a
     * neutral "···" rather than a misleading "0".
     */
    private fun scoreStatusBarIcon(score: Int?): IconCompat {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val label = score?.toString() ?: "···"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textSize = when {
                score == null -> size * 0.30f
                score >= 100 -> size * 0.40f
                else -> size * 0.52f
            }
        }
        val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(label, size / 2f, yPos, paint)
        return IconCompat.createWithBitmap(bitmap)
    }
}
