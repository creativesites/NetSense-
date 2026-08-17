package com.netsense.netpulse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Real outcome of one self-healing recovery attempt (Part 11).
 *
 * A row is inserted as PENDING the moment a HealerActionItem is executed, capturing the
 * pre-recovery state. It is later resolved to SUCCEEDED or FAILED strictly from actual
 * PulseCore validation of the network afterwards - never from the Android intent/action
 * merely having been launched. See RecoveryOutcomeTracker.
 */
enum class RecoveryOutcomeStatus { PENDING, SUCCEEDED, FAILED }

@Entity(tableName = "recovery_outcomes")
data class RecoveryOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val recoveryType: String, // HealerActionType name
    val trigger: String, // primaryDiagnosis at the moment the action fired
    val transportBefore: String,
    val preUsabilityScore: Int,
    val preDiagnosis: String,
    val preIsZombie: Boolean,
    val preIsValidated: Boolean,
    val status: String = RecoveryOutcomeStatus.PENDING.name,
    val transportAfter: String? = null,
    val postUsabilityScore: Int? = null,
    val postDiagnosis: String? = null,
    val resolvedAtTimestamp: Long? = null,
    val timeToRecoveryMs: Long? = null,
    /** Whether validated Internet actually returned - the sole basis for SUCCEEDED, not action completion. */
    val internetActuallyReturned: Boolean? = null
)
