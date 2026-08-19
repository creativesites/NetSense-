package com.netsense.netpulse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.netsense.netpulse.ui.OnboardingScreen
import com.netsense.netpulse.ui.theme.NetPulseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the Trust pass' onboarding rework (Sections 4-7): the redesigned
 * six-screen tour must let a user reach the end via the primary button at every step (Section 4:
 * the button must always be present and clickable, never lost below a system inset), must
 * actively surface the Sentinel opt-in (Section 5), and must explain Location accurately without
 * ever silently enabling anything on the user's behalf (Section 6).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun render(
        isSentinelEnabled: Boolean = false,
        onEnableSentinel: () -> Unit = {},
        onFinish: () -> Unit = {},
        onRequestLocationPermission: () -> Unit = {},
        onRequestNotificationPermission: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            NetPulseTheme {
                OnboardingScreen(
                    isSentinelEnabled = isSentinelEnabled,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onEnableSentinel = onEnableSentinel,
                    onFinish = onFinish
                )
            }
        }
    }

    @Test
    fun `the welcome screen shows Meet NetPulse with a Get Started CTA`() {
        render()
        composeTestRule.onNodeWithText("Meet NetPulse").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_next_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Get Started").assertIsDisplayed()
    }

    @Test
    fun `the primary button is present and clickable on every step through to the end`() {
        var finished = false
        render(onFinish = { finished = true })

        // 6 screens total: tap through all of them via the always-visible primary button.
        repeat(6) {
            composeTestRule.onNodeWithTag("onboarding_next_button").assertIsDisplayed().performClick()
        }

        assert(finished) { "expected the final tap to call onFinish" }
    }

    @Test
    fun `Skip immediately finishes onboarding regardless of which step is showing`() {
        var finished = false
        render(onFinish = { finished = true })

        composeTestRule.onNodeWithTag("onboarding_skip_button").assertIsDisplayed().performClick()

        assert(finished) { "expected Skip to call onFinish" }
    }

    @Test
    fun `the Sentinel screen actively offers to enable it, not a silent default`() {
        var enabled = false
        render(isSentinelEnabled = false, onEnableSentinel = { enabled = true })

        // Step through to the Sentinel screen (index 3): Welcome -> Health -> Diagnostics -> Sentinel
        repeat(3) { composeTestRule.onNodeWithTag("onboarding_next_button").performClick() }

        composeTestRule.onNodeWithText("Keep NetPulse watching").assertIsDisplayed()
        // The Sentinel CTA sits below the intro copy inside the step's scrollable content -
        // same pattern HomeScreenUiTest uses for below-the-fold nodes.
        composeTestRule.onNodeWithTag("onboarding_enable_sentinel_button").performScrollTo().assertIsDisplayed().performClick()
        assert(enabled) { "expected tapping Enable Sentinel to invoke onEnableSentinel" }
    }

    @Test
    fun `when Sentinel is already enabled the screen confirms it rather than asking again`() {
        render(isSentinelEnabled = true)

        repeat(3) { composeTestRule.onNodeWithTag("onboarding_next_button").performClick() }

        composeTestRule.onNodeWithText("Sentinel is on").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_enable_sentinel_button").assertDoesNotExist()
    }

    @Test
    fun `the permissions screen explains Location without ever claiming location tracking`() {
        render()
        repeat(4) { composeTestRule.onNodeWithTag("onboarding_next_button").performClick() }

        composeTestRule.onNodeWithText("Your permissions").assertIsDisplayed()
        composeTestRule.onNodeWithText("does not use Location access to track", substring = true)
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_grant_permissions_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `granting location permission from onboarding invokes the real permission request`() {
        var requested = false
        render(onRequestLocationPermission = { requested = true })
        repeat(4) { composeTestRule.onNodeWithTag("onboarding_next_button").performClick() }

        composeTestRule.onNodeWithTag("onboarding_grant_permissions_button").performScrollTo().performClick()
        assert(requested) { "expected the Location grant button to invoke onRequestLocationPermission" }
    }

    @Test
    fun `declining Sentinel never blocks reaching the final ready screen`() {
        var finished = false
        render(isSentinelEnabled = false, onFinish = { finished = true })

        // Never tap Enable Sentinel - just advance through every step.
        repeat(5) { composeTestRule.onNodeWithTag("onboarding_next_button").performClick() }
        composeTestRule.onNodeWithText("You're ready").assertIsDisplayed()

        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        assert(finished) { "declining Sentinel must not prevent finishing onboarding" }
    }

    @Test
    fun `the ready screen offers a non-trapping way to enable Sentinel later if it was skipped`() {
        render(isSentinelEnabled = false)
        repeat(5) { composeTestRule.onNodeWithTag("onboarding_next_button").performClick() }

        composeTestRule.onNodeWithText("You're ready").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_ready_enable_sentinel_button").performScrollTo().assertIsDisplayed()
        // Still finishable without touching it.
        composeTestRule.onNodeWithText("Open NetPulse").assertIsDisplayed()
    }

    @Test
    fun `the ready screen has no Sentinel reminder when it's already enabled`() {
        render(isSentinelEnabled = true)
        repeat(5) { composeTestRule.onNodeWithTag("onboarding_next_button").performClick() }

        composeTestRule.onNodeWithTag("onboarding_ready_enable_sentinel_button").assertDoesNotExist()
    }
}
