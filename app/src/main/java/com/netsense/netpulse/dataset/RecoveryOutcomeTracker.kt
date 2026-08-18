package com.netsense.netpulse.dataset

import com.netsense.netpulse.data.RecoveryOutcomeDao
import com.netsense.netpulse.data.RecoveryOutcomeEntity
import com.netsense.netpulse.data.RecoveryOutcomeStatus
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.UsabilityScoreResult

/**
 * Resolves PENDING recovery attempts to SUCCEEDED/FAILED using actual PulseCore validation
 * state observed afterwards - never from the triggering Android action having merely
 * completed (Part 11: "success means NETWORK ACTUALLY RECOVERED").
 */
class RecoveryOutcomeTracker(private val dao: RecoveryOutcomeDao) {

    companion object {
        /** Minimum time to let a recovery action take effect before it's eligible to be judged. */
        const val MIN_SETTLE_MS = 4_000L

        /** How long to wait for real recovery before giving up and marking FAILED. */
        const val RECOVERY_TIMEOUT_MS = 90_000L

        /** Minimum score improvement (on top of isValidated && !isZombie) to count as recovered. */
        const val MIN_SCORE_IMPROVEMENT = 15
    }

    suspend fun recordAttempt(
        sessionId: String,
        recoveryType: String,
        snapshot: NetworkSnapshot,
        score: UsabilityScoreResult,
        nowMs: Long = System.currentTimeMillis()
    ): Long = dao.insert(
        RecoveryOutcomeEntity(
            sessionId = sessionId,
            timestamp = nowMs,
            recoveryType = recoveryType,
            trigger = score.primaryDiagnosis,
            transportBefore = snapshot.primaryTransport.name,
            preUsabilityScore = score.score,
            preDiagnosis = score.primaryDiagnosis,
            preIsZombie = score.isZombieConnection,
            preIsValidated = snapshot.isValidated
        )
    )

    /**
     * Re-evaluates every PENDING attempt against the current network state. Safe to call
     * frequently (e.g. once per telemetry cycle) - typically zero rows are pending.
     *
     * Returns whether a PulsePolicy-driven caller should currently treat recovery as "in
     * progress" ([RecoveryResolution.hasPending]) or as having "just succeeded" this exact
     * call ([RecoveryResolution.justRecovered]) - both derived from the same real
     * before/after validation used to resolve the row, never from an action merely completing.
     */
    suspend fun resolvePending(
        snapshot: NetworkSnapshot,
        score: UsabilityScoreResult,
        nowMs: Long = System.currentTimeMillis()
    ): RecoveryResolution {
        var justRecovered = false
        var stillPending = false
        for (attempt in dao.getPending()) {
            val elapsed = nowMs - attempt.timestamp
            if (elapsed < MIN_SETTLE_MS) {
                stillPending = true
                continue // too soon to judge
            }

            val recovered = snapshot.isValidated &&
                !score.isZombieConnection &&
                score.score >= attempt.preUsabilityScore + MIN_SCORE_IMPROVEMENT

            if (recovered) {
                dao.resolveOutcome(
                    id = attempt.id,
                    status = RecoveryOutcomeStatus.SUCCEEDED.name,
                    transportAfter = snapshot.primaryTransport.name,
                    postUsabilityScore = score.score,
                    postDiagnosis = score.primaryDiagnosis,
                    resolvedAtTimestamp = nowMs,
                    timeToRecoveryMs = elapsed,
                    internetActuallyReturned = true
                )
                justRecovered = true
            } else if (elapsed >= RECOVERY_TIMEOUT_MS) {
                dao.resolveOutcome(
                    id = attempt.id,
                    status = RecoveryOutcomeStatus.FAILED.name,
                    transportAfter = snapshot.primaryTransport.name,
                    postUsabilityScore = score.score,
                    postDiagnosis = score.primaryDiagnosis,
                    resolvedAtTimestamp = nowMs,
                    timeToRecoveryMs = elapsed,
                    internetActuallyReturned = snapshot.isValidated
                )
            } else {
                // Still within the grace period - leave PENDING for the next cycle.
                stillPending = true
            }
        }
        return RecoveryResolution(hasPending = stillPending, justRecovered = justRecovered)
    }
}

data class RecoveryResolution(
    val hasPending: Boolean,
    val justRecovered: Boolean
)
