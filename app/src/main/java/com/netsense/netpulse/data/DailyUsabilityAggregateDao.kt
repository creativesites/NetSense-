package com.netsense.netpulse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsabilityAggregateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(aggregate: DailyUsabilityAggregateEntity)

    @Query("SELECT * FROM daily_usability_aggregates ORDER BY dateKey ASC")
    suspend fun getAll(): List<DailyUsabilityAggregateEntity>

    @Query("SELECT * FROM daily_usability_aggregates ORDER BY dateKey ASC")
    fun getAllFlow(): Flow<List<DailyUsabilityAggregateEntity>>

    @Query("SELECT COUNT(*) FROM daily_usability_aggregates")
    suspend fun getCount(): Int

    @Query("DELETE FROM daily_usability_aggregates")
    suspend fun clearAll()
}
