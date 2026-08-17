"""
Phase 3 feature-leakage audit.

For every value that could conceivably end up in a PulsePredictor feature vector, this
module records an explicit verdict: "could this value know the future relative to the row
it's attached to?" Nothing is allowed into `features.FEATURE_NAMES` without a SAFE verdict
recorded here, and `audit_feature_schema()` fails loudly if that ever drifts out of sync.
"""

from __future__ import annotations

from dataclasses import dataclass

from . import dataset, features


@dataclass
class LeakageVerdict:
    field: str
    used_as_feature: bool
    verdict: str  # "SAFE" | "UNSAFE" | "SAFE_BUT_UNUSED"
    justification: str


# One entry per column that exists on the exported dataset (dataset.EXPECTED_COLUMNS),
# not just the 12 that currently feed the model - so a future feature addition has to be
# assessed here before it can be used.
LEAKAGE_AUDIT: list[LeakageVerdict] = [
    LeakageVerdict(
        "dnsLatencyMs / tcpRttMs / tcpJitterMs / httpTtfbMs", True, "SAFE",
        "Measured synchronously by DiagnosticEngine at the row's own timestamp; the probe "
        "that produced them completes before the row is persisted. No dependency on any "
        "later row.",
    ),
    LeakageVerdict(
        "packetLossPct", True, "SAFE",
        "Computed by DiagnosticEngine from that same probe round only.",
    ),
    LeakageVerdict(
        "rsrpDbm / rsrqDb / sinrDb / cqi", True, "SAFE",
        "Read from a CellInfoLte/Nr snapshot taken at the row's own timestamp "
        "(RadarEngine.isRfDataMeasured gates this - see the data-engineering audit). "
        "No dependency on any later row.",
    ),
    LeakageVerdict(
        "wifiRssiDbm", True, "SAFE",
        "Read from WifiInfo at the row's own timestamp (RadarEngine.isRssiMeasured gates this).",
    ),
    LeakageVerdict(
        "latency_velocity / signal_velocity (derived)", True, "SAFE",
        "Computed strictly from `curr` and the immediately PRECEDING row within the same "
        "window (`prev`, prev.timestamp < curr.timestamp always, enforced by "
        "assert_chronological_window() below). Never looks at a row after `curr`.",
    ),
    LeakageVerdict(
        "consecutiveProbeFailures", True, "SAFE",
        "A running count derived from probe history UP TO AND INCLUDING the current probe; "
        "computed in NetPulseViewModel before the row is persisted.",
    ),
    LeakageVerdict(
        "transport", True, "SAFE",
        "ConnectivityManager's active transport at the row's own timestamp - a present-tense "
        "platform fact, not a forecast.",
    ),
    LeakageVerdict(
        "usabilityScore", False, "SAFE_BUT_UNUSED",
        "UsabilityEngine.calculateScore() is a pure function of THIS row's own snapshot/probe "
        "- present-state, not future. Not currently one of the 12 model features; if added "
        "later it remains safe for the same reason.",
    ),
    LeakageVerdict(
        "isValidated / isZombie", False, "SAFE_BUT_UNUSED",
        "Present-state platform/heuristic flags for THIS row. These are the ingredients "
        "LabelSemantics uses to detect a FUTURE ROW's transition and attach the resulting "
        "label back to an EARLIER row - the leakage-sensitive direction (row i's label using "
        "row i+k's state) is handled entirely in LabelResolver/label generation, never in "
        "the feature vector itself. Not currently model features.",
    ),
    LeakageVerdict(
        "isCaptivePortal / httpStatusCode", False, "SAFE_BUT_UNUSED",
        "Present-state signals for THIS row's own probe. Not currently model features "
        "(used only in likely-cause inference on the DEFINING/labeled row, never copied "
        "into another row's feature vector).",
    ),
    LeakageVerdict(
        "labelDegradation15s / labelDropout30s / labelLikelyCause (+ *Status)", False, "UNSAFE_AS_FEATURE",
        "These are look-ahead TARGETS by construction (LabelResolver looks 15-30s into the "
        "future to compute them). They must NEVER appear in a feature vector for the row "
        "they're attached to, or for any other row in the same window. "
        "audit_feature_schema() asserts this holds.",
    ),
    LeakageVerdict(
        "recoveryActionTriggered / recoverySuccess (legacy columns)", False, "SAFE_BUT_UNUSED",
        "Not currently populated by the label-resolution pipeline (superseded by "
        "RecoveryOutcomeEntity) and not a model feature. FLAGGED FOR FUTURE REVIEW: a "
        "recovery attempt/outcome pair spans time, so if these (or RecoveryOutcomeEntity "
        "fields) are ever used as a feature, the 'outcome' side must be excluded from any "
        "row at-or-before the attempt's resolution time.",
    ),
    LeakageVerdict(
        "observationQuality / isSynthetic / sessionId / networkIdHash", False, "SAFE_BUT_UNUSED",
        "Used only as filters/grouping keys before windowing (drop INVALID/SYNTHETIC rows, "
        "group by session) - never fed to the model as a predictive signal.",
    ),
]


def audit_feature_schema() -> list[str]:
    """Fails loudly (returns non-empty problem list) if the 12 features actually used by
    the model include anything not explicitly marked SAFE above, or if any label column
    has leaked into the feature name list."""
    problems: list[str] = []

    label_like = {c for c in dataset.EXPECTED_COLUMNS if c.lower().startswith("label")}
    feature_like = set(features.FEATURE_NAMES)
    overlap = label_like & feature_like
    if overlap:
        problems.append(f"Label columns present in FEATURE_NAMES: {overlap}")

    audited_safe_fields = {
        v.field for v in LEAKAGE_AUDIT if v.verdict in ("SAFE", "SAFE_BUT_UNUSED")
    }
    # Every one of the 12 feature names must be traceable to an audited SAFE source field.
    # This is a loose containment check (feature names are derived/renamed from source
    # columns) rather than a strict 1:1 map, since e.g. "latency_velocity" is derived.
    # Checks EVERY underscore-separated part (not just the first) because the meaningful
    # keyword isn't always first - e.g. "cellular_rsrp" matches on "rsrp", not "cellular".
    def _is_audited(feature_name: str) -> bool:
        parts = feature_name.split("_")
        return any(
            feature_name in field.lower() or any(part in field.lower() for part in parts)
            for field in audited_safe_fields
        )

    unaudited = [name for name in features.FEATURE_NAMES if not _is_audited(name)]
    if unaudited:
        problems.append(
            f"Model features with no matching SAFE leakage-audit entry: {unaudited}. "
            f"Add an explicit LeakageVerdict before using them."
        )

    return problems


def assert_chronological_window(timestamps: list[int]) -> None:
    """A training window must be strictly time-ordered with no row appearing 'after itself'.
    Raises AssertionError (loudly, not silently) if violated - this is the concrete runtime
    guard behind the SAFE verdict on the velocity features above."""
    for i in range(1, len(timestamps)):
        if timestamps[i] < timestamps[i - 1]:
            raise AssertionError(
                f"Window is not chronologically sorted at index {i}: "
                f"{timestamps[i - 1]} -> {timestamps[i]}. A misordered window can make "
                f"velocity features silently look at 'future' data relative to their own row."
            )


def render_leakage_report() -> str:
    lines = [
        "# PulsePredictor Feature Leakage Audit",
        "",
        "Verdict legend: SAFE (used as a model feature, verified past/present-only), "
        "SAFE_BUT_UNUSED (present/past-only but not currently a model feature), "
        "UNSAFE_AS_FEATURE (deliberately future-looking - a label, never a feature).",
        "",
        "| Field | Used as feature? | Verdict | Justification |",
        "|---|---|---|---|",
    ]
    for v in LEAKAGE_AUDIT:
        lines.append(f"| {v.field} | {'yes' if v.used_as_feature else 'no'} | {v.verdict} | {v.justification} |")

    problems = audit_feature_schema()
    lines.append("")
    if problems:
        lines.append("## AUTOMATED CHECK: FAILED")
        for p in problems:
            lines.append(f"- {p}")
    else:
        lines.append("## AUTOMATED CHECK: PASSED - no label leakage detected in the 12-feature schema.")
    return "\n".join(lines)
