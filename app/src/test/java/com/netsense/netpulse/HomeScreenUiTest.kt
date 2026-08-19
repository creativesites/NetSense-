package com.netsense.netpulse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    private fun render(
        uiState: DashboardUiState,
        onRetryFix: () -> Unit = {},
        onDismissHealingOutcome: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            NetPulseTheme {
                HomeScreen(
                    uiState = uiState,
                    hasLocationPermission = true,
                    isLocationServicesEnabled = true,
                    onRequestPermissions = {},
                    onOpenLocationSettings = {},
                    onFixIt = {},
                    onRetryFix = onRetryFix,
                    onDismissHealingOutcome = onDismissHealingOutcome,
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
    fun `a successful outcome's Done button dismisses without retrying`() {
        var dismissed = false
        var retried = false
        render(
            DashboardUiState(
                snapshot = healthySnapshot,
                scoreResult = score(UsabilityRating.OPTIMAL, value = 90),
                healingOutcome = HealingOutcome(succeeded = true, durationMs = 8_000L, message = "Recovered")
            ),
            onRetryFix = { retried = true },
            onDismissHealingOutcome = { dismissed = true }
        )

        composeTestRule.onNodeWithTag("home_healing_done_button").assertIsDisplayed().performClick()

        assert(dismissed) { "expected Done to dismiss the outcome" }
        assert(!retried) { "Done must not trigger a retry" }
    }

    private fun failedOutcomeState() = DashboardUiState(
        snapshot = healthySnapshot,
        scoreResult = score(UsabilityRating.UNUSABLE, isZombie = true, value = 10),
        healingOutcome = HealingOutcome(
            succeeded = false,
            durationMs = 45_000L,
            message = "The carrier connection appears to be stalled."
        )
    )

    @Test
    fun `a failed outcome's Try Again button retries immediately rather than just dismissing`() {
        var dismissed = false
        var retried = false
        render(
            failedOutcomeState(),
            onRetryFix = { retried = true },
            onDismissHealingOutcome = { dismissed = true }
        )

        // The primary button on a failure must re-trigger the fix, not just return the user
        // to the hero card (the exact regression reported: "try again is going straight to
        // the network info" instead of retrying).
        composeTestRule.onNodeWithTag("home_healing_done_button").assertIsDisplayed().performClick()
        assert(retried) { "expected Try Again to retry the fix" }
        assert(!dismissed) { "Try Again must not merely dismiss" }
    }

    @Test
    fun `a failed outcome's separate Dismiss link dismisses without retrying`() {
        var dismissed = false
        var retried = false
        render(
            failedOutcomeState(),
            onRetryFix = { retried = true },
            onDismissHealingOutcome = { dismissed = true }
        )

        // The dismiss link sits below the fold in this test's viewport - scroll the LazyColumn
        // to it before clicking, same as a real user would.
        composeTestRule.onNodeWithTag("home_healing_dismiss_button").performScrollTo().performClick()
        assert(dismissed) { "expected the dismiss link to dismiss the outcome" }
        assert(!retried) { "Dismiss must not trigger a retry" }
    }

    @Test
    fun `the healing outcome card shows carrier and network info, not just the result`() {
        render(
            DashboardUiState(
                snapshot = healthySnapshot,
                scoreResult = score(UsabilityRating.OPTIMAL, value = 90),
                healingOutcome = HealingOutcome(succeeded = true, durationMs = 8_000L, message = "Recovered")
            )
        )

        composeTestRule.onNodeWithText("Airtel Zambia · 4G LTE", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an active healing session shows a live progress ticker, not a frozen message`() {
        val action = HealerActionItem(
            id = "healer_airplane_cycle",
            title = "Radio PDP Context Reset",
            description = "Cellular signal is strong but upstream routing is dead.",
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

        composeTestRule.onNodeWithTag("home_healing_ticker").assertIsDisplayed()
        composeTestRule.onNodeWithText(action.description, substring = true).assertIsDisplayed()
    }
}
