package com.netsense.netpulse

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.netsense.netpulse.monetization.AdMobProvider
import com.netsense.netpulse.monetization.AdProvider
import com.netsense.netpulse.monetization.AdSlot
import com.netsense.netpulse.monetization.EntitlementManager
import com.netsense.netpulse.monetization.FreeTierEntitlementManager

/**
 * Application-level singletons and one-time SDK initialization.
 *
 * Mobile Ads is initialized exactly once here (Application.onCreate runs once per process
 * lifetime, unlike an Activity/Composable which can be recreated many times) and
 * asynchronously - [MobileAds.initialize] takes a completion listener but does not block the
 * calling thread, so app startup is never gated on it. If it's slow or fails, the first ad
 * request simply queues behind it or comes back empty; nothing else in the app depends on ads
 * having initialized.
 */
class NetPulseApplication : Application() {

    val entitlementManager: EntitlementManager by lazy { FreeTierEntitlementManager() }

    val adProvider: AdProvider by lazy {
        AdMobProvider(
            bannerAdUnitIds = mapOf(
                AdSlot.HISTORY_LIST_FOOTER to bannerAdUnitId(),
                AdSlot.HEALING_SUCCESS_BANNER to bannerAdUnitId()
            )
        )
    }

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) { /* no-op: nothing in the app blocks on this callback */ }
    }

    /**
     * The real production ad-unit ID is only used in a non-debug build and only once it's
     * actually been supplied (via ADMOB_BANNER_AD_UNIT_ID_PROD at build time - see
     * app/build.gradle.kts). Every other case - a debug build, or a release build built before
     * that ID exists - safely falls back to Google's own public test ad unit rather than
     * loading with a blank/invalid ID.
     */
    private fun bannerAdUnitId(): String {
        val prod = BuildConfig.ADMOB_BANNER_AD_UNIT_ID_PROD
        return if (!BuildConfig.DEBUG && prod.isNotBlank()) prod else BuildConfig.ADMOB_BANNER_AD_UNIT_ID_TEST
    }
}
