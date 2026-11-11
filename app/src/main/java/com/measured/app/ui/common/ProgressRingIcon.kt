package com.measured.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A thin progress arc drawn around whatever [content] puts at its center - the same ring/track
 * styling as the Status tab's main progress ring (see TodayScreen's ProgressRing), scaled down to
 * wrap a small badge or icon instead of standing alone as a hero display.
 */
@Composable
fun ProgressRingIcon(
    progress: Float,
    diameter: Dp,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 3.dp,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    val trackColor = ringColor.copy(alpha = 0.18f)
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val strokePx = strokeWidth.toPx()
            val arcDiameter = size.minDimension - strokePx
            val topLeft = Offset((size.width - arcDiameter) / 2f, (size.height - arcDiameter) / 2f)
            val arcSize = Size(arcDiameter, arcDiameter)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
        content()
    }
}
