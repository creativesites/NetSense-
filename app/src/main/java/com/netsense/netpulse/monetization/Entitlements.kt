package com.netsense.netpulse.monetization

/**
 * Monetization architecture only - per spec, no gating of any real feature and no ad SDK yet.
 * This package exists so a future premium/ads pass has a single, well-defined seam to plug
 * into instead of retrofitting tier checks across the UI later. Nothing in this file changes
 * any user-facing behavior today: [FreeTierEntitlementManager] is the only implementation, and
 * it always reports FREE - there is no billing integration, so anything else would be a fake
 * unlock this app's own "never fabricate" standard doesn't allow.
 *
 * See MONETIZATION.md at the repo root for the full plan (candidate premium features, ad
 * placement strategy, what Play Billing/Store Console work is needed to actually activate this).
 */
enum class FeatureTier {
    FREE,
    PREMIUM
}

/**
 * A specific capability that could plausibly sit behind [FeatureTier.PREMIUM] once real
 * entitlements exist. Enumerating candidates here - rather than scattering ad hoc tier checks
 * through the UI later - is the point of building this now: one registry of "what's premium",
 * not a decision that any of these are actually restricted yet.
 */
enum class PremiumFeature(val displayName: String) {
    /** One-tap "Heal Now" action directly from the persistent notification, skipping the trip
     *  through the app entirely for users who'd rather not open it. */
    NOTIFICATION_HEAL_NOW("Heal Now from Notification"),

    /** Retention beyond the free-tier default (see AppSettings.diagnosticLogRetentionDays /
     *  rawTelemetryRetentionDays) for users who want longer history than the app keeps by
     *  default. */
    EXTENDED_HISTORY_RETENTION("Extended History Retention"),

    /** An ad-free experience, once ads exist at all - see AdPlacementPolicy. */
    AD_FREE("Ad-Free"),

    /** Deep Diagnostic probe mode and the full technical export (CSV/JSONL) already exist and
     *  are free today; this is a placeholder for a possible future "priority/deeper" probe
     *  tier (e.g. more frequent Sentinel polling, more ping-matrix targets) if usage data ever
     *  shows a real premium demand for it - not a current plan, just a reserved slot. */
    ADVANCED_DIAGNOSTICS("Advanced Diagnostics")
}

/**
 * The single place anything in the app should ask "can the current user use X" - a UI
 * component must never invent its own tier check. [FreeTierEntitlementManager] is the only
 * implementation today; a real one (backed by Play Billing) would replace it without any
 * caller needing to change, since they only ever depend on this interface.
 */
interface EntitlementManager {
    fun tierFor(feature: PremiumFeature): FeatureTier
    fun isUnlocked(feature: PremiumFeature): Boolean = tierFor(feature) == FeatureTier.FREE
}

/**
 * The only implementation wired into the app today. Every feature is FREE for every user -
 * there is no purchase flow, no receipt validation, nothing to check against, so claiming
 * anything else would be exactly the kind of fabricated state this app's engines refuse to
 * produce elsewhere (never a fake healing success, never a fake ML prediction - and never a
 * fake premium unlock either).
 */
class FreeTierEntitlementManager : EntitlementManager {
    override fun tierFor(feature: PremiumFeature): FeatureTier = FeatureTier.FREE
}
