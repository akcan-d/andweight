package com.andweight.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.andweight.app.AndWeightApplication
import com.andweight.app.MainActivity
import com.andweight.app.domain.GoalDirection
import com.andweight.app.domain.Phrasing
import com.andweight.app.domain.TdeeEngine
import com.andweight.app.domain.TdeeState
import com.andweight.app.domain.UserProfile
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * A calm, read-only glance at progress: a goal-progress ring and a short weight trend, styled
 * with the exact same colors as the in-app theme. Nothing in here is interactive except opening
 * the app - deliberately no quick-log shortcuts, so the widget reads as "here's how it's going"
 * rather than another place to tap through.
 */
class AndWeightWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as AndWeightApplication
        val profile = app.repository.getProfileOnce()
        val theme = WidgetTheme.resolve(context)

        if (profile == null) {
            provideContent {
                GlanceTheme(colors = theme.colorProviders) {
                    EmptyWidgetContent()
                }
            }
            return
        }

        val today = LocalDate.now()
        val logs = app.repository.logsFlow.first()
        val tdeeState = TdeeEngine.compute(profile, logs, today)

        val ringProgress = goalProgress(profile, tdeeState) ?: 1f
        val daysToGoal = tdeeState.goalDate?.let { ChronoUnit.DAYS.between(today, it) }?.coerceAtLeast(0)
        val countdown = daysToGoal?.let { Phrasing.goalCountdown(it) }

        val recentWeights = logs
            .filter { it.weightKg != null }
            .sortedBy { it.date }
            .takeLast(14)
            .map { profile.weightUnit.fromKg(it.weightKg!!) }

        val ringBitmap = WidgetGraphics.progressRing(
            sizePx = RING_SIZE_PX,
            progress = ringProgress,
            trackColor = theme.scheme.surfaceVariant.toArgb(),
            ringColor = theme.scheme.primary.toArgb(),
        )
        val sparklineBitmap = if (recentWeights.size >= 2) {
            WidgetGraphics.sparkline(
                widthPx = SPARKLINE_WIDTH_PX,
                heightPx = SPARKLINE_HEIGHT_PX,
                values = recentWeights,
                lineColor = theme.scheme.secondary.toArgb(),
            )
        } else {
            null
        }

        provideContent {
            GlanceTheme(colors = theme.colorProviders) {
                WidgetContent(
                    ringBitmap = ringBitmap,
                    countdownAmount = countdown?.let { if (it.isApproximate) "~${it.amount}" else "${it.amount}" },
                    countdownLabel = countdown?.let { it.unitLabel + " to go" } ?: "On track",
                    currentWeightText = recentWeights.lastOrNull()?.let { oneDecimal(it) },
                    weightUnitLabel = profile.weightUnit.name.lowercase(),
                    sparklineBitmap = sparklineBitmap,
                )
            }
        }
    }

    private companion object {
        const val RING_SIZE_PX = 220
        const val SPARKLINE_WIDTH_PX = 360
        const val SPARKLINE_HEIGHT_PX = 90
    }
}

private fun goalProgress(profile: UserProfile, tdeeState: TdeeState): Float? {
    if (profile.goalDirection == GoalDirection.MAINTAIN) return null
    val start = tdeeState.earliestKnownWeightKg
    val total = abs(profile.goalWeightKg - start)
    if (total <= 0.0) return 1f
    val done = abs(tdeeState.currentWeightKg - start)
    return (done / total).toFloat().coerceIn(0f, 1f)
}

private fun oneDecimal(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

@Composable
private fun EmptyWidgetContent() {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(20.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Open AndWeight to finish setup",
            style = TextStyle(color = GlanceTheme.colors.onBackground, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun WidgetContent(
    ringBitmap: Bitmap,
    countdownAmount: String?,
    countdownLabel: String,
    currentWeightText: String?,
    weightUnitLabel: String,
    sparklineBitmap: Bitmap?,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(20.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = GlanceModifier.size(72.dp), contentAlignment = Alignment.Center) {
                Image(provider = ImageProvider(ringBitmap), contentDescription = null, modifier = GlanceModifier.size(72.dp))
                if (countdownAmount != null) {
                    Text(
                        countdownAmount,
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                    )
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                countdownLabel,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
        }

        Spacer(GlanceModifier.width(16.dp))

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            if (currentWeightText != null) {
                Text(
                    "$currentWeightText $weightUnitLabel",
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    ),
                )
                Text(
                    "current weight",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
            } else {
                Text(
                    "Log your weight to see stats here",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }

            Spacer(GlanceModifier.height(8.dp))

            if (sparklineBitmap != null) {
                Image(
                    provider = ImageProvider(sparklineBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxWidth().height(28.dp),
                )
            }
        }
    }
}
