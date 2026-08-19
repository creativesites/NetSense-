package com.netsense.netpulse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryObservationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(observation: TelemetryObservationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(observations: List<TelemetryObservationEntity>)

    @Query("SELECT * FROM ml_telemetry_observations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentObservations(limit: Int = 100): List<TelemetryObservationEntity>

    @Query("SELECT * FROM ml_telemetry_observations ORDER BY timestamp DESC")
    fun getAllObservationsFlow(): Flow<List<TelemetryObservationEntity>>

    @Query("SELECT COUNT(*) FROM ml_telemetry_observations")
    suspend fun getObservationCount(): Int

    @Query("DELETE FROM ml_telemetry_observations WHERE timestamp < :cutoffTimestamp")
    suspend fun purgeOlderThan(cutoffTimestamp: Long)

    /**
     * The retention-aware purge DataLifecycleManager actually calls: never deletes a row
     * whose future-outcome labels are still UNRESOLVED (LabelResolver hasn't finished judging
     * it yet), even if it's past the age cutoff - a row that's mid-resolution is exactly the
     * data a purge must not destroy out from under it. In practice a row only stays UNRESOLVED
     * for minutes (LabelResolver's lookahead windows are 15-30s, and a session is declared
     * stale after 5 - so this exclusion is narrow, not a way to dodge retention indefinitely.
     */
    @Query(
        "DELETE FROM ml_telemetry_observations WHERE timestamp < :cutoffTimestamp " +
            "AND labelDegradation15sStatus != 'UNRESOLVED' AND labelDropout30sStatus != 'UNRESOLVED'"
    )
    suspend fun purgeExpiredResolved(cutoffTimestamp: Long)

    @Query("DELETE FROM ml_telemetry_observations")
    suspend fun clearAll()

    // --- Session / label-resolution support -------------------------------------------

    /** All rows for one session, chronological. Required input shape for LabelResolver. */
    @Query("SELECT * FROM ml_telemetry_observations WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getObservationsForSession(sessionId: String): List<TelemetryObservationEntity>

    /** Sessions that still have at least one label awaiting resolution - keeps periodic label resolution cheap. */
    @Query(
        "SELECT DISTINCT sessionId FROM ml_telemetry_observations " +
            "WHERE labelDegradation15sStatus = 'UNRESOLVED' OR labelDropout30sStatus = 'UNRESOLVED'"
    )
    suspend fun getSessionIdsWithUnresolvedLabels(): List<String>

    @Query(
        """
        UPDATE ml_telemetry_observations SET
            labelDegradation15s = :degradation,
            labelDegradation15sStatus = :degradationStatus,
            labelDropout30s = :dropout,
            labelDropout30sStatus = :dropoutStatus,
            labelLikelyCause = :cause,
            labelLikelyCauseStatus = :causeStatus,
            labelSchemaVersion = :schemaVersion
        WHERE id = :id
        """
    )
    suspend fun updateResolvedLabels(
        id: Long,
        degradation: Boolean?,
        degradationStatus: String,
        dropout: Boolean?,
        dropoutStatus: String,
        cause: String?,
        causeStatus: String,
        schemaVersion: Int
    )

    // --- Production (non-synthetic) dataset access -------------------------------------
    // Synthetic (FaultSimulator) rows must never silently enter training data/exports -
    // these queries are the default access path and always exclude isSynthetic = 1.

    @Query("SELECT * FROM ml_telemetry_observations WHERE isSynthetic = 0 ORDER BY timestamp ASC")
    suspend fun getProductionObservations(): List<TelemetryObservationEntity>

    @Query("SELECT * FROM ml_telemetry_observations WHERE isSynthetic = 0 ORDER BY timestamp DESC")
    fun getProductionObservationsFlow(): Flow<List<TelemetryObservationEntity>>

    @Query("SELECT DISTINCT sessionId FROM ml_telemetry_observations WHERE isSynthetic = 0")
    suspend fun getProductionSessionIds(): List<String>
}
