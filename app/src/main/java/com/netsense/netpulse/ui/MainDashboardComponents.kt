package com.netsense.netpulse.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netsense.netpulse.ai.gemini.GeminiAiConsultation
import com.netsense.netpulse.model.CellularRfSnapshot
import com.netsense.netpulse.model.DiagnosticMode
import com.netsense.netpulse.model.DiagnosticProbeResult
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.model.UsabilityRating
import com.netsense.netpulse.model.UsabilityScoreResult
import com.netsense.netpulse.model.WifiRadarSnapshot
import com.netsense.netpulse.ui.theme.NetPulseAccent
import com.netsense.netpulse.ui.theme.NetPulseAccentContainer
import com.netsense.netpulse.ui.theme.NetPulseBg
import com.netsense.netpulse.ui.theme.NetPulseBorder
import com.netsense.netpulse.ui.theme.NetPulseBorderSubtle
import com.netsense.netpulse.ui.theme.NetPulseOnAccentContainer
import com.netsense.netpulse.ui.theme.NetPulseSurface
import com.netsense.netpulse.ui.theme.NetPulseSurfaceVariant
import com.netsense.netpulse.ui.theme.NetPulseTextPrimary
import com.netsense.netpulse.ui.theme.NetPulseTextSecondary
import com.netsense.netpulse.ui.theme.NetPulseTextTertiary
import com.netsense.netpulse.ui.theme.StatusDegraded
import com.netsense.netpulse.ui.theme.StatusDegradedBg
import com.netsense.netpulse.ui.theme.StatusGood
import com.netsense.netpulse.ui.theme.StatusGoodBg
import com.netsense.netpulse.ui.theme.StatusOptimal
import com.netsense.netpulse.ui.theme.StatusOptimalBg
import com.netsense.netpulse.ui.theme.StatusPoor
import com.netsense.netpulse.ui.theme.StatusPoorBg
import com.netsense.netpulse.ui.theme.StatusUnusable
import com.netsense.netpulse.ui.theme.StatusUnusableBg
import com.netsense.netpulse.ui.theme.StatusZombie
import com.netsense.netpulse.ui.theme.StatusZombieBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// =============================================================================
// 1. SIGNAL QUALITY GAUGE (Speedometer & Physical RF Sweep Meter)
// =============================================================================
@Composable
fun SignalQualityGaugeCard(
    snapshot: NetworkSnapshot,
    wifiRadar: WifiRadarSnapshot,
    cellularRf: CellularRfSnapshot,
    modifier: Modifier = Modifier
) {
    // Determine raw dBm, signal percentage (0..100), and tier description
    val isWifi = snapshot.primaryTransport == NetworkTransport.WIFI
    val rawDbm: Int? = if (isWifi) {
        wifiRadar.rssiDbm
    } else {
        cellularRf.rsrpDbm ?: snapshot.signalDbm
    }

    // Convert dBm to 0..100 quality percentage
    // For Wi-Fi: -50 dBm or better = 100%, -90 dBm or worse = 0%
    // For LTE/5G RSRP: -80 dBm or better = 100%, -120 dBm or worse = 0%
    val qualityPct: Float = when {
        rawDbm == null || !snapshot.isConnected -> 0f
        isWifi -> ((rawDbm + 90).toFloat() / 40f).coerceIn(0f, 1f) * 100f
        else -> ((rawDbm + 120).toFloat() / 40f).coerceIn(0f, 1f) * 100f
    }

    val animatedQuality by animateFloatAsState(
        targetValue = qualityPct,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "signal_gauge_anim"
    )

    val (tierLabel, tierColor, tierBg) = when {
        !snapshot.isConnected -> Triple("NO SIGNAL", StatusUnusable, StatusUnusableBg)
        qualityPct >= 80f -> Triple("EXCELLENT", StatusOptimal, StatusOptimalBg)
        qualityPct >= 60f -> Triple("GOOD", StatusGood, StatusGoodBg)
        qualityPct >= 40f -> Triple("FAIR", StatusDegraded, StatusDegradedBg)
        qualityPct >= 20f -> Triple("POOR", StatusPoor, StatusPoorBg)
        else -> Triple("CRITICAL", StatusUnusable, StatusUnusableBg)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("signal_quality_gauge"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(tierColor.copy(alpha = 0.4f), NetPulseBorder)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NetPulseSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isWifi) Icons.Default.WifiTethering else Icons.Default.CellTower,
                            contentDescription = "Signal Gauge",
                            tint = NetPulseAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Signal Quality Gauge",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NetPulseTextPrimary
                        )
                        Text(
                            text = if (isWifi) "Wi-Fi Physical Layer (RSSI)" else "Cellular Radio Layer (RSRP/SINR)",
                            style = MaterialTheme.typography.labelSmall,
                            color = NetPulseTextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = tierBg
                ) {
                    Text(
                        text = tierLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tierColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Speedometer Arc Canvas
            Box(
                modifier = Modifier
                    .size(width = 240.dp, height = 145.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(220.dp, 140.dp)) {
                    val strokeWidth = 14.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height * 1.8f)
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                    val startAngle = 160f
                    val sweepAngle = 220f

                    // Background Track
                    drawArc(
                        color = NetPulseSurfaceVariant,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active Colored Arc
                    val activeSweep = (animatedQuality / 100f) * sweepAngle
                    if (activeSweep > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                0.0f to StatusUnusable,
                                0.3f to StatusDegraded,
                                0.6f to StatusGood,
                                1.0f to StatusOptimal
                            ),
                            startAngle = startAngle,
                            sweepAngle = activeSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Gauge Needle Indicator
                    val currentAngleRad = Math.toRadians((startAngle + activeSweep).toDouble())
                    val radius = (arcSize.width / 2) - 10.dp.toPx()
                    val centerX = size.width / 2
                    val centerY = arcSize.height / 2

                    val needleEndX = centerX + radius * cos(currentAngleRad).toFloat()
                    val needleEndY = centerY + radius * sin(currentAngleRad).toFloat()

                    drawCircle(
                        color = tierColor,
                        radius = 7.dp.toPx(),
                        center = Offset(needleEndX, needleEndY)
                    )
                    drawCircle(
                        color = NetPulseSurface,
                        radius = 3.dp.toPx(),
                        center = Offset(needleEndX, needleEndY)
                    )
                }

                // Center Numerical Readout
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 28.dp)
                ) {
                    Text(
                        text = if (snapshot.isConnected) "${animatedQuality.toInt()}%" else "--",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = tierColor,
                        lineHeight = 38.sp
                    )
                    Text(
                        text = if (rawDbm != null) "$rawDbm dBm" else "No Signal",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = NetPulseTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-metrics Grid (Link Speed, Frequency, SINR, Channel)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NetPulseSurfaceVariant)
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isWifi) "LINK SPEED" else "NETWORK",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetPulseTextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isWifi) "${wifiRadar.linkSpeedMbps} Mbps" else cellularRf.dataNetworkType,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isWifi) "BAND / FREQ" else "SINR / QUALITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetPulseTextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isWifi) {
                            if (wifiRadar.frequencyMhz > 5000) "5 GHz" else "2.4 GHz"
                        } else {
                            if (cellularRf.sinrDb != null) "${cellularRf.sinrDb} dB" else "Standard"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isWifi) "CHANNEL" else "CARRIER",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetPulseTextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isWifi) {
                            if (wifiRadar.channelNumber > 0) "Ch ${wifiRadar.channelNumber}" else "--"
                        } else {
                            snapshot.carrierName ?: "Active"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                }
            }
        }
    }
}

// =============================================================================
// 2. CURRENT CONNECTION STATUS INDICATOR
// =============================================================================
@Composable
fun CurrentConnectionStatusCard(
    snapshot: NetworkSnapshot,
    wifiRadar: WifiRadarSnapshot,
    scoreResult: UsabilityScoreResult,
    probeResult: DiagnosticProbeResult?,
    modifier: Modifier = Modifier
) {
    // Pulse animation for live beacon dot
    val infiniteTransition = rememberInfiniteTransition(label = "beacon_pulse")
    val beaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_alpha"
    )

    val (statusTitle, statusSubtitle, statusColor, statusBg, statusIcon) = when {
        !snapshot.isConnected -> Tuple5(
            "DISCONNECTED",
            "No active network connection",
            StatusUnusable,
            StatusUnusableBg,
            Icons.Default.Error
        )
        scoreResult.isZombieConnection -> Tuple5(
            "ZOMBIE CONNECTION",
            "Link associated but 0% internet transit",
            StatusZombie,
            StatusZombieBg,
            Icons.Default.Warning
        )
        snapshot.isCaptivePortal -> Tuple5(
            "CAPTIVE PORTAL DETECTED",
            "Authentication or login page required",
            StatusDegraded,
            StatusDegradedBg,
            Icons.Default.Warning
        )
        snapshot.isValidated -> Tuple5(
            "ONLINE & VALIDATED",
            "Direct internet route confirmed operational",
            StatusOptimal,
            StatusOptimalBg,
            Icons.Default.CheckCircle
        )
        else -> Tuple5(
            "CONNECTED (UNVALIDATED)",
            "Connected to local interface, validating WAN...",
            StatusGood,
            StatusGoodBg,
            Icons.Default.NetworkCheck
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("connection_status_indicator"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(statusColor.copy(alpha = 0.5f), NetPulseBorder)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Status Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsing Glowing Beacon Dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = beaconAlpha))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = statusSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = NetPulseTextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = NetPulseBorderSubtle, thickness = 1.dp)

            // Connection Details Matrix
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Column 1: Transport & Network Name
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ACTIVE TRANSPORT",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetPulseTextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        val icon = when (snapshot.primaryTransport) {
                            NetworkTransport.WIFI -> Icons.Default.Wifi
                            NetworkTransport.CELLULAR -> Icons.Default.CellTower
                            else -> Icons.Default.SignalCellularAlt
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = NetPulseAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (!snapshot.isConnected) "None"
                            else (if (snapshot.primaryTransport == NetworkTransport.WIFI) wifiRadar.ssid ?: "Wi-Fi Network" else snapshot.carrierName ?: snapshot.primaryTransport.name),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = NetPulseTextPrimary
                        )
                    }
                }

                // Column 2: IP / Interface
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LOCAL GATEWAY & IFACE",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetPulseTextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (!snapshot.isConnected) "0.0.0.0"
                        else "${wifiRadar.gatewayIp ?: "Assigned"} (${snapshot.interfaceName ?: "wlan0"})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = NetPulseTextPrimary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Column 3: DNS Server
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DNS SERVER",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetPulseTextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (snapshot.dnsServers.isNotEmpty()) snapshot.dnsServers.first() else "System Default",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = NetPulseTextPrimary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Column 4: Latency Ping
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PING RTT LATENCY",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetPulseTextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val rtt = probeResult?.averageTcpMs
                    Text(
                        text = if (rtt != null && rtt > 0) "$rtt ms" else if (probeResult != null) "Unreachable" else "Pending verify",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (rtt != null && rtt < 80) StatusOptimal else if (rtt != null && rtt < 180) StatusDegraded else NetPulseAccent,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

// =============================================================================
// 3. 'RUN DIAGNOSTICS' BUTTON & ACTION SUITE
// =============================================================================
@Composable
fun RunDiagnosticsActionCard(
    isProbing: Boolean,
    onRunProbe: (DiagnosticMode) -> Unit,
    lastChecked: Long,
    selectedMode: DiagnosticMode = DiagnosticMode.STANDARD,
    onSelectMode: (DiagnosticMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("run_diagnostics_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(NetPulseAccent.copy(alpha = 0.4f), NetPulseBorder)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NetPulseAccentContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Diagnostics Action",
                            tint = NetPulseOnAccentContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Diagnostic Engine",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NetPulseTextPrimary
                        )
                        Text(
                            text = "Layer 3 DNS • Layer 4 TCP • Layer 7 TTFB",
                            style = MaterialTheme.typography.labelSmall,
                            color = NetPulseTextSecondary
                        )
                    }
                }

                if (lastChecked > 0L) {
                    Text(
                        text = "Last: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastChecked))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetPulseTextTertiary
                    )
                }
            }

            // Big Primary 'Run Diagnostics' Button
            ElevatedButton(
                onClick = { onRunProbe(selectedMode) },
                enabled = !isProbing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("run_diagnostics_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = NetPulseAccent,
                    contentColor = Color.Black
                ),
                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 3.dp)
            ) {
                if (isProbing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "PROBING MULTI-TARGET MATRIX...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RUN DIAGNOSTICS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Mode Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiagnosticMode.entries.forEach { mode ->
                    val isSelected = mode == selectedMode
                    val modeLabel = when (mode) {
                        DiagnosticMode.MICRO -> "Micro (Fast)"
                        DiagnosticMode.STANDARD -> "Standard (4 Targets)"
                        DiagnosticMode.DEEP -> "Deep (All Targets)"
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) NetPulseAccentContainer else NetPulseSurfaceVariant,
                        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(NetPulseAccent)
                        ) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectMode(mode) }
                    ) {
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) NetPulseOnAccentContainer else NetPulseTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// 4. AI FEATURES FOR DASHBOARD (Gemini AI Network Copilot & Smart Assistant)
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiDashboardCopilotCard(
    consultation: GeminiAiConsultation?,
    isConsulting: Boolean,
    onConsultAi: (String) -> Unit,
    onClearConsultation: () -> Unit,
    modifier: Modifier = Modifier
) {
    var queryText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val promptSuggestions = listOf(
        "⚡ Why is my latency/ping spiking?",
        "📡 Analyze my RF signal quality & dBm",
        "🛡️ Diagnose captive portal / zombie connection",
        "🚀 Recommend Wi-Fi channel & MTU optimization"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_copilot_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF8AB4F8).copy(alpha = 0.5f), NetPulseBorder)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with AI Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF4285F4), Color(0xFF9C27B0)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Network Copilot",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Gemini AI Network Copilot",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NetPulseTextPrimary
                        )
                        Text(
                            text = "Deep Telemetry Insights & Smart Diagnostics",
                            style = MaterialTheme.typography.labelSmall,
                            color = NetPulseTextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = Color(0xFF8AB4F8),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "AI Brain",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8AB4F8)
                        )
                    }
                }
            }

            // Quick Prompt Chips
            Text(
                text = "Tap a prompt to consult AI on live telemetry:",
                style = MaterialTheme.typography.labelSmall,
                color = NetPulseTextSecondary
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                promptSuggestions.forEach { prompt ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = NetPulseSurfaceVariant,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorderSubtle)
                        ),
                        modifier = Modifier.clickable(enabled = !isConsulting) {
                            onConsultAi(prompt)
                        }
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.labelSmall,
                            color = NetPulseTextPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Custom Question Input Field
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    placeholder = {
                        Text(
                            "Ask AI about your connection...",
                            style = MaterialTheme.typography.bodySmall,
                            color = NetPulseTextTertiary
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_query_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NetPulseAccent,
                        unfocusedBorderColor = NetPulseBorder,
                        focusedContainerColor = NetPulseBg,
                        unfocusedContainerColor = NetPulseBg
                    ),
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (queryText.isNotBlank() && !isConsulting) {
                                onConsultAi(queryText)
                                keyboardController?.hide()
                                queryText = ""
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (queryText.isNotBlank() && !isConsulting) {
                            onConsultAi(queryText)
                            keyboardController?.hide()
                            queryText = ""
                        }
                    },
                    enabled = queryText.isNotBlank() && !isConsulting,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (queryText.isNotBlank()) NetPulseAccent else NetPulseSurfaceVariant)
                        .testTag("ai_submit_button")
                ) {
                    if (isConsulting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send to AI",
                            tint = if (queryText.isNotBlank()) Color.Black else NetPulseTextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // AI Consultation Result Display
            AnimatedVisibility(
                visible = consultation != null || isConsulting,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NetPulseBg,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(NetPulseBorder)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = consultation?.source ?: "Analyzing telemetry...",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NetPulseAccent
                                )
                            }

                            if (consultation != null) {
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NetPulseTextTertiary,
                                    modifier = Modifier
                                        .clickable { onClearConsultation() }
                                        .padding(4.dp)
                                )
                            }
                        }

                        if (isConsulting) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = NetPulseAccent
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Synthesizing cross-layer telemetry with Gemini...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NetPulseTextSecondary
                                )
                            }
                        } else if (consultation != null) {
                            Text(
                                text = "Q: ${consultation.query}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NetPulseTextSecondary
                            )

                            Text(
                                text = consultation.response,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NetPulseTextPrimary,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
