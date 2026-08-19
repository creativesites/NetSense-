package com.netsense.netpulse.monetization

/**
 * Ad architecture only - no ad SDK dependency exists in this project, and this interface has
 * no implementation that ever actually returns an ad. It exists so a future ads pass has a
 * single seam (this interface) to implement against, instead of an SDK's views/APIs getting
 * wired directly into Compose screens ad hoc. See MONETIZATION.md for placement rationale.
 */
enum class AdSlot {
    /** Bottom of the History tab's incident list - a natural break after the user is done
     *  reviewing past outages, not interrupting an active diagnosis. */
    HISTORY_LIST_FOOTER,

    /** After a successful healing outcome resolves on Home - the user's problem is already
     *  solved at that point, so it doesn't block anything time-sensitive. Never shown on a
     *  failed outcome or while healing is in progress. */
    HEALING_SUCCESS_INTERSTITIAL
}

/** One placement's ad, or the deliberate absence of one - callers must handle "no ad" as the
 *  normal case, not an error, since [NoOpAdProvider] always returns it today. */
sealed class AdResult {
    data object NoAdAvailable : AdResult()
    data class Loaded(val slot: AdSlot) : AdResult()
}

interface AdProvider {
    suspend fun requestAd(slot: AdSlot): AdResult
}

/**
 * The only implementation wired into the app today. Always reports [AdResult.NoAdAvailable] -
 * there is no ad SDK integrated, so anything else would be a placeholder ad the spec explicitly
 * asked not to add. A caller that unconditionally treats "no ad" as normal (never blocking on
 * it, never showing a loading spinner waiting for one) is exactly what lets this later be
 * swapped for a real SDK-backed implementation with zero UI-side changes.
 */
class NoOpAdProvider : AdProvider {
    override suspend fun requestAd(slot: AdSlot): AdResult = AdResult.NoAdAvailable
}
