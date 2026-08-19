package com.netsense.netpulse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One calendar day's worth of [DiagnosticLogEntity] rows rolled up into real averages/counts -
 * never interpolated or fabricated. This is what lets NetPulse purge raw diagnostic history
 * past its retention window without losing long-range trend signal outright: once a day's raw
 * rows age out, this aggregate is the only thing left representing that day, and it's built
 * from the same real per-probe rows before they're deleted.
 *
 * [dateKey] is a UTC "yyyy-MM-dd" bucket, independent of device timezone, so aggregation is
 * deterministic and testable without a device/locale dependency.
 */
@Entity(tableName = "daily_usability_aggregates")
data class DailyUsabilityAggregateEntity(
    @PrimaryKey val dateKey: String,
    val sampleCount: Int,
    val averageScore: Int,
    val minScore: Int,
    val maxScore: Int,
    val zombieCount: Int,
    val averageDnsMs: Long?,
    val averageTcpMs: Long?,
    val averageHttpMs: Long?,
    val firstTimestamp: Long,
    val lastTimestamp: Long
)
