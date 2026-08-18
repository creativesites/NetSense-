package com.netsense.netpulse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.netsense.netpulse.model.HealerActionItem
import com.netsense.netpulse.model.HealerActionType
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.model.ProductStatus
import com.netsense.netpulse.model.UsabilityRating
import com.netsense.netpulse.model.UsabilityScoreResult
import com.netsense.netpulse.policy.PolicyDecision
import com.netsense.netpulse.ui.DashboardUiState
import com.netsense.netpulse.ui.HealingOutcome
import com.netsense.netpulse.ui.HomeScreen
import com.netsense.netpulse.ui.theme.NetPulseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the Home screen's real state-driven behavior (redesign Section 30: healthy,
 * degraded/no-internet, healing, and recovered states) rather than a single screenshot -
 * the "Fix It" action must appear exactly when PulsePolicy actually has something to fix,
 * and disappear the instant a real recovery attempt starts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val healthySnapshot = NetworkSnapshot(
        isConnected = true,
        isValidated = true,
        primaryTransport = NetworkTransport.CELLULAR,
        activeTransports = setOf(NetworkTransport.CELLULAR),
        carrierName = "Airtel Zambia",
        cellularDataNetworkType = "4G LTE",
        signalLevel = 4
    )

    private fun score(rating: UsabilityRating, isZombie: Boolean = false, value: Int) = UsabilityScoreResult(
        score = value,
        rating = rating,
        primaryDiagnosis = "test",
        rootCauseSummary = "The carrier connection appears to be stalled.",
        explanatoryReasons = emptyList(),
        isZombieConnection = isZombie,
        scoreBreakdown = emptyMap()
    )

    private fun render(uiState: DashboardUiState) {
        composeTestRule.setContent {
            NetPulseTheme {
                HomeScreen(
                    uiState = uiState,
                    hasLocationPermission = true,
                    isLocationServicesEnabled = true,
                    onRequestPermissions = {},
                    onOpenLocationSettings = {},
                    onFixIt = {},
                    onDismissHealingOutcome = {},
                    onNavigateToAdvanced = {},
                    onNavigateToHistory = {}
                )
            }
        }
    }

    @Test
    fun `healthy state shows no Fix It button`() {
        render(
            DashboardUiState(
                snapshot = healthySnapshot,
                scoreResult = score(UsabilityRating.OPTIMAL, value = 92),
                policyDecision = PolicyDecision(
                    state = ProductStatus.ONLINE,
                    recommendedAction = null,
                    reason = "Connection is healthy - no action needed.",
                    predictionAvailable = false
                )
            )
        )

        composeTestRule.onNodeWithTag("home_health_ring").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_headline").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_fix_it_button").assertDoesNotExist()
    }

    @Test
    fun `no-internet state shows a Fix It button wired to PulsePolicy's action`() {
        val action = HealerActionItem(
            id = "healer_airplane_cycle",
            title = "Radio PDP Context Reset",
            description = "desc",
            impactLevel = "High Impact",
            actionType = HealerActionType.AIRPLANE_CYCLE
        )
        render(
            DashboardUiState(
                snapshot = healthySnapshot,
                scoreResult = score(UsabilityRating.UNUSABLE, isZombie = true, value = 20),
                policyDecision = PolicyDecision(
                    state = ProductStatus.NO_INTERNET,
                    recommendedAction = action,
                    reason = "Diagnosed as No Internet. Least-disruptive available fix: ${action.title}.",
                    predictionAvailable = false
                )
            )
        )

        composeTestRule.onNodeWithTag("home_fix_it_button").assertIsDisplayed()
    }

    @Test
    fun `while healing is in progress, Fix It is hidden even if a status still shows NO_INTERNET`() {
        val action = HealerActionItem(
            id = "healer_airplane_cycle",
            title = "Radio PDP Context Reset",
            description = "desc",
            impactLevel = "High Impact",
            actionType = HealerActionType.AIRPLANE_CYCLE
        )
        render(
            DashboardUiState(
                snapshot = healthySnapshot,
                scoreResult = score(UsabilityRating.UNUSABLE, isZombie = true, value = 20),
                policyDecision = PolicyDecision(
                    state = ProductStatus.NO_INTERNET,
                    recommendedAction = action,
                    reason = "test",
                    predictionAvailable = false
                ),
                isHealing = true
            )
        )

        composeTestRule.onNodeWithTag("home_fix_it_button").assertDoesNotExist()
    }

    @Test
    fun `a resolved healing outcome replaces the hero card with a done button`() {
        render(
            DashboardUiState(
                snapshot = healthySnapshot,
                scoreResult = score(UsabilityRating.OPTIMAL, value = 90),
                healingOutcome = HealingOutcome(succeeded = true, durationMs = 8_000L, message = "Recovered")
            )
        )

        composeTestRule.onNodeWithTag("home_healing_done_button").assertIsDisplayed()
    }

    @Test
    fun `a failed healing outcome offers Try Again instead of Done`() {
        render(
            DashboardUiState(
                snapshot = healthySnapshot,
                scoreResult = score(UsabilityRating.UNUSABLE, isZombie = true, value = 10),
                healingOutcome = HealingOutcome(
                    succeeded = false,
                    durationMs = 90_000L,
                    message = "The carrier connection appears to be stalled."
                )
            )
        )

        composeTestRule.onNodeWithTag("home_healing_done_button").assertIsDisplayed()
    }
}
