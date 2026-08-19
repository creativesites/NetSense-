# Per-App Bandwidth Usage ("Live Data Activity") - Feasibility Investigation

Status: **investigated, not implemented**. Per the explicit scoping ("do NOT implement this
feature yet unless investigation shows it can be implemented cleanly and reliably"), this
document is the investigation's conclusion: it can't be, not without tradeoffs serious enough
to warrant a separate, deliberate decision - so nothing was built.

## The idea

A real-time-ish chart showing how much Internet each active app is using right now, similar to
a rolling daily usage chart but focused on the current moment - "which app is eating my data
right now."

## What Android actually offers

Two APIs could source this data, and both have real problems for this use case:

### 1. `NetworkStatsManager` (the API Android's own Settings > Data Usage uses)

Gives accurate, attributed per-UID byte counts across Wi-Fi/cellular, bucketed by time. This
is the *correct* data source in principle - but:

- It requires **`PACKAGE_USAGE_STATS`**, a special permission that is **not** a normal runtime
  permission. It cannot be requested via a standard permission dialog - the user has to be
  sent to Settings → Apps → Special app access → Usage access and manually enable it for
  NetPulse. That's real friction most users won't complete, for a feature that isn't the app's
  core value proposition (which is "does my Internet actually work").
- Google Play reviews apps requesting this permission for a clearly justified primary use
  case (it's the same permission "digital wellbeing"/parental-control/launcher apps use).
  A network-diagnostics utility adding it for a secondary chart feature is a plausible but not
  certain approval, and adds review risk to a launch that should otherwise be clean.
- It's queryable, not a push stream - "live" would still mean polling on an interval and
  diffing cumulative counters yourself, not a true real-time feed.

### 2. `TrafficStats` (no special permission)

`TrafficStats.getUidRxBytes(uid)` / `getUidTxBytes(uid)` give per-UID cumulative bytes since
boot, with no special permission needed - poll on an interval and diff consecutive samples to
approximate a current rate. This avoids the `PACKAGE_USAGE_STATS` friction, but:

- To show *which app* is using data, the app needs the list of installed apps and their UIDs.
  Since Android 11, **package visibility restrictions** mean an app can only see a limited set
  of other installed packages by default - not the full list - unless it declares
  **`QUERY_ALL_PACKAGES`**, which is itself a heavily scrutinized Play Store permission with
  its own justification requirements and rejection risk, for largely the same reason as
  `PACKAGE_USAGE_STATS` above.
- Without `QUERY_ALL_PACKAGES`, a "top apps by usage" list would be **incomplete** - showing
  only a subset of what's actually running, with no reliable way to tell the user that's what's
  happening. That directly conflicts with this app's standing principle of never showing data
  that looks complete/authoritative when it isn't (the same reason RF telemetry shows "N/A"
  instead of a fabricated placeholder, and diagnostic classifications never guess).

### Battery/footprint

Either approach means polling on a short interval (seconds, not the Sentinel's existing 45s
MICRO-probe cadence) to feel "live." That's a meaningfully different battery/CPU profile than
anything else in the app today, and would need its own justification against the app's
existing "as light as possible" background posture.

## Conclusion

Not implementable cleanly or reliably right now:

- The accurate data source (`NetworkStatsManager`) needs a high-friction special permission
  with real Play Store review risk for a secondary feature.
- The low-friction data source (`TrafficStats`) can't reliably enumerate which apps to
  attribute usage to, which would make the feature quietly incomplete - the opposite of this
  app's "never show a misleading number" standard.
- Either path adds a materially different (higher) battery/CPU cost than the app's current
  background design.

## If pursued later

A defensible, honest version of this feature, if a future phase decides it's worth the cost:

- Make it **opt-in**, not default-on, with clear onboarding explaining *why* the special
  Settings permission is needed before sending the user there (never a bare permission
  request with no context).
- Use `NetworkStatsManager` (the accurate source), not `TrafficStats` + `QUERY_ALL_PACKAGES`
  (the incomplete one) - accuracy over convenience, consistent with how the rest of the app is
  built.
- Scope it as a periodic snapshot (e.g. refreshed every 10-30s while the screen is open), not
  a continuously-running background stream, to keep the battery cost bounded to only when a
  user is actually looking at it.
- Budget real time for the Play Store review conversation this permission will trigger before
  committing to it.

Not recommended for the current pre-launch phase - it's a plausible future "Advanced" feature,
not launch-blocking, and the two viable implementations both carry costs (review risk or data
completeness) that deserve their own deliberate go/no-go decision rather than being bundled
into this polish pass.
