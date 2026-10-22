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
 * short label underneath that number (shown only at the larger sizes - see [MinimalWidgetContent]).
 * [locked] means the person placed the widget but isn't a supporter - rendered as a lock instead
 * of real data rather than leaving the widget off the picker entirely, since the perk is a nicer
 * placement rather than something to hide from non-supporters. */
data class WidgetStat(val ringProgress: Float, val numberText: String, val label: String, val locked: Boolean = false)

/** WEIGHT_MOVED and PERCENT share one ring (weight-based progress toward the goal, same meaning
 * as the in-app Status ring) and differ only in what number sits at its center - two ways of
 * reading the same underlying fact. TIME_REMAINING gets its own ring instead of reusing that one:
 * its number is about elapsed time, not weight moved, so a weight-based ring next to it would
 * visually claim something the number doesn't say. This also means placing multiple widgets
 * together shows genuinely different rings, not three copies of the same sweep. */
object WidgetStats {

    enum class Metric { WEIGHT_MOVED, PERCENT, TIME_REMAINING }

    suspend fun compute(context: Context, metric: Metric): WidgetStat? {
        val app = context.applicationContext as AndWeightApplication
        val profile = app.repository.getProfileOnce() ?: return null
        if (!app.settingsRepository.isSupporterOnce()) {
            return WidgetStat(ringProgress = 0f, numberText = "", label = "Supporter perk", locked = true)
        }
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
                val goalDate = tdeeState.goalDate
                val startDate = logs.minOfOrNull { it.date }
                // How much of the estimated time budget (first logged day to the goal date) has
                // elapsed - not [progress] (weight moved), which would show a ring that doesn't
                // match what the center number is actually counting down.
                val timeProgress = if (startDate != null && goalDate != null && goalDate > startDate) {
                    val totalDays = ChronoUnit.DAYS.between(startDate, goalDate).toFloat()
                    val elapsedDays = ChronoUnit.DAYS.between(startDate, today).toFloat()
                    (elapsedDays / totalDays).coerceIn(0f, 1f)
                } else {
                    progress
                }
                val daysToGoal = goalDate?.let { ChronoUnit.DAYS.between(today, it) }?.coerceAtLeast(0)
                val countdown = daysToGoal?.let { Phrasing.goalCountdown(it) }
                val numberText = countdown?.let { if (it.isApproximate) "~${it.amount}" else "${it.amount}" } ?: "-"
                val label = countdown?.let { "${it.unitLabel} left" } ?: "on track"
                WidgetStat(timeProgress, numberText, label)
            }
        }
    }

    private fun oneDecimal(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
