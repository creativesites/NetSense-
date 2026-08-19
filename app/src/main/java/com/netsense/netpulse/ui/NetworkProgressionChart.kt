package com.netsense.netpulse.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netsense.netpulse.data.DiagnosticLogEntity
import com.netsense.netpulse.ui.theme.NetPulseAccent
import com.netsense.netpulse.ui.theme.NetPulseAccentContainer
import com.netsense.netpulse.ui.theme.NetPulseBorderSubtle
import com.netsense.netpulse.ui.theme.NetPulseSurface
import com.netsense.netpulse.ui.theme.NetPulseSurfaceVariant
import com.netsense.netpulse.ui.theme.NetPulseTextPrimary
import com.netsense.netpulse.ui.theme.NetPulseTextTertiary
import com.netsense.netpulse.ui.theme.StatusDegraded
import com.netsense.netpulse.ui.theme.StatusOptimal
import com.netsense.netpulse.ui.theme.StatusUnusable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Time window + bucket size a user can pick for the Home progression chart. Buckets are
 *  averaged, real samples - never interpolated or fabricated for empty buckets, which are
 *  simply skipped rather than drawn as a flat/zero line. */
enum class ChartPeriod(val label: String, val windowMs: Long, val bucketMs: Long) {
    TODAY("Today", 24L * 60 * 60 * 1000, 60L * 60 * 1000),
    WEEK("7 Days", 7L * 24 * 60 * 60 * 1000, 24L * 60 * 60 * 1000),
    MONTH("30 Days", 30L * 24 * 60 * 60 * 1000, 24L * 60 * 60 * 1000)
}

data class ChartPoint(val bucketStartMs: Long, val averageScore: Float, val sampleCount: Int)

/**
 * Groups real DiagnosticLogEntity rows within [period]'s window into time buckets and averages
 * their score - a presentation aggregation over data UsabilityEngine already computed, not a
 * new diagnosis. A bucket with zero samples is simply absent from the result (no interpolated
 * or zero-filled points), so the chart never implies a measurement that didn't happen.
 */
fun buildChartPoints(
    logs: List<DiagnosticLogEntity>,
    period: ChartPeriod,
    nowMs: Long = System.currentTimeMillis()
): List<ChartPoint> {
    val windowStart = nowMs - period.windowMs
    val inWindow = logs.filter { it.timestamp in windowStart..nowMs }
    if (inWindow.isEmpty()) return emptyList()
    return inWindow
        .groupBy { (it.timestamp - windowStart) / period.bucketMs }
        .entries
        .sortedBy { it.key }
        .map { (bucketIndex, bucketLogs) ->
            ChartPoint(
                bucketStartMs = windowStart + bucketIndex * period.bucketMs,
                averageScore = bucketLogs.map { it.score }.average().toFloat(),
                sampleCount = bucketLogs.size
            )
        }
}

/** One real, labeled point on the chart's time axis - never a synthesized/interpolated instant,
 *  always either an actual measurement's bucket start or the window's "now" boundary. */
data class ChartAxisTick(val timestampMs: Long, val label: String)

/** "Nice" round intervals to snap axis ticks to, so the axis reads "08:00, 08:15, 08:30" rather
 *  than arbitrary sample-driven offsets like "08:03, 08:19, 08:41". */
private val niceTickStepsMs = listOf(
    5 * 60_000L, 15 * 60_000L, 30 * 60_000L,
    60 * 60_000L, 2 * 60 * 60_000L, 3 * 60 * 60_000L, 6 * 60 * 60_000L, 12 * 60 * 60_000L,
    24 * 60 * 60_000L, 2 * 24 * 60 * 60_000L, 7 * 24 * 60 * 60_000L
)

/**
 * Builds the chart's dynamic time axis from the actual span of real data - the earliest
 * measurement currently on screen through "now" - rather than a fixed two-label
 * first-point/"Now" pair. The tick interval adapts to how much history is actually available
 * (Section 3 of the Trust pass: "never imply precision the data doesn't have"), and every tick
 * is a real clock time within that real span - nothing is invented outside
 * [points.first().bucketStartMs, nowMs].
 */
fun buildChartAxisTicks(
    points: List<ChartPoint>,
    nowMs: Long = System.currentTimeMillis(),
    targetTickCount: Int = 4
): List<ChartAxisTick> {
    if (points.isEmpty()) return emptyList()

    val domainStart = points.first().bucketStartMs
    val domainEnd = maxOf(nowMs, points.last().bucketStartMs)

    // A single point (or a zero/negative span, e.g. a fabricated "now" earlier than the data)
    // has no meaningful interval to tick across - just label the one real instant we have.
    if (points.size < 2 || domainEnd <= domainStart) {
        return listOf(ChartAxisTick(domainStart, formatChartTime(domainStart, nowMs)))
    }

    val span = domainEnd - domainStart
    val rawStep = span / (targetTickCount - 1).coerceAtLeast(1)
    val step = niceTickStepsMs.firstOrNull { it >= rawStep } ?: niceTickStepsMs.last()

    val ticks = mutableListOf(domainStart)
    var t = (domainStart / step + 1) * step
    while (t < domainEnd) {
        ticks += t
        t += step
    }
    ticks += domainEnd

    val distinctTicks = ticks.distinct()
    val thinned = if (distinctTicks.size > targetTickCount) {
        val stride = (distinctTicks.size - 1).toFloat() / (targetTickCount - 1)
        (0 until targetTickCount)
            .map { i -> distinctTicks[(i * stride).toInt().coerceIn(0, distinctTicks.size - 1)] }
            .distinct()
    } else {
        distinctTicks
    }

    return thinned.map { ts ->
        // The right edge of the visible window is genuinely "now" - a relative label reads more
        // naturally there than repeating a clock time also shown moments ago on screen.
        val label = if (ts == domainEnd && ts == nowMs) "Now" else formatChartTime(ts, nowMs)
        ChartAxisTick(ts, label)
    }
}

@Composable
fun NetworkProgressionChartCard(logs: List<DiagnosticLogEntity>) {
    var period by remember { mutableStateOf(ChartPeriod.TODAY) }
    val points = remember(logs, period) { buildChartPoints(logs, period) }
    val axisTicks = remember(points) { buildChartAxisTicks(points) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = NetPulseSurface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("network_progression_chart_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Network Health",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NetPulseTextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChartPeriod.values().forEach { candidate ->
                        PeriodChip(
                            label = candidate.label,
                            isSelected = period == candidate,
                            onClick = { period = candidate }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (points.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Not enough data yet for this period.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NetPulseTextTertiary
                    )
                }
            } else {
                ProgressionLineChart(
                    points = points,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("network_progression_chart")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("network_progression_chart_axis"),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    axisTicks.forEach { tick ->
                        Text(
                            text = tick.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = NetPulseTextTertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) NetPulseAccentContainer else NetPulseSurfaceVariant,
        modifier = Modifier
            .clickable { onClick() }
            .testTag("chart_period_${label.lowercase().replace(" ", "_")}")
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) NetPulseAccent else NetPulseTextTertiary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ProgressionLineChart(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    // Canvas's draw lambda isn't a @Composable context, so resolve theme colors here first,
    // same pattern as HealthRing.
    val gridColor = NetPulseBorderSubtle
    val goodColor = StatusOptimal
    val fairColor = StatusDegraded
    val poorColor = StatusUnusable
    val averageScore = points.map { it.averageScore }.average().toFloat()
    val lineColor = when {
        averageScore >= 60f -> goodColor
        averageScore >= 35f -> fairColor
        else -> poorColor
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val topInset = 8.dp.toPx()
        val bottomInset = 4.dp.toPx()
        val plotHeight = h - topInset - bottomInset

        // Reference gridlines at 100/50/0.
        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = topInset + plotHeight * (1f - fraction)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        fun xFor(index: Int) = if (points.size == 1) w / 2f else w * index / (points.size - 1).toFloat()
        fun yFor(score: Float) = topInset + plotHeight * (1f - (score.coerceIn(0f, 100f) / 100f))

        val linePath = Path()
        points.forEachIndexed { index, point ->
            val x = xFor(index)
            val y = yFor(point.averageScore)
            if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(xFor(points.size - 1), topInset + plotHeight)
            lineTo(xFor(0), topInset + plotHeight)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0f)))
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        points.forEachIndexed { index, point ->
            drawCircle(color = lineColor, radius = 2.5.dp.toPx(), center = Offset(xFor(index), yFor(point.averageScore)))
        }
    }
}

private fun formatChartTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(nowMs)) ==
        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(timestampMs))
    return if (sameDay) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
    } else {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMs))
    }
}
