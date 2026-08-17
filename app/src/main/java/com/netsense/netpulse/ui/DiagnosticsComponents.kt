package com.netsense.netpulse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.netsense.netpulse.model.DualStackResult
import com.netsense.netpulse.model.DualStackStatus
import com.netsense.netpulse.model.HopNodeType
import com.netsense.netpulse.model.HopTraceResult
import com.netsense.netpulse.model.PingTargetResult
import com.netsense.netpulse.model.TargetCategory
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
import com.netsense.netpulse.ui.theme.StatusUnusable
import com.netsense.netpulse.ui.theme.StatusUnusableBg

@Composable
fun DualStackDiagnosticCard(
    dualStack: DualStackResult?,
    isProbing: Boolean,
    onRunCheck: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dual_stack_card")
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
                            imageVector = Icons.Default.Language,
                            contentDescription = "Dual Stack",
                            tint = NetPulseOnAccentContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Dual-Stack IPv4 / IPv6 Engine",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NetPulseTextPrimary
                        )
                        Text(
                            text = "Happy Eyeballs RFC 8305 Diagnostic",
                            fontSize = 12.sp,
                            color = NetPulseTextSecondary
                        )
                    }
                }

                if (isProbing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    OutlinedButton(
                        onClick = onRunCheck,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("check_dual_stack_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Check", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (dualStack != null) {
                // Status Banner
                val statusBg = if (dualStack.status.isDegraded) StatusDegradedBg else StatusOptimalBg
                val statusColor = if (dualStack.status.isDegraded) StatusDegraded else StatusOptimal

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (dualStack.status.isDegraded) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = dualStack.status.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                            Text(
                                text = dualStack.status.description,
                                fontSize = 11.sp,
                                color = NetPulseTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // IPv4 & IPv6 Side by Side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // IPv4 Box
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
                                Text("IPv4 Route", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NetPulseTextPrimary)
                                ProtocolBadge(isReachable = dualStack.ipv4Reachable)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (dualStack.ipv4Reachable) "${dualStack.ipv4RttMs} ms RTT" else "Unreachable",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (dualStack.ipv4Reachable) NetPulseTextPrimary else StatusUnusable
                            )
                            Text(
                                text = dualStack.ipv4Address ?: "No IPv4 default route",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = NetPulseTextTertiary
                            )
                        }
                    }

                    // IPv6 Box
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
                                Text("IPv6 Route", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NetPulseTextPrimary)
                                ProtocolBadge(isReachable = dualStack.ipv6Reachable)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (dualStack.ipv6Reachable) "${dualStack.ipv6RttMs} ms RTT" else "Unprovisioned",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (dualStack.ipv6Reachable) NetPulseTextPrimary else NetPulseTextTertiary
                            )
                            Text(
                                text = dualStack.ipv6Address ?: "No IPv6 route found",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = NetPulseTextTertiary,
                                maxLines = 1
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Tap Check to test IPv4 vs IPv6 Happy Eyeballs socket latency.",
                    fontSize = 12.sp,
                    color = NetPulseTextTertiary
                )
            }
        }
    }
}

@Composable
fun MultiHostPingMatrixCard(
    results: List<PingTargetResult>,
    isProbing: Boolean,
    selectedCategory: TargetCategory?,
    onSelectCategory: (TargetCategory?) -> Unit,
    onRunMatrixProbe: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ping_matrix_card")
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
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Ping Matrix",
                            tint = NetPulseOnAccentContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Multi-Host Reachability Matrix",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NetPulseTextPrimary
                        )
                        Text(
                            text = "${results.size} Targets (DNS, CDN, Gaming, APIs)",
                            fontSize = 12.sp,
                            color = NetPulseTextSecondary
                        )
                    }
                }

                if (isProbing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Button(
                        onClick = onRunMatrixProbe,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NetPulseAccent),
                        modifier = Modifier.testTag("run_ping_matrix_btn")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Probe All", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryPill(
                    label = "All Targets (${results.size})",
                    isSelected = selectedCategory == null,
                    onClick = { onSelectCategory(null) }
                )
                TargetCategory.values().forEach { cat ->
                    val count = results.count { it.target.category == cat }
                    CategoryPill(
                        label = "${cat.title} ($count)",
                        isSelected = selectedCategory == cat,
                        onClick = { onSelectCategory(cat) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val filtered = if (selectedCategory == null) results else results.filter { it.target.category == selectedCategory }

            if (filtered.isEmpty() && isProbing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Probing targets across Anycast DNS, CDN edges & VoIP servers...", fontSize = 12.sp, color = NetPulseTextSecondary)
                }
            } else if (filtered.isEmpty()) {
                Text("No targets probed yet. Tap Probe All to start matrix.", fontSize = 12.sp, color = NetPulseTextTertiary)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtered.forEach { item ->
                        PingTargetRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun PingTargetRow(item: PingTargetResult) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = NetPulseSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                val icon = when (item.target.category) {
                    TargetCategory.DNS_ANYCAST -> Icons.Default.Dns
                    TargetCategory.CLOUD_CDN -> Icons.Default.CloudQueue
                    TargetCategory.GAMING_VOICE -> Icons.Default.SportsEsports
                    TargetCategory.PRODUCTIVITY -> Icons.Default.Language
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NetPulseTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = item.target.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NetPulseTextPrimary
                    )
                    Text(
                        text = "${item.target.host} • ${item.resolvedIp ?: item.target.ip}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = NetPulseTextTertiary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (item.isSuccess && item.rttMs != null) {
                    val badgeColor = when {
                        item.rttMs < 50 -> StatusOptimal
                        item.rttMs < 120 -> StatusGood
                        item.rttMs < 250 -> StatusDegraded
                        else -> StatusUnusable
                    }
                    Text(
                        text = "${item.rttMs} ms",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                    if (item.isTlsSuccess && item.tlsHandshakeMs != null) {
                        Text(
                            text = "TLS: ${item.tlsHandshakeMs}ms",
                            fontSize = 10.sp,
                            color = NetPulseTextSecondary
                        )
                    } else if (item.jitterMs != null && item.jitterMs > 0) {
                        Text(
                            text = "±${item.jitterMs}ms jitter",
                            fontSize = 10.sp,
                            color = NetPulseTextTertiary
                        )
                    }
                } else {
                    Text(
                        text = "Timeout",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusUnusable
                    )
                    Text(
                        text = "100% loss",
                        fontSize = 10.sp,
                        color = StatusUnusable
                    )
                }
            }
        }
    }
}

@Composable
fun PathHopTracerCard(
    hopTrace: HopTraceResult?,
    isTracing: Boolean,
    onRunTrace: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hop_tracer_card")
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
                            imageVector = Icons.Default.Route,
                            contentDescription = "Hop Tracer",
                            tint = NetPulseOnAccentContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Visual Path Hop Tracer",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NetPulseTextPrimary
                        )
                        Text(
                            text = "Gateway ➔ ISP ➔ DNS ➔ CDN ➔ Host",
                            fontSize = 12.sp,
                            color = NetPulseTextSecondary
                        )
                    }
                }

                if (isTracing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    OutlinedButton(
                        onClick = { onRunTrace("connectivitycheck.gstatic.com") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("run_hop_trace_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Trace", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (hopTrace != null && hopTrace.hops.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    hopTrace.hops.forEach { hop ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (hop.hopIndex == hopTrace.bottleneckHopIndex && (hop.rttMs ?: 0) > 100) StatusDegradedBg else NetPulseSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(NetPulseBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${hop.hopIndex}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NetPulseTextPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = hop.label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NetPulseTextPrimary
                                        )
                                        Text(
                                            text = hop.ipOrHost,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = NetPulseTextTertiary
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = hop.rttMs?.let { "$it ms" } ?: "* timeout",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hop.isReachable) NetPulseTextPrimary else StatusUnusable
                                    )
                                    Text(
                                        text = hop.statusNote,
                                        fontSize = 10.sp,
                                        color = NetPulseTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Tap Trace to map intermediate routing hops and identify network bottlenecks.",
                    fontSize = 12.sp,
                    color = NetPulseTextTertiary
                )
            }
        }
    }
}

@Composable
private fun ProtocolBadge(isReachable: Boolean) {
    val bg = if (isReachable) StatusOptimalBg else StatusDegradedBg
    val fg = if (isReachable) StatusOptimal else StatusDegraded
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = if (isReachable) "Active" else "Inactive",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun CategoryPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) NetPulseAccent else NetPulseSurfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else NetPulseTextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
