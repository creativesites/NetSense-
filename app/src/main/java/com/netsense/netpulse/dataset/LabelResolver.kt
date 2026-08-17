package com.netsense.netpulse.dataset

import com.netsense.netpulse.data.TelemetryObservationEntity

/** Resolved value of one look-ahead boolean label. [value] is only meaningful when [status] == RESOLVED. */
data class ResolvedLabel(
    val value: Boolean?,
    val status: LabelResolutionStatus
)

/** Resolved value of the likely-cause label. [cause] is only meaningful when [status] == RESOLVED. */
data class ResolvedLikelyCause(
    val cause: String?,
    val status: LikelyCauseStatus
)

data class ResolvedLabels(
    val degradation15s: ResolvedLabel,
    val dropout30s: ResolvedLabel,
    val likelyCause: ResolvedLikelyCause,
    val schemaVersion: Int = LabelSemantics.LABEL_SCHEMA_VERSION
)

/**
 * Resolves future-outcome labels for persisted telemetry observations using only data that
 * already exists in Room.
 *
 * This is the mechanism that turns "for a given moment in time, what happened during the
 * following 15-30 seconds?" into a label, and it exists specifically to avoid the classic
 * mistake: never writes `false` for an observation whose look-ahead window hasn't actually
 * been fully, continuously observed yet.
 *
 * IMPORTANT: must always be called with the FULL set of observations belonging to one
 * sessionId (see TelemetryObservationDao.getObservationsForSession). Calling it with a
 * filtered/partial subset of a session will produce artificially pessimistic
 * INSUFFICIENT_DATA results, because the resolver has no way to tell "this row doesn't
 * exist" apart from "this row was filtered out".
 */
object LabelResolver {

    /**
     * A same-session gap larger than this, inside a look-ahead window, means we cannot
     * confidently claim "nothing happened" through it - even if a later row exists past the
     * end of the window. This is intentionally smaller than NetworkSessionManager's session
     * rollover gap: a session can legitimately contain occasional multi-second collection
     * gaps (e.g. a probe round took a while) that are still fine for session identity, but
     * are NOT fine to silently skip over when deciding a negative label.
     */
    const val MAX_TRUSTED_INTRA_WINDOW_GAP_MS = 20_000L

    /**
     * How long a session's most recent observation must be untouched before we treat the
     * session as closed (i.e. it will never receive more data, so any still-open windows
     * become INSUFFICIENT_DATA instead of staying UNRESOLVED forever).
     */
    const val SESSION_STALE_MS = 5 * 60_000L // 5 minutes

    /**
     * Resolves labels for every observation in one session.
     *
     * @param observations all rows for one sessionId, any order (sorted internally).
     * @param nowMs wall-clock time to evaluate staleness against; a parameter (not
     *   System.currentTimeMillis() directly) purely so this stays a pure, deterministic,
     *   easily-testable function.
     * @return a map from observation id to its resolved labels.
     */
    fun resolveSession(
        observations: List<TelemetryObservationEntity>,
        nowMs: Long = System.currentTimeMillis()
    ): Map<Long, ResolvedLabels> {
        if (observations.isEmpty()) return emptyMap()
        val sorted = observations.sortedBy { it.timestamp }
        val sessionOpen = (nowMs - sorted.last().timestamp) < SESSION_STALE_MS

        val results = LinkedHashMap<Long, ResolvedLabels>()
        for (i in sorted.indices) {
            val current = sorted[i]
            val future = sorted.subList(i + 1, sorted.size)

            val degradation = resolveWindow(
                current = current,
                future = future,
                windowMs = LabelSemantics.DEGRADATION_LOOKAHEAD_MS,
                sessionOpen = sessionOpen,
                eventPredicate = { prev, curr ->
                    LabelSemantics.detectEvents(prev, curr).any { it.type == NetworkEventType.DEGRADATION_STARTED }
                }
            )

            val dropout = resolveWindow(
                current = current,
                future = future,
                windowMs = LabelSemantics.DROPOUT_LOOKAHEAD_MS,
                sessionOpen = sessionOpen,
                eventPredicate = { prev, curr ->
                    LabelSemantics.detectEvents(prev, curr).any {
                        it.type == NetworkEventType.CONNECTIVITY_LOST || it.type == NetworkEventType.ZOMBIE_DETECTED
                    }
                }
            )

            val cause = if (dropout.value == true) {
                val triggerRow = findDropoutTriggerRow(current, future, LabelSemantics.DROPOUT_LOOKAHEAD_MS)
                val inferred = triggerRow?.let { LabelSemantics.inferLikelyCause(it) }
                ResolvedLikelyCause(inferred, if (inferred != null) LikelyCauseStatus.RESOLVED else LikelyCauseStatus.UNLABELED)
            } else {
                ResolvedLikelyCause(null, LikelyCauseStatus.UNLABELED)
            }

            results[current.id] = ResolvedLabels(degradation, dropout, cause)
        }
        return results
    }

    /**
     * Walks forward from [current] through [future] up to [windowMs], looking for the first
     * row pair that satisfies [eventPredicate]. Implements the three-way UNRESOLVED /
     * RESOLVED / INSUFFICIENT_DATA outcome:
     *  - a qualifying event inside the window -> RESOLVED(true), regardless of gaps.
     *  - the window fully, gaplessly covered with no event -> RESOLVED(false).
     *  - the window not fully covered (or covered with an untrusted gap) and the session is
     *    still open -> UNRESOLVED (may still resolve later).
     *  - the window not fully covered (or covered with an untrusted gap) and the session is
     *    closed -> INSUFFICIENT_DATA (never will resolve; this is a terminal state).
     */
    private fun resolveWindow(
        current: TelemetryObservationEntity,
        future: List<TelemetryObservationEntity>,
        windowMs: Long,
        sessionOpen: Boolean,
        eventPredicate: (prev: TelemetryObservationEntity, curr: TelemetryObservationEntity) -> Boolean
    ): ResolvedLabel {
        val windowEnd = current.timestamp + windowMs
        var prev = current
        var coveredUntil = current.timestamp
        var sawUntrustedGap = false

        for (row in future) {
            if (row.timestamp > windowEnd) break
            val gap = row.timestamp - prev.timestamp
            if (gap > MAX_TRUSTED_INTRA_WINDOW_GAP_MS) sawUntrustedGap = true

            if (eventPredicate(prev, row)) {
                return ResolvedLabel(true, LabelResolutionStatus.RESOLVED)
            }

            coveredUntil = row.timestamp
            prev = row
        }

        return when {
            coveredUntil >= windowEnd && !sawUntrustedGap -> ResolvedLabel(false, LabelResolutionStatus.RESOLVED)
            sessionOpen -> ResolvedLabel(null, LabelResolutionStatus.UNRESOLVED)
            else -> ResolvedLabel(null, LabelResolutionStatus.INSUFFICIENT_DATA)
        }
    }

    private fun findDropoutTriggerRow(
        current: TelemetryObservationEntity,
        future: List<TelemetryObservationEntity>,
        windowMs: Long
    ): TelemetryObservationEntity? {
        val windowEnd = current.timestamp + windowMs
        var prev = current
        for (row in future) {
            if (row.timestamp > windowEnd) break
            val hit = LabelSemantics.detectEvents(prev, row).any {
                it.type == NetworkEventType.CONNECTIVITY_LOST || it.type == NetworkEventType.ZOMBIE_DETECTED
            }
            if (hit) return row
            prev = row
        }
        return null
    }
}
