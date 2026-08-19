package com.netsense.netpulse.monetization

import com.netsense.netpulse.ui.DashboardUiState

/**
 * Ad architecture - the app depends on [AdProvider], never on the Google Mobile Ads SDK
 * directly from a Composable. [NoOpAdProvider] is the null implementation (used in tests and
 * as a safe default); [AdMobProvider] is the real, SDK-backed one. See MONETIZATION.md for
 * placement rationale.
 */
enum class AdSlot {
    /** Bottom of the History tab's incident list - a natural break after the user is done
     *  reviewing past outages, not interrupting an active diagnosis. */
    HISTORY_LIST_FOOTER,

    /** After a successful healing outcome resolves on Home - the user's problem is already
     *  solved at that point, so it doesn't block anything time-sensitive. A banner, not an
     *  interstitial - never full-screen, and never shown on a failed/pending outcome or while
     *  healing is in progress (see [isAdEligible]). */
    HEALING_SUCCESS_BANNER
}

/** One placement's ad, or the deliberate absence of one - callers must handle "no ad" as the
 *  normal case, not an error, since [NoOpAdProvider] always returns it and even [AdMobProvider]
 *  returns it for any slot with no configured ad unit. */
sealed class AdResult {
    data object NoAdAvailable : AdResult()
    data class Loaded(val slot: AdSlot) : AdResult()
}

interface AdProvider {
    suspend fun requestAd(slot: AdSlot): AdResult

    /** The real ad-network unit ID to load for [slot], or null if this provider has nothing
     *  configured for it. Only meaningful after [requestAd] reports [AdResult.Loaded] - this is
     *  what the actual rendering layer (AdBannerView) uses to construct the SDK's AdView. */
    fun bannerAdUnitId(slot: AdSlot): String?
}

/**
 * The null implementation - always reports [AdResult.NoAdAvailable] and null for any ad unit
 * ID. Used in tests, and as a safe default anywhere an [AdProvider] isn't otherwise supplied.
 */
class NoOpAdProvider : AdProvider {
    override suspend fun requestAd(slot: AdSlot): AdResult = AdResult.NoAdAvailable
    override fun bannerAdUnitId(slot: AdSlot): String? = null
}

/**
 * Whether [slot] is currently allowed to show an ad at all, independent of whether the
 * [AdProvider] actually has one - a pure, unit-tested decision so "no ads during
 * diagnostics/healing" is enforced once here rather than re-checked ad hoc at each call site.
 *
 * History has no diagnostic/healing state to conflict with, so it's always eligible. The
 * post-healing banner is eligible only once healing has genuinely resolved successfully -
 * never while pending, never on a failure, and never made to look like the success state
 * itself is uncertain.
 */
fun isAdEligible(slot: AdSlot, uiState: DashboardUiState): Boolean = when (slot) {
    AdSlot.HISTORY_LIST_FOOTER -> true
    AdSlot.HEALING_SUCCESS_BANNER -> !uiState.isHealing && uiState.healingOutcome?.succeeded == true
}
