package com.netsense.netpulse.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build as BuildIcon
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
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
import com.netsense.netpulse.ui.theme.NetPulseSurface
import com.netsense.netpulse.ui.theme.NetPulseSurfaceVariant
import com.netsense.netpulse.ui.theme.NetPulseTextPrimary
import com.netsense.netpulse.ui.theme.NetPulseTextSecondary
import com.netsense.netpulse.ui.theme.NetPulseTextTertiary
import com.netsense.netpulse.ui.theme.StatusOptimal
import com.netsense.netpulse.ui.theme.StatusOptimalBg

/** Which special, non-generic content (if any) a step renders below its title/body. */
private enum class OnboardingPageType { WELCOME, HEALTH, DIAGNOSTICS, SENTINEL, PERMISSIONS, READY }

private data class OnboardingStep(
    val type: OnboardingPageType,
    val icon: ImageVector,
    val title: String,
    val body: String
)

/**
 * The first-run tour (Trust pass, Sections 5-7): explain what NetPulse actually does and why it
 * asks for what it asks for, before asking for it. Six screens, each answering one question a
 * new user actually has - not a feature tour, and never longer than a phone screen needs it to
 * be. Sentinel gets its own dedicated, prominent screen (Section 5: "actively encourage
 * Sentinel") and Location gets an explicit, code-accurate explanation of what it's really for
 * (Section 6) - nothing here claims more than [com.netsense.netpulse.engine.RadarEngine] and
 * [com.netsense.netpulse.service.NetPulseSentinelService] actually do.
 */
private val onboardingSteps = listOf(
    OnboardingStep(
        type = OnboardingPageType.WELCOME,
        icon = Icons.Default.NetworkCheck,
        title = "Meet NetPulse",
        body = "Your phone can say it's \"connected\" while the Internet is actually broken. " +
            "NetPulse checks what's really happening on your connection and helps you understand it."
    ),
    OnboardingStep(
        type = OnboardingPageType.HEALTH,
        icon = Icons.Default.SignalCellularAlt,
        title = "See your real connection health",
        body = "NetPulse gives your connection a live 0-100 health score, built from real checks - " +
            "not just signal bars - along with your connection status, network type, and recent history."
    ),
    OnboardingStep(
        type = OnboardingPageType.DIAGNOSTICS,
        icon = Icons.Default.BuildIcon,
        title = "Diagnose & fix",
        body = "Run a deeper diagnostic any time something feels wrong. NetPulse identifies the likely " +
            "problem and, when Android allows it, can guide you through fixing the connection. " +
            "Not every problem can be fixed automatically - but you'll always know what's going on."
    ),
    OnboardingStep(
        type = OnboardingPageType.SENTINEL,
        icon = Icons.Default.Security,
        title = "Keep NetPulse watching",
        body = "Sentinel monitors your connection in the background and lets you know when your " +
            "network needs attention - even when NetPulse isn't open."
    ),
    OnboardingStep(
        type = OnboardingPageType.PERMISSIONS,
        icon = Icons.Default.CellTower,
        title = "Your permissions",
        body = "NetPulse only asks for what it actually uses. Here's exactly why."
    ),
    OnboardingStep(
        type = OnboardingPageType.READY,
        icon = Icons.Default.CheckCircle,
        title = "You're ready",
        body = "NetPulse is ready to help you understand your connection."
    )
)

@Composable
fun OnboardingScreen(
    isSentinelEnabled: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
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
                // Section 4 fix: safeDrawing covers status bar, nav bar (gesture or 3-button)
                // and display cutouts on every device, so the bottom CTA can never end up drawn
                // under a system bar the way a fixed padding value could on a taller/shorter
                // device - this is the one modifier that adapts on its own instead of a magic dp.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
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

            // A large system font or a small screen can make any one step - especially the
            // permissions step's two explanation cards - taller than the space between the Skip
            // row and the bottom button; this scrolls instead of clipping or overlapping either.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                if (stepIndex == 0) {
                    // The real app icon on the welcome step only - every later step keeps its
                    // own contextual vector icon, but this is the one place onboarding should
                    // actually show the NetPulse brand mark.
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
                            .background(
                                if (step.type == OnboardingPageType.SENTINEL) StatusOptimalBg else NetPulseAccentContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = step.icon,
                            contentDescription = null,
                            tint = if (step.type == OnboardingPageType.SENTINEL) StatusOptimal else NetPulseOnAccentContainer,
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
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("onboarding_title")
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

                when (step.type) {
                    OnboardingPageType.SENTINEL -> SentinelCallout(
                        isEnabled = isSentinelEnabled,
                        onEnable = onEnableSentinel
                    )
                    OnboardingPageType.PERMISSIONS -> PermissionsExplanation(
                        onRequestLocationPermission = onRequestLocationPermission,
                        onRequestNotificationPermission = onRequestNotificationPermission
                    )
                    OnboardingPageType.READY -> if (!isSentinelEnabled) {
                        ReadyScreenSentinelReminder(onEnable = onEnableSentinel)
                    }
                    else -> {}
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 20.dp)
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
                    .height(52.dp)
                    .testTag("onboarding_next_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NetPulseAccent,
                    contentColor = Color.White
                )
            ) {
                Text(
                    when {
                        stepIndex == 0 -> "Get Started"
                        isLastStep -> "Open NetPulse"
                        else -> "Next"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * The onboarding's strongest screen (Section 5: "actively encourage Sentinel"), a value-based
 * pitch rather than a bare toggle - explains what background monitoring actually does before
 * asking the user to turn it on, and never enables it silently on its own.
 */
@Composable
private fun SentinelCallout(isEnabled: Boolean, onEnable: () -> Unit) {
    Spacer(modifier = Modifier.height(20.dp))

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StatusOptimalBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Watches your connection in the background",
                "Lets you know the moment your network needs attention",
                "Can offer Fix It right from the alert when a fix is available"
            ).forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusOptimal,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = line,
                        fontSize = 13.sp,
                        color = NetPulseTextPrimary,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = "It's entirely optional, and you're always in control - turn it off anytime in Settings.",
        fontSize = 12.sp,
        color = NetPulseTextTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    if (isEnabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = StatusOptimal, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Sentinel is on", fontWeight = FontWeight.Bold, color = StatusOptimal)
        }
    } else {
        Button(
            onClick = onEnable,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StatusOptimal, contentColor = Color.White),
            modifier = Modifier
                .height(48.dp)
                .testTag("onboarding_enable_sentinel_button")
        ) {
            Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enable Sentinel", fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Section 6: an accurate, plain-language explanation of Location permission, matching what
 * [com.netsense.netpulse.engine.RadarEngine] actually reads with it - cellular RF fields
 * (RSRP/RSRQ/SINR/CQI, cell/tower identity) and Wi-Fi network detail - and never claiming this
 * app tracks the user's physical location, because it doesn't.
 */
@Composable
private fun PermissionsExplanation(
    onRequestLocationPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    Spacer(modifier = Modifier.height(20.dp))

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PermissionExplanationCard(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            explanation = "Used to keep you informed about network problems and Sentinel's status - " +
                "nothing else.",
            buttonLabel = "Grant Notification Permission",
            onGrant = onRequestNotificationPermission,
            testTag = "onboarding_grant_notifications_button"
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    PermissionExplanationCard(
        icon = Icons.Default.LocationOn,
        title = "Location",
        explanation = "Android protects some Wi-Fi and cellular radio details behind Location " +
            "permission. NetPulse uses this access for network diagnostics - things like signal " +
            "strength (RSRP/RSRQ/SINR) and which cell tower or Wi-Fi network you're on.",
        emphasis = "NetPulse does not use Location access to track where you are.",
        buttonLabel = "Grant Location Permission",
        onGrant = onRequestLocationPermission,
        testTag = "onboarding_grant_permissions_button"
    )
}

@Composable
private fun PermissionExplanationCard(
    icon: ImageVector,
    title: String,
    explanation: String,
    buttonLabel: String,
    onGrant: () -> Unit,
    testTag: String,
    emphasis: String? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NetPulseSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = NetPulseAccent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NetPulseTextPrimary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = explanation,
                fontSize = 12.sp,
                color = NetPulseTextSecondary,
                lineHeight = 17.sp,
                textAlign = TextAlign.Start
            )
            if (emphasis != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = emphasis,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NetPulseTextPrimary,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Start
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onGrant,
                modifier = Modifier.testTag(testTag)
            ) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(buttonLabel, fontSize = 13.sp)
            }
        }
    }
}

/** The final screen never traps a user who skipped Sentinel earlier - a clear, low-pressure way
 *  to turn it on right before entering the app, or they can just as easily tap past it. */
@Composable
private fun ReadyScreenSentinelReminder(onEnable: () -> Unit) {
    Spacer(modifier = Modifier.height(20.dp))
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NetPulseSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Background monitoring is still off",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = NetPulseTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "You can turn Sentinel on now, or anytime later from Settings.",
                fontSize = 12.sp,
                color = NetPulseTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onEnable,
                modifier = Modifier.testTag("onboarding_ready_enable_sentinel_button")
            ) {
                Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Enable Sentinel", fontSize = 13.sp)
            }
        }
    }
}
