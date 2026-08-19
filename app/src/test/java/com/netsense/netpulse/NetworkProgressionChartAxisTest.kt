package com.netsense.netpulse

import com.netsense.netpulse.ui.ChartPoint
import com.netsense.netpulse.ui.buildChartAxisTicks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the Trust pass' Section 3 fix: the Home network-health chart's time
 * axis must dynamically reflect the real span of available history, with a tick interval that
 * adapts to that span - never a fixed two-label "first point / Now" pair, and never a tick
 * outside the real observed window.
 */
class NetworkProgressionChartAxisTest {

    private fun point(bucketStartMs: Long, score: Float = 80f) = ChartPoint(bucketStartMs, score, sampleCount = 1)

    @Test
    fun `no points produces no axis ticks`() {
        assertTrue(buildChartAxisTicks(emptyList(), nowMs = 100_000L).isEmpty())
    }

    @Test
    fun `a single point produces exactly one tick, never a fabricated second one`() {
        val ticks = buildChartAxisTicks(listOf(point(50_000L)), nowMs = 100_000L)
        assertEquals(1, ticks.size)
        assertEquals(50_000L, ticks.first().timestampMs)
    }

    @Test
    fun `every tick timestamp falls within the real observed window - none invented outside it`() {
        val start = 1_000_000L
        val now = start + 6 * 60 * 60 * 1000L // 6 hours of real history
        val points = (0..6).map { point(start + it * 60 * 60 * 1000L) }

        val ticks = buildChartAxisTicks(points, nowMs = now)

        ticks.forEach { tick ->
            assertTrue(
                "tick ${tick.timestampMs} must be within [$start, $now]",
                tick.timestampMs in start..now
            )
        }
    }

    @Test
    fun `the axis never exceeds the requested tick count, even with dense data`() {
        val start = 1_000_000L
        val now = start + 24 * 60 * 60 * 1000L
        // One point every 5 minutes across a full day - 288 real samples.
        val points = (0..288).map { point(start + it * 5 * 60 * 1000L) }

        val ticks = buildChartAxisTicks(points, nowMs = now, targetTickCount = 4)

        assertTrue("expected at most 4 ticks, got ${ticks.size}", ticks.size <= 4)
    }

    @Test
    fun `a short same-day span labels ticks with a clock time, not a date`() {
        val start = 1_000_000L
        val now = start + 45 * 60 * 1000L // 45 minutes of history, same calendar day
        val points = listOf(point(start), point(start + 20 * 60 * 1000L), point(now))

        val ticks = buildChartAxisTicks(points, nowMs = now)

        // Every label except the "Now" boundary should look like a clock time (contains ':'),
        // never a bare date - there's no multi-day span here to justify one.
        ticks.filter { it.label != "Now" }.forEach { tick ->
            assertTrue("expected a clock-time label, got '${tick.label}'", tick.label.contains(":"))
        }
    }

    @Test
    fun `the real right edge of the window is labeled Now`() {
        val start = 1_000_000L
        val now = start + 90 * 60 * 1000L
        val points = listOf(point(start), point(start + 45 * 60 * 1000L))

        val ticks = buildChartAxisTicks(points, nowMs = now)

        assertEquals("Now", ticks.last().label)
        assertEquals(now, ticks.last().timestampMs)
    }

    @Test
    fun `a multi-day span produces date-style labels for ticks that aren't today`() {
        val start = 1_000_000L
        val now = start + 3L * 24 * 60 * 60 * 1000L // 3 days of history
        val points = (0..3).map { point(start + it * 24 * 60 * 60 * 1000L) }

        val ticks = buildChartAxisTicks(points, nowMs = now)

        // At least one non-"Now" tick must exist, and it must not be formatted as a bare
        // clock time (HH:mm) since it isn't from today.
        val nonNowTicks = ticks.filter { it.label != "Now" }
        assertTrue(nonNowTicks.isNotEmpty())
        nonNowTicks.forEach { tick ->
            assertTrue("expected a date-style label, got '${tick.label}'", !tick.label.contains(":"))
        }
    }

    @Test
    fun `ticks are returned in chronological order`() {
        val start = 1_000_000L
        val now = start + 10L * 24 * 60 * 60 * 1000L
        val points = (0..10).map { point(start + it * 24 * 60 * 60 * 1000L) }

        val ticks = buildChartAxisTicks(points, nowMs = now)

        val timestamps = ticks.map { it.timestampMs }
        assertEquals(timestamps.sorted(), timestamps)
    }

    @Test
    fun `sparse data with large gaps still produces a sensible bounded axis`() {
        // Two real measurements 5 days apart, nothing fabricated in between.
        val start = 1_000_000L
        val now = start + 5L * 24 * 60 * 60 * 1000L
        val points = listOf(point(start), point(now))

        val ticks = buildChartAxisTicks(points, nowMs = now)

        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.size <= 4)
        assertEquals(start, ticks.first().timestampMs)
        assertEquals(now, ticks.last().timestampMs)
    }
}
