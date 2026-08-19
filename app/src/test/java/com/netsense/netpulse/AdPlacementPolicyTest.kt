package com.netsense.netpulse

import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.ProductStatus
import com.netsense.netpulse.model.UsabilityRating
import com.netsense.netpulse.model.UsabilityScoreResult
import com.netsense.netpulse.monetization.AdMobProvider
import com.netsense.netpulse.monetization.AdResult
import com.netsense.netpulse.monetization.AdSlot
import com.netsense.netpulse.monetization.isAdEligible
import com.netsense.netpulse.policy.PolicyDecision
import com.netsense.netpulse.ui.DashboardUiState
import com.netsense.netpulse.ui.HealingOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ad placements are hard requirements, not preferences: no ads during an active diagnosis or
 * healing attempt, no ads on a failed or still-pending outcome, and the post-healing banner
 * only once a real success has resolved. Tested at the AdProvider/isAdEligible boundary per
 * spec - never against the real ad network.
 */
class AdPlacementPolicyTest {

    private fun idleState(healingOutcome: HealingOutcome? = null, isHealing: Boolean = false) = DashboardUiState(
        snapshot = NetworkSnapshot(),
        scoreResult = UsabilityScoreResult(
            score = 90,
            rating = UsabilityRating.OPTIMAL,
            primaryDiagnosis = "test",
            rootCauseSummary = "test",
            explanatoryReasons = emptyList(),
            isZombieConnection = false,
            scoreBreakdown = emptyMap()
        ),
        policyDecision = PolicyDecision(
            state = ProductStatus.ONLINE,
            recommendedAction = null,
            reason = "test",
            predictionAvailable = false
        ),
        isHealing = isHealing,
        healingOutcome = healingOutcome
    )

    @Test
    fun `History footer is always ad-eligible - no diagnostic or healing state to conflict with`() {
        assertTrue(isAdEligible(AdSlot.HISTORY_LIST_FOOTER, idleState()))
        assertTrue(isAdEligible(AdSlot.HISTORY_LIST_FOOTER, idleState(isHealing = true)))
    }

    @Test
    fun `post-healing banner is ineligible while healing is actively in progress`() {
        val state = idleState(isHealing = true, healingOutcome = null)
        assertFalse(isAdEligible(AdSlot.HEALING_SUCCESS_BANNER, state))
    }

    @Test
    fun `post-healing banner is ineligible when there is no resolved outcome yet`() {
        val state = idleState(isHealing = false, healingOutcome = null)
        assertFalse(isAdEligible(AdSlot.HEALING_SUCCESS_BANNER, state))
    }

    @Test
    fun `post-healing banner is ineligible on a failed healing outcome`() {
        val failed = HealingOutcome(succeeded = false, durationMs = 45_000L, message = "still broken")
        val state = idleState(isHealing = false, healingOutcome = failed)
        assertFalse(isAdEligible(AdSlot.HEALING_SUCCESS_BANNER, state))
    }

    @Test
    fun `post-healing banner is eligible only once healing has genuinely resolved successfully`() {
        val succeeded = HealingOutcome(succeeded = true, durationMs = 8_000L, message = "Recovered")
        val state = idleState(isHealing = false, healingOutcome = succeeded)
        assertTrue(isAdEligible(AdSlot.HEALING_SUCCESS_BANNER, state))
    }

    @Test
    fun `AdMobProvider reports NoAdAvailable and a null unit id for an unconfigured slot`() = runBlocking {
        val provider = AdMobProvider(bannerAdUnitIds = mapOf(AdSlot.HISTORY_LIST_FOOTER to "test-unit-id"))
        assertEquals(AdResult.NoAdAvailable, provider.requestAd(AdSlot.HEALING_SUCCESS_BANNER))
        assertEquals(null, provider.bannerAdUnitId(AdSlot.HEALING_SUCCESS_BANNER))
    }

    @Test
    fun `AdMobProvider reports Loaded and the real unit id for a configured slot`() = runBlocking {
        val provider = AdMobProvider(bannerAdUnitIds = mapOf(AdSlot.HISTORY_LIST_FOOTER to "test-unit-id"))
        assertEquals(AdResult.Loaded(AdSlot.HISTORY_LIST_FOOTER), provider.requestAd(AdSlot.HISTORY_LIST_FOOTER))
        assertEquals("test-unit-id", provider.bannerAdUnitId(AdSlot.HISTORY_LIST_FOOTER))
    }

    @Test
    fun `AdMobProvider never treats a blank configured id as a real ad - avoids loading with an invalid unit id`() = runBlocking {
        val provider = AdMobProvider(bannerAdUnitIds = mapOf(AdSlot.HISTORY_LIST_FOOTER to ""))
        assertEquals(AdResult.NoAdAvailable, provider.requestAd(AdSlot.HISTORY_LIST_FOOTER))
        assertEquals(null, provider.bannerAdUnitId(AdSlot.HISTORY_LIST_FOOTER))
    }
}
