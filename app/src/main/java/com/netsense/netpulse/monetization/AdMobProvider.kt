package com.netsense.netpulse.monetization

/**
 * The real, Google Mobile Ads-backed [AdProvider]. Deliberately thin: it only decides *which*
 * ad unit ID (if any) is configured for a slot - the actual network request happens inside the
 * real `AdView` at render time (see ui/AdComponents.kt), which is how the Mobile Ads SDK is
 * designed to work and isn't something worth wrapping further for a single banner format.
 */
class AdMobProvider(
    private val bannerAdUnitIds: Map<AdSlot, String>
) : AdProvider {

    override suspend fun requestAd(slot: AdSlot): AdResult =
        if (bannerAdUnitIds[slot].isNullOrBlank()) AdResult.NoAdAvailable else AdResult.Loaded(slot)

    override fun bannerAdUnitId(slot: AdSlot): String? = bannerAdUnitIds[slot]?.takeIf { it.isNotBlank() }
}
