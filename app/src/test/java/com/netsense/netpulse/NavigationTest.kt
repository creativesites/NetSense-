package com.netsense.netpulse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.netsense.netpulse.ui.DashboardTab
import com.netsense.netpulse.ui.DashboardUiState
import com.netsense.netpulse.ui.NetPulseDashboard
import com.netsense.netpulse.ui.PrimaryTab
import com.netsense.netpulse.ui.theme.NetPulseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Confirms the consumer redesign's 4-tab shell (Home/History/Advanced/Settings) actually
 * navigates, and - critically for redesign Section 29 ("do not remove advanced power") -
 * that every existing engineering screen is still reachable through Advanced. Hoists its own
 * tab-selection state (mirroring what NetPulseViewModel does in the real app) so clicks
 * actually recompose, rather than passing a single static DashboardUiState.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderInteractiveDashboard() {
        composeTestRule.setContent {
            NetPulseTheme {
                var primaryTab by remember { mutableStateOf(PrimaryTab.HOME) }
                var tab by remember { mutableStateOf(DashboardTab.DIAGNOSTICS) }
                NetPulseDashboard(
                    uiState = DashboardUiState(selectedPrimaryTab = primaryTab, selectedTab = tab),
                    onRunProbe = {},
                    onSelectTab = { tab = it },
                    onSelectPrimaryTab = { primaryTab = it },
                    onSelectMode = {}
                )
            }
        }
    }

    @Test
    fun `bottom nav has exactly the four consumer-facing primary tabs`() {
        renderInteractiveDashboard()

        composeTestRule.onNodeWithTag("primary_bottom_nav").assertIsDisplayed()
        composeTestRule.onNodeWithTag("primary_tab_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("primary_tab_history").assertIsDisplayed()
        composeTestRule.onNodeWithTag("primary_tab_advanced").assertIsDisplayed()
        composeTestRule.onNodeWithTag("primary_tab_settings").assertIsDisplayed()
    }

    @Test
    fun `default landing screen is Home, not a technical dashboard`() {
        renderInteractiveDashboard()

        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun `Advanced tab still exposes every existing engineering screen`() {
        renderInteractiveDashboard()

        composeTestRule.onNodeWithTag("primary_tab_advanced").performClick()

        composeTestRule.onNodeWithTag("tab_diagnostics").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_radar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_speed_test").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_healer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_analytics").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_copilot").assertIsDisplayed()
    }

    @Test
    fun `History tab renders without crashing on an empty log history`() {
        renderInteractiveDashboard()

        composeTestRule.onNodeWithTag("primary_tab_history").performClick()

        composeTestRule.onNodeWithTag("history_empty_state").assertIsDisplayed()
    }
}
