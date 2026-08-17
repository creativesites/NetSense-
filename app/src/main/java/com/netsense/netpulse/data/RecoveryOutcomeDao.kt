package com.netsense.netpulse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecoveryOutcomeDao {

    @Insert
    suspend fun insert(outcome: RecoveryOutcomeEntity): Long

    @Query("SELECT * FROM recovery_outcomes WHERE status = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPending(): List<RecoveryOutcomeEntity>

    @Query("SELECT * FROM recovery_outcomes ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<RecoveryOutcomeEntity>>

    @Query(
        """
        UPDATE recovery_outcomes SET
            status = :status,
            transportAfter = :transportAfter,
            postUsabilityScore = :postUsabilityScore,
            postDiagnosis = :postDiagnosis,
            resolvedAtTimestamp = :resolvedAtTimestamp,
            timeToRecoveryMs = :timeToRecoveryMs,
            internetActuallyReturned = :internetActuallyReturned
        WHERE id = :id
        """
    )
    suspend fun resolveOutcome(
        id: Long,
        status: String,
        transportAfter: String?,
        postUsabilityScore: Int?,
        postDiagnosis: String?,
        resolvedAtTimestamp: Long?,
        timeToRecoveryMs: Long?,
        internetActuallyReturned: Boolean?
    )
}
