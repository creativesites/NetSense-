package com.netsense.netpulse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netsense.netpulse.model.CellularRfSnapshot
import com.netsense.netpulse.model.NetworkComparisonSummary
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.model.RfUnavailableReason
import com.netsense.netpulse.model.WifiBand
import com.netsense.netpulse.model.WifiRadarSnapshot
import com.netsense.netpulse.ui.theme.NetPulseAccent
import com.netsense.netpulse.ui.theme.NetPulseAccentContainer
import com.netsense.netpulse.ui.theme.NetPulseBg
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
import com.netsense.netpulse.ui.theme.StatusUnusable
import com.netsense.netpulse.ui.theme.StatusUnusableBg

@Composable
fun LinkStabilityCard(
    stabilityScore: Int,
    verdict: String
) {
    val scoreColor = when {
        stabilityScore >= 85 -> StatusOptimal
        stabilityScore >= 65 -> StatusGood
        stabilityScore >= 40 -> StatusDegraded
        else -> StatusUnusable
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("link_stability_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (stabilityScore >= 70) StatusOptimalBg else StatusDegradedBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = "Stability",
                        tint = scoreColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Connection Stability Index",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NetPulseTextPrimary
                    )
                    Text(
                        text = verdict,
                        fontSize = 12.sp,
                        color = NetPulseTextSecondary
                    )
                }
            }

            Text(
                text = "$stabilityScore / 100",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )
        }
    }
}

@Composable
fun NetworkComparisonHeadToHeadCard(
    summary: NetworkComparisonSummary
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("head_to_head_comparison_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NetPulseAccentContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CompareArrows,
                        contentDescription = "Comparison",
                        tint = NetPulseOnAccentContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Wi-Fi vs Cellular Benchmark",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NetPulseTextPrimary
                    )
                    Text(
                        text = "Real-time Path Arbitrator",
                        fontSize = 12.sp,
                        color = NetPulseTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Recommended Verdict Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (summary.overallWinner == NetworkTransport.WIFI) StatusOptimalBg else StatusGoodBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (summary.overallWinner == NetworkTransport.WIFI) Icons.Default.Wifi else Icons.Default.SignalCellularAlt,
                        contentDescription = null,
                        tint = if (summary.overallWinner == NetworkTransport.WIFI) StatusOptimal else StatusGood,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = summary.recommendationTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (summary.overallWinner == NetworkTransport.WIFI) StatusOptimal else StatusGood
                        )
                        Text(
                            text = summary.recommendationBody,
                            fontSize = 11.sp,
                            color = NetPulseTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Score Compare Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Wi-Fi Score Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NetPulseSurfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Wi-Fi Score", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NetPulseTextPrimary)
                            Icon(imageVector = Icons.Default.Wifi, contentDescription = null, tint = NetPulseTextSecondary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${summary.wifiScore} / 100",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NetPulseAccent
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { summary.wifiScore / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = NetPulseAccent,
                            trackColor = NetPulseBorderSubtle
                        )
                    }
                }

                // Cellular Score Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NetPulseSurfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cellular Score", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NetPulseTextPrimary)
                            Icon(imageVector = Icons.Default.SignalCellularAlt, contentDescription = null, tint = NetPulseTextSecondary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${summary.cellularScore} / 100",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusGood
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { summary.cellularScore / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = StatusGood,
                            trackColor = NetPulseBorderSubtle
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Comparison Metrics Table
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.metrics.forEach { metric ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NetPulseBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text(metric.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NetPulseTextPrimary)
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(metric.wifiValue, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = NetPulseTextSecondary)
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(metric.cellularValue, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = NetPulseTextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WifiRadarCard(radar: WifiRadarSnapshot) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wifi_radar_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NetPulseAccentContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Wi-Fi Radar",
                            tint = NetPulseOnAccentContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Wi-Fi RF & Channel Radar",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NetPulseTextPrimary
                        )
                        Text(
                            text = radar.ssid ?: "Not Connected to Wi-Fi",
                            fontSize = 12.sp,
                            color = NetPulseTextSecondary
                        )
                    }
                }

                if (radar.isWifiConnected) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = StatusOptimalBg
                    ) {
                        Text(
                            text = radar.band.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusOptimal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (radar.isWifiConnected) {
                // Signal RSSI Gauge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Signal Attenuation (RSSI)", fontSize = 12.sp, color = NetPulseTextSecondary)
                    Text(
                        text = "${radar.rssiDbm} dBm (${radar.signalStrengthPercent}%)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (radar.rssiDbm > -70) StatusOptimal else StatusDegraded
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { radar.signalStrengthPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (radar.rssiDbm > -70) StatusOptimal else StatusDegraded,
                    trackColor = NetPulseBorderSubtle
                )

                Spacer(modifier = Modifier.height(14.dp))

                // RF Technical Details Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RfChip("Channel", "${radar.channelNumber} (${radar.frequencyMhz}MHz)", Modifier.weight(1f))
                    RfChip("Width", "${radar.channelWidthMhz} MHz", Modifier.weight(1f))
                    RfChip("Standard", radar.wifiStandard, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RfChip("Link Speed", "${radar.linkSpeedMbps} Mbps", Modifier.weight(1f))
                    RfChip("Congestion", radar.congestionLevel, Modifier.weight(1f))
                    RfChip("Interference", radar.interferenceRisk, Modifier.weight(1f))
                }
            } else {
                Text(
                    text = "Device is not currently connected to a Wi-Fi Access Point.",
                    fontSize = 12.sp,
                    color = NetPulseTextTertiary
                )
            }
        }
    }
}

@Composable
fun CellularRfCard(
    cellular: CellularRfSnapshot,
    onRequestLocationPermission: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cellular_rf_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NetPulseAccentContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CellTower,
                            contentDescription = "Cell Tower",
                            tint = NetPulseOnAccentContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Cellular Baseband & RF Telemetry",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NetPulseTextPrimary
                        )
                        Text(
                            text = "${cellular.carrierName ?: "Mobile Radio"} • ${cellular.dataNetworkType}",
                            fontSize = 12.sp,
                            color = NetPulseTextSecondary
                        )
                    }
                }

                if (cellular.isCellularConnected) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = StatusGoodBg
                    ) {
                        Text(
                            text = cellular.simState,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusGood,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (cellular.unavailableReason != null) {
                RfUnavailableBanner(
                    reason = cellular.unavailableReason,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onOpenLocationSettings = onOpenLocationSettings
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // RF Engineering Grid (RSRP, RSRQ, SINR, CQI) - "N/A" whenever the detailed
            // registered-cell reading wasn't actually measured this cycle. Never fabricate
            // a plausible-looking placeholder number here.
            val measured = cellular.isRfDataMeasured
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RfChip("RSRP (Power)", cellular.rsrpDbm?.let { "$it dBm" } ?: "N/A", Modifier.weight(1f), isMeasured = cellular.rsrpDbm != null)
                RfChip("RSRQ (Quality)", cellular.rsrqDb?.let { "$it dB" } ?: "N/A", Modifier.weight(1f), isMeasured = measured)
                RfChip("SINR (SNR)", cellular.sinrDb?.let { "$it dB" } ?: "N/A", Modifier.weight(1f), isMeasured = measured)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RfChip("Serving Band", cellular.bandIndicator ?: "N/A", Modifier.weight(1.2f), isMeasured = measured)
                RfChip("CQI Index", cellular.cqi?.let { "CQI $it" } ?: "N/A", Modifier.weight(0.9f), isMeasured = measured)
                RfChip("Cell ID / PCI", cellular.pci?.let { "$it (PCI)" } ?: "N/A", Modifier.weight(0.9f), isMeasured = measured)
            }
        }
    }
}

@Composable
private fun RfUnavailableBanner(
    reason: RfUnavailableReason,
    onRequestLocationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit
) {
    val (message, actionLabel, action) = when (reason) {
        RfUnavailableReason.PERMISSION_DENIED -> Triple(
            "Detailed RF telemetry (RSRQ/SINR/CQI/Cell ID) requires Location permission - Android ties cell-tower detail to it.",
            "Grant Location",
            onRequestLocationPermission
        )
        RfUnavailableReason.LOCATION_SERVICES_DISABLED -> Triple(
            "Location permission is granted, but system Location Services is off. Android blocks cell-tower detail while it's off, even with permission granted.",
            "Turn On Location",
            onOpenLocationSettings
        )
        RfUnavailableReason.NO_REGISTERED_CELL -> Triple(
            "Not available from the modem right now (no registered cell info this cycle).",
            null,
            null
        )
        RfUnavailableReason.NOT_CELLULAR -> Triple(
            "Not on cellular right now - connect via mobile data to see RF telemetry.",
            null,
            null
        )
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StatusDegradedBg,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rf_unavailable_banner")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = StatusDegraded,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = NetPulseTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
            if (actionLabel != null && action != null) {
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = action,
                    modifier = Modifier.testTag("rf_unavailable_action_button")
                ) {
                    Text(actionLabel, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RfChip(label: String, value: String, modifier: Modifier = Modifier, isMeasured: Boolean = true) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = NetPulseSurfaceVariant,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, fontSize = 10.sp, color = NetPulseTextTertiary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isMeasured) NetPulseTextPrimary else NetPulseTextTertiary,
                maxLines = 1
            )
        }
    }
}
