package com.netsense.netpulse.dataset

import com.netsense.netpulse.data.TelemetryObservationDao

/**
 * Orchestrates periodic label resolution (Part 16's "repository/service layer") against
 * Room, without ever running a neural network or model on-device - this is pure data
 * engineering, not inference.
 *
 * Deliberately cheap by default: only sessions that still have at least one UNRESOLVED
 * label are re-scanned (Part 20 - high-value data, not maximum churn), and a row is only
 * written back to Room when its resolved value/status actually changed.
 */
class LabelResolutionService(private val dao: TelemetryObservationDao) {

    /** Resolves labels for every session with pending work. Returns the number of rows updated. */
    suspend fun resolvePendingLabels(nowMs: Long = System.currentTimeMillis()): Int {
        var updated = 0
        for (sessionId in dao.getSessionIdsWithUnresolvedLabels()) {
            updated += resolveSession(sessionId, nowMs)
        }
        return updated
    }

    /** Resolves labels for one specific session. Returns the number of rows updated. */
    suspend fun resolveSession(sessionId: String, nowMs: Long = System.currentTimeMillis()): Int {
        val observations = dao.getObservationsForSession(sessionId)
        if (observations.isEmpty()) return 0

        val resolved = LabelResolver.resolveSession(observations, nowMs)
        var updated = 0
        for (obs in observations) {
            val labels = resolved[obs.id] ?: continue
            val changed = obs.labelDegradation15s != labels.degradation15s.value ||
                obs.labelDegradation15sStatus != labels.degradation15s.status.name ||
                obs.labelDropout30s != labels.dropout30s.value ||
                obs.labelDropout30sStatus != labels.dropout30s.status.name ||
                obs.labelLikelyCause != labels.likelyCause.cause ||
                obs.labelLikelyCauseStatus != labels.likelyCause.status.name

            // Never write over a terminal state with another identical terminal result -
            // saves a Room write for rows that were already resolved in a prior pass.
            if (!changed) continue

            dao.updateResolvedLabels(
                id = obs.id,
                degradation = labels.degradation15s.value,
                degradationStatus = labels.degradation15s.status.name,
                dropout = labels.dropout30s.value,
                dropoutStatus = labels.dropout30s.status.name,
                cause = labels.likelyCause.cause,
                causeStatus = labels.likelyCause.status.name,
                schemaVersion = labels.schemaVersion
            )
            updated++
        }
        return updated
    }
}
