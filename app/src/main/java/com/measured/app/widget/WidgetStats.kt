package com.measured.app.widget

import android.content.Context
import com.measured.app.AndWeightApplication
import com.measured.app.domain.GoalDirection
import com.measured.app.domain.Phrasing
import com.measured.app.domain.TdeeEngine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** What a single widget instance shows: how full its ring is, the number at its center, and the
 * short label underneath that number (shown only at the larger sizes - see [MinimalWidgetContent]). */
data class WidgetStat(val ringProgress: Float, val numberText: String, val label: String)

/** The three widget types all share one ring (always weight-based progress toward the goal, same
 * meaning as the in-app Status ring) and only differ in what number sits at its center - rather
 * than each widget computing its own notion of "progress", which would make the three widgets'
 * rings mean subtly different things sitting next to each other on a home screen. */
object WidgetStats {

    enum class Metric { WEIGHT_MOVED, PERCENT, TIME_REMAINING }

    suspend fun compute(context: Context, metric: Metric): WidgetStat? {
        val app = context.applicationContext as AndWeightApplication
        val profile = app.repository.getProfileOnce() ?: return null
        val today = LocalDate.now()
        val logs = app.repository.logsFlow.first()
        val tdeeState = TdeeEngine.compute(profile, logs, today)
        val unitLabel = profile.weightUnit.name.lowercase()

        if (profile.goalDirection == GoalDirection.MAINTAIN) {
            val currentInUnit = profile.weightUnit.fromKg(tdeeState.currentWeightKg)
            return WidgetStat(ringProgress = 0f, numberText = oneDecimal(currentInUnit), label = "current $unitLabel")
        }

        val start = tdeeState.earliestKnownWeightKg
        val total = abs(profile.goalWeightKg - start)
        val progress = if (total <= 0.0) {
            1f
        } else {
            (abs(tdeeState.currentWeightKg - start) / total).toFloat().coerceIn(0f, 1f)
        }

        return when (metric) {
            Metric.WEIGHT_MOVED -> {
                val movedInUnit = profile.weightUnit.fromKg(abs(tdeeState.currentWeightKg - start))
                val verb = if (profile.goalDirection == GoalDirection.LOSE) "lost" else "gained"
                WidgetStat(progress, oneDecimal(movedInUnit), "$unitLabel $verb")
            }
            Metric.PERCENT -> WidgetStat(progress, "${(progress * 100).roundToInt()}%", "to goal")
            Metric.TIME_REMAINING -> {
                val daysToGoal = tdeeState.goalDate?.let { ChronoUnit.DAYS.between(today, it) }?.coerceAtLeast(0)
                val countdown = daysToGoal?.let { Phrasing.goalCountdown(it) }
                val numberText = countdown?.let { if (it.isApproximate) "~${it.amount}" else "${it.amount}" } ?: "-"
                val label = countdown?.let { "${it.unitLabel} left" } ?: "on track"
                WidgetStat(progress, numberText, label)
            }
        }
    }

    private fun oneDecimal(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
