package com.measured.app.widget

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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

/** The three sizes every minimal widget declares via `SizeMode.Responsive` - matching the
 * standard Android home-screen cell formula (70dp * cells - 30dp), so a 1x1 placement gets
 * [SIZE_SMALL], and resizing up to 2x1 or 2x2 steps up from there. */
val SIZE_SMALL = DpSize(40.dp, 40.dp)
val SIZE_MEDIUM = DpSize(110.dp, 40.dp)
val SIZE_LARGE = DpSize(110.dp, 110.dp)

/** Shared visual for every minimal widget: a ring with its number drawn inside, and - once
 * there's room - a short label next to or below it. Deliberately no card background of any
 * kind, so it reads as a small floating gauge on the home screen rather than another panel. */
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
        val ringDiameterDp = if (size == SIZE_LARGE) 64.dp else 32.dp
        val ringBitmap = ring(ringDiameterDp, stat, scheme)

        when (size) {
            SIZE_SMALL -> RingImage(ringBitmap, ringDiameterDp)
            SIZE_MEDIUM -> Row(verticalAlignment = Alignment.CenterVertically) {
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
        trackColor = scheme.surfaceVariant.toArgb(),
        ringColor = scheme.primary.toArgb(),
        textColor = scheme.onBackground.toArgb(),
        // A lock glyph takes the same center spot a number normally would, rather than adding a
        // whole separate "locked" rendering path just for this one case.
        numberText = if (stat.locked) "🔒" else stat.numberText,
    )
}
