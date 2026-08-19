package com.netsense.netpulse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.netsense.netpulse.model.DiagnosticProbeResult
import com.netsense.netpulse.model.HealerActionItem
import com.netsense.netpulse.model.HealerActionType
import com.netsense.netpulse.model.ProductStatus
import com.netsense.netpulse.model.UsabilityRating
import com.netsense.netpulse.model.UsabilityScoreResult
import com.netsense.netpulse.policy.PolicyDecision
import com.netsense.netpulse.ui.DashboardUiState
import com.netsense.netpulse.ui.DiagnosticsTabContent
import com.netsense.netpulse.ui.theme.NetPulseTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the Diagnostics hero's three real states (idle/running/result) - the "CleanMyMac
 * style" redesign's friendly front door - rather than a single screenshot. Heal Connection
 * must appear exactly when PulsePolicy actually has a recommended action, same invariant as
 * Home's Fix It button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiagnosticsHeroCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun score(rating: UsabilityRating, value: Int) = UsabilityScoreResult(
        score = value,
        rating = rating,
        primaryDiagnosis = "test diagnosis",
        rootCauseSummary = "test",
        explanatoryReasons = emptyList(),
        isZombieConnection = false,
        scoreBreakdown = emptyMap()
    )

    private fun render(uiState: DashboardUiState, onHealConnection: () -> Unit = {}) {
        composeTestRule.setContent {
            NetPulseTheme {
                DiagnosticsTabContent(
                    uiState = uiState,
                    onSelectMode = {},
                    onRunProbe = {},
                    onRunPingMatrix = {},
                    onRunDualStackCheck = {},
                    onRunHopTrace = {},
                    onSelectCategoryFilter = {},
                    onHealConnection = onHealConnection
                )
            }
        }
    }

    @Test
    fun `idle state (nothing run yet) shows the Run Diagnostic button`() {
        render(DashboardUiState(probeResult = null, isProbing = false))
        composeTestRule.onNodeWithTag("run_diagnostics_button").assertIsDisplayed()
    }

    @Test
    fun `running state shows the animated checklist, not a plain spinner`() {
        render(DashboardUiState(probeResult = null, isProbing = true))
        composeTestRule.onNodeWithTag("diagnostics_running_checklist").assertIsDisplayed()
    }

    @Test
    fun `a healthy result shows no Heal Connection button`() {
        render(
            DashboardUiState(
                probeResult = DiagnosticProbeResult(),
                isProbing = false,
                scoreResult = score(UsabilityRating.OPTIMAL, 92),
                policyDecision = PolicyDecision(
                    state = ProductStatus.ONLINE,
                    recommendedAction = null,
                    reason = "Connection is healthy - no action needed.",
                    predictionAvailable = false
                )
            )
        )
        composeTestRule.onNodeWithTag("diagnostics_result_headline").assertIsDisplayed()
        composeTestRule.onNodeWithTag("diagnostics_heal_connection_button").assertDoesNotExist()
    }

    @Test
    fun `an unhealthy result with a real recommended action shows Heal Connection wired to it`() {
        var healed = false
        val action = HealerActionItem(
            id = "healer_airplane_cycle",
            title = "Radio PDP Context Reset",
            description = "desc",
            impactLevel = "High Impact",
            actionType = HealerActionType.AIRPLANE_CYCLE
        )
        render(
            DashboardUiState(
                probeResult = DiagnosticProbeResult(),
                isProbing = false,
                scoreResult = score(UsabilityRating.UNUSABLE, 10),
                policyDecision = PolicyDecision(
                    state = ProductStatus.NO_INTERNET,
                    recommendedAction = action,
                    reason = "test",
                    predictionAvailable = false
                )
            ),
            onHealConnection = { healed = true }
        )
        composeTestRule.onNodeWithTag("diagnostics_heal_connection_button").assertIsDisplayed().performClick()
        assertFalse("sanity: click handler must actually run", !healed)
    }

    @Test
    fun `technical details are hidden by default and reveal on toggle`() {
        render(DashboardUiState(probeResult = null, isProbing = false))
        composeTestRule.onNodeWithTag("ping_matrix_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("toggle_technical_details").performClick()
        composeTestRule.onNodeWithTag("ping_matrix_card").assertIsDisplayed()
    }
}
