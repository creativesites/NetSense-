# NetPulse Monetization Architecture

Status: **architecture only**. Nothing in this document is implemented as user-facing
behavior yet. No ad SDK is integrated, no purchase flow exists, and no feature in the app is
currently gated behind a premium tier. This is intentional, per the product decision that
launch prep should establish the *seams* monetization will plug into without shipping
placeholder ads or fake premium gates ahead of an actual decision to launch them.

## What exists today

Two small interfaces in `app/src/main/java/com/netsense/netpulse/monetization/`:

- **`EntitlementManager`** (`Entitlements.kt`) - the single place any future UI code should
  ask "can this user use X". `FreeTierEntitlementManager` is the only implementation and
  always returns `FeatureTier.FREE` for every `PremiumFeature`, because there is no billing
  integration to check against. Claiming anything else would be a fabricated unlock, which
  this codebase's engines (diagnostics, healing, ML) already refuse to do elsewhere - this
  applies the same standard to monetization.
- **`AdProvider`** (`AdPlacementPolicy.kt`) - the seam a future ad SDK would implement.
  `NoOpAdProvider` is the only implementation and always returns `AdResult.NoAdAvailable`.
  `AdSlot` enumerates the two placements considered (see below), so a future ads pass has a
  fixed, reviewed list to implement against instead of ad hoc SDK calls sprinkled into
  screens.

Both interfaces are currently unreferenced by any ViewModel or Compose screen - they exist
so the seam is defined, not so anything is gated. Wiring them in is future work, gated on an
explicit decision to launch monetization (see "What's needed to activate this" below).

## Candidate premium features

These are candidates worth having *already thought through*, not decisions to gate anything
today:

| Feature | Why it's a plausible premium candidate |
|---|---|
| **Heal Now from notification** | Saves the user a trip into the app; the free tier already gets healing via the Home screen's Fix It button, so this would be a convenience upsell, not withholding a core capability. |
| **Extended history retention** | The data lifecycle system (see the ML data lifecycle work) has real, configurable retention (`AppSettings.diagnosticLogRetentionDays` / `rawTelemetryRetentionDays`). A premium tier could raise the ceiling for users who want longer local history - this is a natural, low-cost premium lever since it only affects local storage, not server infrastructure. |
| **Ad-free** | Standard once ads exist at all. |
| **Advanced diagnostics** | Reserved slot, not a current plan. Every diagnostic capability in the app (Deep Diagnostic probe mode, full CSV/JSONL export, RF radar, ping matrix) is free today and should stay free unless real usage data justifies otherwise - this row exists so the registry is complete, not because there's a specific feature in mind. |

Explicitly **not** candidates: anything that would make the core promise of the app ("tell me
if my Internet actually works, and help me fix it") worse for free users. The persistent
Sentinel notification, the Home screen's Fix It action, and basic diagnostics must stay free
- gating those would undermine the entire reason someone installs the app.

## Ad placement strategy

Two placements were considered, both deliberately *not* interrupting an active diagnosis or
healing attempt:

1. **History list footer** - a natural break after the user has finished reviewing past
   incidents, not during any time-sensitive flow.
2. **Post-healing-success interstitial** - only after a healing attempt has already resolved
   successfully (never during healing, never on a failed outcome - showing an ad instead of
   "we couldn't fix it" would be actively hostile to a frustrated user).

No banner ads on Home, no ads anywhere in the Diagnostics/Healer/RF Radar screens, and no ads
that could be mistaken for app UI (a fake "Fix It" button, etc.) - these would work directly
against user trust in an app whose entire value proposition is telling the truth about the
user's connection.

## What's needed to actually activate this

None of this is started; listed so the gap is explicit rather than assumed:

- **Premium tier**: Google Play Billing Library integration, a real `EntitlementManager`
  implementation backed by a verified purchase/subscription, Play Console product
  configuration, and a purchase UI (not built).
- **Ads**: an SDK choice (e.g. AdMob), its dependency added to `app/build.gradle.kts`, a real
  `AdProvider` implementation, Play Console ad unit configuration, and - critically - a Play
  Store data-safety declaration update (ads and any associated SDK data collection must be
  disclosed) and a privacy policy update before this can ship.
- **Store listing**: Play Store requires monetization and in-app purchases to be disclosed in
  the store listing if either is added.

## Explicit non-goals for this phase

- No ad SDK dependency added.
- No placeholder/dummy ad views anywhere in the UI.
- No feature currently gated behind a premium check.
- No purchase flow, no Play Billing dependency.
