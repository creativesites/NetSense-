package com.netsense.netpulse.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.netsense.netpulse.monetization.AdProvider
import com.netsense.netpulse.monetization.AdSlot

/**
 * Renders a real anchored-adaptive banner for [slot] via [adProvider] - or renders nothing at
 * all, cleanly, if the provider has no ad unit configured for it or the ad fails to load. A
 * missing/failed ad is never an error state here, just an absence: nothing else on screen
 * (diagnostics, healing, Sentinel monitoring) depends on this succeeding, and callers are
 * expected to have already checked [com.netsense.netpulse.monetization.isAdEligible] before
 * placing this in the tree at all (e.g. never during an active diagnosis or healing attempt).
 */
@Composable
fun AdBannerView(
    slot: AdSlot,
    adProvider: AdProvider,
    modifier: Modifier = Modifier
) {
    val unitId = remember(slot, adProvider) { adProvider.bannerAdUnitId(slot) } ?: return
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    var failed by remember(slot) { mutableStateOf(false) }

    // Disappears cleanly rather than leaving a blank reserved box once we know there's
    // nothing to show - the brief reserved height *before* that (while the request is still
    // in flight) is the intended behavior of an "anchored" banner, sized up front specifically
    // to avoid a layout jump if/when it loads.
    if (failed) return

    Box(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, screenWidthDp))
                    adUnitId = unitId
                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            failed = true
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
