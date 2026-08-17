package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.model.DiagnosticMode
import com.example.model.DiagnosticProbeResult
import com.example.model.EndpointProbeDetail
import com.example.model.NetworkClassification
import com.example.model.NetworkSnapshot
import com.example.model.NetworkTransport
import com.example.model.UsabilityRating
import com.example.model.UsabilityScoreResult
import com.example.telephony.TelephonySnapshot
import com.example.ui.DashboardTab
import com.example.ui.DashboardUiState
import com.example.ui.NetPulseDashboard
import com.example.ui.theme.NetPulseTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleState = DashboardUiState(
        snapshot = NetworkSnapshot(
            isConnected = true,
            isValidated = true,
            isMetered = true,
            primaryTransport = NetworkTransport.CELLULAR,
            activeTransports = setOf(NetworkTransport.CELLULAR),
            downstreamBandwidthKbps = 45000,
            upstreamBandwidthKbps = 15000,
            interfaceName = "rmnet0",
            dnsServers = listOf("8.8.8.8", "1.1.1.1"),
            carrierName = "Airtel Zambia",
            cellularDataNetworkType = "4G LTE",
            signalLevel = 4,
            signalDbm = -85,
            classification = NetworkClassification.INTERNET_OPTIMAL
        ),
        telephony = TelephonySnapshot(
            carrierName = "Airtel Zambia",
            networkType = "4G LTE",
            signalLevel = 4,
            signalDbm = -85,
            isSimReady = true
        ),
        probeResult = DiagnosticProbeResult(
            mode = DiagnosticMode.STANDARD,
            primaryEndpoint = EndpointProbeDetail(
                endpointHost = "connectivitycheck.gstatic.com",
                ipAddress = "142.250.180.3",
                dnsLookupMs = 38L,
                dnsSuccess = true,
                tcpHandshakeMs = 54L,
                tcpSuccess = true,
                httpStatusCode = 204,
                httpLatencyMs = 95L,
                httpSuccess = true
            ),
            secondaryEndpoint = EndpointProbeDetail(
                endpointHost = "cloudflare.com",
                ipAddress = "104.16.132.229",
                dnsLookupMs = 32L,
                dnsSuccess = true,
                tcpHandshakeMs = 48L,
                tcpSuccess = true,
                httpStatusCode = 204,
                httpLatencyMs = 88L,
                httpSuccess = true
            ),
            tcpJitterMs = 6L,
            packetLossPct = 0f,
            totalProbesSent = 3,
            totalProbesReceived = 3
        ),
        scoreResult = UsabilityScoreResult(
            score = 94,
            rating = UsabilityRating.OPTIMAL,
            primaryDiagnosis = "Optimal Internet Performance: Rapid DNS, low TCP RTT, and high end-to-end responsiveness.",
            rootCauseSummary = "All network layers operating normally.",
            explanatoryReasons = listOf(
                "✓ Android OS validated active Internet connectivity",
                "✓ DNS resolved in 38ms",
                "✓ TCP handshake established in 54ms",
                "✓ HTTPS 204 verified in 95ms",
                "✓ Zero packet loss detected across test sockets"
            ),
            isZombieConnection = false,
            scoreBreakdown = mapOf(
                "Platform Validation" to 15,
                "Radio Signal Quality" to 10,
                "DNS Resolution" to 20,
                "TCP Handshake" to 20,
                "HTTP Response" to 20,
                "Packet Reliability" to 15
            )
        )
    )

    composeTestRule.setContent {
      NetPulseTheme {
        NetPulseDashboard(
            uiState = sampleState,
            onRunProbe = {},
            onSelectTab = {},
            onSelectMode = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
