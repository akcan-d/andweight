package com.measured.app.widget

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.measured.app.MainActivity

/** Shared visual for every minimal widget: a ring with its number drawn inside, and - once
 * there's room - a short label next to or below it. Deliberately no card background of any
 * kind, so it reads as a small floating gauge on the home screen rather than another panel.
 *
 * Uses the widget's actual placed size (`SizeMode.Exact` on each widget class) rather than
 * picking from a few fixed breakpoints - different launchers give a "2x2" placement very
 * different real dimensions (some well over 140dp a side), and a ring fixed to one dp size
 * just left most of a bigger placement as blank margin. Scaling the ring to a fraction of
 * whatever space is actually there fills it correctly regardless of the launcher's own grid. */
@Composable
fun MinimalWidgetContent(stat: WidgetStat?, scheme: ColorScheme) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        if (stat == null) {
            Text(
                "Open Measured to finish setup",
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                ),
            )
            return@Box
        }

        val size = LocalSize.current
        val shortestSide = minOf(size.width, size.height)
        val wide = size.width > size.height * 1.3f
        // The label only has room to show once the ring itself isn't already eating the whole
        // placement - below that, a label would just get clipped or force an overflow.
        val hasRoomForLabel = shortestSide >= 64.dp

        val ringDiameterDp = (shortestSide * (if (hasRoomForLabel) 0.62f else 0.9f)).coerceIn(24.dp, 220.dp)
        val ringBitmap = ring(ringDiameterDp, stat, scheme)

        when {
            !hasRoomForLabel -> RingImage(ringBitmap, ringDiameterDp)
            wide -> Row(verticalAlignment = Alignment.CenterVertically) {
                RingImage(ringBitmap, ringDiameterDp)
                Spacer(GlanceModifier.width(8.dp))
                WidgetLabel(stat.label)
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RingImage(ringBitmap, ringDiameterDp)
                WidgetLabel(stat.label)
            }
        }
    }
}

@Composable
private fun RingImage(bitmap: Bitmap, diameterDp: Dp) {
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        modifier = GlanceModifier.size(diameterDp),
    )
}

@Composable
private fun WidgetLabel(label: String) {
    Text(
        label,
        style = TextStyle(
            color = GlanceTheme.colors.onBackground,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        ),
    )
}

private fun ring(diameterDp: Dp, stat: WidgetStat, scheme: ColorScheme): Bitmap {
    // Rendered a few times denser than its display size so the ring and number stay crisp on
    // high-density screens, matching how the diameter was chosen relative to the dp sizes above.
    val sizePx = (diameterDp.value * 3f).toInt()
    return WidgetGraphics.progressRingWithNumber(
        sizePx = sizePx,
        progress = stat.ringProgress,
        // A tint of the ring's own color rather than a flat gray - needs to read clearly against
        // an arbitrary photo wallpaper, not just a themed surface, so a plain muted neutral risks
        // disappearing into a busy or dark background. Matches TodayScreen's ProgressRing.
        trackColor = scheme.primary.copy(alpha = 0.3f).toArgb(),
        ringColor = scheme.primary.toArgb(),
        textColor = scheme.onBackground.toArgb(),
        // A lock glyph takes the same center spot a number normally would, rather than adding a
        // whole separate "locked" rendering path just for this one case.
        numberText = if (stat.locked) "🔒" else stat.numberText,
    )
}
