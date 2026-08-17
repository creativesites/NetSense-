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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netsense.netpulse.engine.SimulatedFaultScenario
import com.netsense.netpulse.model.HealerActionItem
import com.netsense.netpulse.model.HealerActionType
import com.netsense.netpulse.ui.theme.NetPulseAccent
import com.netsense.netpulse.ui.theme.NetPulseAccentContainer
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
import com.netsense.netpulse.ui.theme.StatusZombie
import com.netsense.netpulse.ui.theme.StatusZombieBg

@Composable
fun SmartHealerCard(
    actions: List<HealerActionItem>,
    onExecuteAction: (HealerActionItem) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_healer_card")
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
                        imageVector = Icons.Default.Build,
                        contentDescription = "Smart Healer",
                        tint = NetPulseOnAccentContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Automated Network Healer",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NetPulseTextPrimary
                    )
                    Text(
                        text = "Targeted 1-Tap Optimization Remedies",
                        fontSize = 12.sp,
                        color = NetPulseTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (actions.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StatusOptimalBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✨ Network interfaces, routes, and socket pools are running optimally. No critical fixes required.",
                        fontSize = 12.sp,
                        color = StatusOptimal,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    actions.forEach { action ->
                        HealerActionRow(action, onExecute = { onExecuteAction(action) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HealerActionRow(
    action: HealerActionItem,
    onExecute: () -> Unit
) {
    val impactBg = when (action.impactLevel) {
        "High Impact" -> StatusUnusableBg
        "Medium Impact" -> StatusDegradedBg
        else -> StatusGoodBg
    }
    val impactColor = when (action.impactLevel) {
        "High Impact" -> StatusUnusable
        "Medium Impact" -> StatusDegraded
        else -> StatusGood
    }

    val actionIcon = when (action.actionType) {
        HealerActionType.AIRPLANE_CYCLE -> Icons.Default.Flight
        HealerActionType.SWITCH_NETWORK -> Icons.Default.SwapHoriz
        HealerActionType.OPEN_CAPTIVE_PORTAL -> Icons.Default.OpenInBrowser
        HealerActionType.OPTIMIZE_DNS -> Icons.Default.Settings
        HealerActionType.FLUSH_SOCKET_CACHE -> Icons.Default.CleaningServices
        else -> Icons.Default.Build
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NetPulseSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(imageVector = actionIcon, contentDescription = null, tint = impactColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = action.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NetPulseTextPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = impactBg
                ) {
                    Text(
                        text = action.impactLevel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = impactColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = action.description,
                fontSize = 12.sp,
                color = NetPulseTextSecondary
            )

            if (action.recommendedValue != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Recommended: ${action.recommendedValue}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NetPulseAccent
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onExecute,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (action.impactLevel == "High Impact") NetPulseAccent else NetPulseAccentContainer,
                        contentColor = if (action.impactLevel == "High Impact") Color.White else NetPulseOnAccentContainer
                    ),
                    modifier = Modifier.testTag("execute_action_${action.id}")
                ) {
                    Icon(
                        imageVector = if (action.isAutoFixable) Icons.Default.Refresh else Icons.Default.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (action.isAutoFixable) "Auto-Fix Now" else "Open Settings",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun FaultSandboxSimulatorCard(
    currentScenario: SimulatedFaultScenario,
    onSelectScenario: (SimulatedFaultScenario) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetPulseSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fault_sandbox_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(StatusDegradedBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = "Sandbox",
                        tint = StatusDegraded,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Network Fault Sandbox Simulator",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NetPulseTextPrimary
                    )
                    Text(
                        text = "Inject realistic network anomalies safely",
                        fontSize = 12.sp,
                        color = NetPulseTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SimulatedFaultScenario.values().forEach { scenario ->
                    val isSelected = scenario == currentScenario
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) NetPulseAccentContainer else NetPulseSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scenario_option_${scenario.name}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = scenario.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) NetPulseOnAccentContainer else NetPulseTextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = scenario.description,
                                    fontSize = 11.sp,
                                    color = NetPulseTextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isSelected) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = NetPulseAccent
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onSelectScenario(scenario) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("apply_scenario_${scenario.name}")
                                ) {
                                    Text("Inject", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
