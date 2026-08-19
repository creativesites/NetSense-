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

        /** How long to wait for real recovery before giving up and marking FAILED. 90s made the
         *  Home healing screen feel stuck for a long, unexplained wait - most of these actions
         *  (radio re-registration after an airplane-mode toggle, DNS re-resolution) settle well
         *  within 45s in practice, so there's no honest reason to keep the user waiting longer
         *  before offering a retry. */
        const val RECOVERY_TIMEOUT_MS = 45_000L

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
        var justResolved: ResolvedRecovery? = null
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
                justResolved = ResolvedRecovery(
                    recoveryType = attempt.recoveryType,
                    succeeded = true,
                    timeToRecoveryMs = elapsed,
                    postDiagnosis = score.primaryDiagnosis
                )
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
                justResolved = ResolvedRecovery(
                    recoveryType = attempt.recoveryType,
                    succeeded = false,
                    timeToRecoveryMs = elapsed,
                    postDiagnosis = score.primaryDiagnosis
                )
            } else {
                // Still within the grace period - leave PENDING for the next cycle.
                stillPending = true
            }
        }
        return RecoveryResolution(hasPending = stillPending, justRecovered = justRecovered, justResolved = justResolved)
    }
}

data class RecoveryResolution(
    val hasPending: Boolean,
    val justRecovered: Boolean,
    val justResolved: ResolvedRecovery? = null
)

/** The outcome of one recovery attempt that finished resolving this exact tick (success or
 *  failure), carrying the real duration and real post-attempt diagnosis text so the UI never
 *  has to invent either. */
data class ResolvedRecovery(
    val recoveryType: String,
    val succeeded: Boolean,
    val timeToRecoveryMs: Long,
    val postDiagnosis: String
)
