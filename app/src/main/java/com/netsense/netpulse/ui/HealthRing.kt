package com.netsense.netpulse.ui

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.netsense.netpulse.model.ConnectionVisual
import com.netsense.netpulse.ui.theme.NetPulseSurfaceVariant
import com.netsense.netpulse.ui.theme.StatusDegraded
import com.netsense.netpulse.ui.theme.StatusGood
import com.netsense.netpulse.ui.theme.StatusOptimal
import com.netsense.netpulse.ui.theme.StatusUnusable

/**
 * The Home screen's hero visual: an animated ring whose motion communicates connection state
 * at a glance - calm and slow when healthy, a gentle pulse when degraded, an indeterminate
 * sweep while actively recovering, and a settled check once restored. All motion is driven by
 * [visual] and [score], which come straight from PulseCore's real diagnosis - nothing here is
 * a decorative animation independent of actual state.
 */
@Composable
fun HealthRing(
    score: Int,
    visual: ConnectionVisual,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 220.dp,
    content: @Composable () -> Unit
) {
    val ringColor = when (visual) {
        ConnectionVisual.CHECK, ConnectionVisual.RECOVERED -> StatusOptimal
        ConnectionVisual.PULSE_WARNING -> StatusDegraded
        ConnectionVisual.ALERT, ConnectionVisual.OFFLINE -> StatusUnusable
        ConnectionVisual.RECOVERING, ConnectionVisual.SEARCHING -> StatusGood
    }

    val infiniteTransition = rememberInfiniteTransition(label = "health_ring_ambient")

    // Ambient glow pulse - slow and subtle when healthy, faster/stronger when something needs
    // attention. Never fast enough to read as an alarm/flash.
    val pulsePeriodMs = when (visual) {
        ConnectionVisual.CHECK, ConnectionVisual.RECOVERED -> 3200
        ConnectionVisual.PULSE_WARNING -> 1600
        ConnectionVisual.ALERT, ConnectionVisual.OFFLINE -> 1400
        ConnectionVisual.RECOVERING, ConnectionVisual.SEARCHING -> 1000
    }
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulsePeriodMs, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Indeterminate rotation, only visible while actively recovering/searching.
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing)
        ),
        label = "ring_rotation"
    )

    val targetProgress = (score.coerceIn(0, 100) / 100f)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 700),
        label = "ring_progress"
    )

    val isIndeterminate = visual == ConnectionVisual.RECOVERING || visual == ConnectionVisual.SEARCHING

    // Canvas's draw lambda is a DrawScope receiver, not a @Composable context - resolve the
    // theme-aware track color here, outside the lambda, same as MainDashboardComponents' gauge.
    val trackColor = NetPulseSurfaceVariant

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 12.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            // Soft ambient glow behind the ring.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ringColor.copy(alpha = glowAlpha), ringColor.copy(alpha = 0f))
                ),
                radius = this.size.minDimension / 2
            )

            // Track.
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            if (isIndeterminate) {
                drawArc(
                    color = ringColor,
                    startAngle = rotation,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            } else {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        content()
    }
}

/** Small status glyph shown above the score/headline - a Warning/Check/PriorityHigh icon. */
@Composable
fun ConnectionStatusIcon(visual: ConnectionVisual, modifier: Modifier = Modifier) {
    val (icon, tint) = when (visual) {
        ConnectionVisual.CHECK, ConnectionVisual.RECOVERED -> Icons.Default.Check to StatusOptimal
        ConnectionVisual.PULSE_WARNING -> Icons.Default.Warning to StatusDegraded
        ConnectionVisual.ALERT, ConnectionVisual.OFFLINE -> Icons.Default.PriorityHigh to StatusUnusable
        ConnectionVisual.RECOVERING, ConnectionVisual.SEARCHING -> Icons.Default.Warning to StatusGood
    }
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier)
}
