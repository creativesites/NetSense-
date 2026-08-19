package com.netsense.netpulse.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netsense.netpulse.R
import com.netsense.netpulse.ui.theme.NetPulseAccent
import com.netsense.netpulse.ui.theme.NetPulseAccentContainer
import com.netsense.netpulse.ui.theme.NetPulseBg
import com.netsense.netpulse.ui.theme.NetPulseOnAccentContainer
import com.netsense.netpulse.ui.theme.NetPulseSurfaceVariant
import com.netsense.netpulse.ui.theme.NetPulseTextPrimary
import com.netsense.netpulse.ui.theme.NetPulseTextSecondary
import com.netsense.netpulse.ui.theme.NetPulseTextTertiary

private data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val body: String
)

private val onboardingSteps = listOf(
    OnboardingStep(
        icon = Icons.Default.NetworkCheck,
        title = "Meet NetPulse",
        body = "NetPulse tells you whether the Internet actually works right now - not just whether your device is connected to a network."
    ),
    OnboardingStep(
        icon = Icons.Default.SignalCellularAlt,
        title = "Signal isn't the whole story",
        body = "Full signal bars don't mean the Internet works. NetPulse actively probes DNS, TCP, and HTTP to catch \"zombie connections\" - strong radio, dead upstream - that your status bar can't show you."
    ),
    OnboardingStep(
        icon = Icons.Default.CellTower,
        title = "Location permission, used for RF only",
        body = "Android requires Location permission to read detailed cellular signal data (RSRP, RSRQ, SINR, cell tower info). NetPulse never uses it to track your location - only to read radio telemetry."
    ),
    OnboardingStep(
        icon = Icons.Default.Security,
        title = "Background monitoring",
        body = "NetPulse's Sentinel service can keep checking your connection in the background and alert you the moment it goes dead - even when the app isn't open."
    )
)

@Composable
fun OnboardingScreen(
    onRequestPermissions: () -> Unit,
    onEnableSentinel: () -> Unit,
    onFinish: () -> Unit
) {
    var stepIndex by remember { mutableIntStateOf(0) }
    val step = onboardingSteps[stepIndex]
    val isLastStep = stepIndex == onboardingSteps.size - 1

    Surface(modifier = Modifier.fillMaxSize(), color = NetPulseBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onFinish,
                    modifier = Modifier.testTag("onboarding_skip_button")
                ) {
                    Text("Skip", color = NetPulseTextTertiary)
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                if (stepIndex == 0) {
                    // The real app icon on the welcome step - every later step keeps its own
                    // contextual vector icon (signal, cell tower, security), but this is the
                    // one place onboarding should actually show the NetPulse brand mark.
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_netpulse),
                        contentDescription = "NetPulse",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(NetPulseAccentContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = step.icon,
                            contentDescription = null,
                            tint = NetPulseOnAccentContainer,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = step.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = NetPulseTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = step.body,
                    fontSize = 14.sp,
                    color = NetPulseTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                if (stepIndex == 2) {
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onRequestPermissions,
                        modifier = Modifier.testTag("onboarding_grant_permissions_button")
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Grant Location Permission")
                    }
                }
                if (stepIndex == 3) {
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onEnableSentinel,
                        modifier = Modifier.testTag("onboarding_enable_sentinel_button")
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enable Background Monitoring")
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                onboardingSteps.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == stepIndex) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (i == stepIndex) NetPulseAccent else NetPulseSurfaceVariant)
                    )
                }
            }

            Button(
                onClick = {
                    if (isLastStep) onFinish() else stepIndex++
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_next_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NetPulseAccent,
                    contentColor = Color.White
                )
            ) {
                Text(if (isLastStep) "Get Started" else "Next", fontWeight = FontWeight.Bold)
            }
        }
    }
}
