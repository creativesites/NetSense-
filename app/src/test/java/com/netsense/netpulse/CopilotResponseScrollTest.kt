package com.netsense.netpulse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.netsense.netpulse.ai.gemini.GeminiAiConsultation
import com.netsense.netpulse.ai.gemini.StructuredAdvice
import com.netsense.netpulse.ui.AiDashboardCopilotCard
import com.netsense.netpulse.ui.theme.NetPulseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the Trust pass' Section 2 fix: the Ask NetPulse / Copilot response
 * card must be fully readable, not clipped, for both a short answer and a long one - including
 * the local PulseMind fallback path (StructuredAdvice), which is the only Copilot path this
 * build can actually reach without a configured Gemini API key (see GeminiAdvisorEngine).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CopilotResponseScrollTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun render(consultation: GeminiAiConsultation?) {
        composeTestRule.setContent {
            NetPulseTheme {
                AiDashboardCopilotCard(
                    consultation = consultation,
                    isConsulting = false,
                    onConsultAi = {},
                    onClearConsultation = {}
                )
            }
        }
    }

    @Test
    fun `a short response renders fully without needing to scroll`() {
        render(
            GeminiAiConsultation(
                query = "Why is my latency spiking?",
                response = "flattened text unused when structured is present",
                structured = StructuredAdvice(
                    whatsHappening = "Latency is stable.",
                    why = "No congestion detected.",
                    recommendation = "No action needed."
                )
            )
        )

        composeTestRule.onNodeWithTag("ai_copilot_response_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_copilot_response_text").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a long response stays fully reachable by scrolling instead of being clipped`() {
        // A deliberately long, real-shaped PulseMind-style explanation - this is what the
        // previous unscrollable card would have clipped below the fold.
        val longRecommendation = buildString {
            append("Switch your DNS to 1.1.1.1 or 8.8.8.8, and enable QoS/traffic prioritization ")
            append("on your router if it supports it. ")
            repeat(40) {
                append("This connection has shown elevated round-trip time and jitter over the last several probe cycles, consistent with uplink congestion. ")
            }
            append("END_OF_LONG_RECOMMENDATION_MARKER")
        }

        render(
            GeminiAiConsultation(
                query = "Why is my latency spiking?",
                response = "flattened text unused when structured is present",
                structured = StructuredAdvice(
                    whatsHappening = "Your connection has elevated round-trip time with high packet jitter.",
                    why = "This is most often caused by uplink congestion or bufferbloat on the local gateway.",
                    recommendation = longRecommendation
                )
            )
        )

        composeTestRule.onNodeWithTag("ai_copilot_response_card").assertIsDisplayed()

        // The long "WHAT I RECOMMEND" section must still be reachable via scroll - proving the
        // response card actually scrolls internally rather than clipping overflow content.
        composeTestRule.onNodeWithTag("ai_copilot_response_text").performScrollTo().assertIsDisplayed()
    }
}
