package com.andweight.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Reimplementation of the "TDEE 3.0" spreadsheet's adaptive-TDEE algorithm.
 *
 * The spreadsheet groups daily weight/calorie entries into fixed calendar weeks
 * (anchored to a start date), forward-fills missing days within a week using the
 * most recently known average, and estimates that week's true maintenance calories as:
 *
 *   naiveTdee = avgCaloriesThisWeek + (-weightChangeThisWeekKg * kcalPerKg) / 7
 *
 * i.e. "how many calories would explain the weight change we actually saw, given what
 * was eaten". This is then smoothed with a rolling average of up to the 6 most recent
 * weeks (this week's naive estimate plus the last 5 already-finalized weekly TDEEs) so a
 * single noisy week can't swing the number, and a week with zero entries is skipped
 * entirely (carries the previous state forward unchanged).
 *
 * The whole state is recomputed from the full log history on every call rather than
 * incrementally maintained - with realistic data volumes (a few thousand days at most)
 * this is fast enough, and it avoids an entire class of "stale incremental state" bugs.
 */
object TdeeEngine {

    fun mround(value: Double, multiple: Double): Double = (value / multiple).roundToLong() * multiple

    fun ceilToHalf(value: Double): Double = ceil(value * 2.0) / 2.0

    fun initialTdeeGuess(profile: UserProfile): Double {
        val mifflinStJeor = if (profile.age != null && profile.heightCm != null && profile.sex != Sex.UNSPECIFIED) {
            val sexTerm = if (profile.sex == Sex.MALE) 5.0 else -161.0
            val bmr = 10.0 * profile.startWeightKg + 6.25 * profile.heightCm - 5.0 * profile.age + sexTerm
            bmr * profile.activityLevel.multiplier
        } else {
            null
        }
        val guess = mifflinStJeor ?: (profile.startWeightKg * EnergyConstants.MAINTENANCE_KCAL_PER_KG)
        return mround(guess, 5.0)
    }

    fun compute(profile: UserProfile, logs: List<DailyLog>, today: LocalDate): TdeeState {
        val initialGuess = initialTdeeGuess(profile)
        val logsByDate = logs.associateBy { it.date }

        var lastKnownAvgWeight = profile.startWeightKg
        var lastKnownAvgCalories = initialGuess
        val finalizedTdees = mutableListOf<Double>()
        val weekSummaries = mutableListOf<WeekSummary>()

        val lastLoggedDate = logs.maxOfOrNull { it.date }
        val daysToToday = ChronoUnit.DAYS.between(profile.startDate, today)
        val daysToLastLog = lastLoggedDate?.let { ChronoUnit.DAYS.between(profile.startDate, it) } ?: 0L
        val daysSpan = maxOf(daysToToday, daysToLastLog, 0L)
        val weekCount = (daysSpan / 7 + 1).toInt()

        for (weekIndex in 0 until weekCount) {
            val weekStart = profile.startDate.plusDays(weekIndex * 7L)
            val weekEnd = weekStart.plusDays(6)

            var hasWeight = false
            var hasCalories = false
            var prevW = lastKnownAvgWeight
            var prevC = lastKnownAvgCalories
            var sumW = 0.0
            var sumC = 0.0

            for (dayOffset in 0 until 7) {
                val log = logsByDate[weekStart.plusDays(dayOffset.toLong())]
                val filledW = log?.weightKg?.also { hasWeight = true } ?: prevW
                val filledC = log?.calories?.also { hasCalories = true } ?: prevC
                sumW += filledW
                sumC += filledC
                prevW = filledW
                prevC = filledC
            }

            if (!hasWeight && !hasCalories) continue

            val avgWeight = if (hasWeight) sumW / 7.0 else null
            val avgCalories = if (hasCalories) sumC / 7.0 else null
            val weightDelta = if (hasWeight) avgWeight!! - lastKnownAvgWeight else null

            var tdeeThisWeek: Double? = null
            if (hasWeight && hasCalories) {
                val naiveTdee = avgCalories!! + (-weightDelta!! * EnergyConstants.KCAL_PER_KG_BODYWEIGHT) / 7.0
                val window = (listOf(naiveTdee) + finalizedTdees.asReversed()).take(6)
                val rolling = window.sum() / window.size
                tdeeThisWeek = mround(rolling, 5.0)
                finalizedTdees.add(tdeeThisWeek)
            }

            weekSummaries.add(
                WeekSummary(
                    weekIndex = weekIndex + 1,
                    startDate = weekStart,
                    endDate = weekEnd,
                    hasWeightData = hasWeight,
                    hasCalorieData = hasCalories,
                    avgWeightKg = avgWeight,
                    avgCalories = avgCalories,
                    weightDeltaKg = weightDelta,
                    tdee = tdeeThisWeek,
                ),
            )

            if (hasWeight) lastKnownAvgWeight = avgWeight!!
            if (hasCalories) lastKnownAvgCalories = avgCalories!!
        }

        val currentWeight = lastKnownAvgWeight
        val currentTdee = finalizedTdees.lastOrNull() ?: initialGuess

        val targetDailyDeficit = mround(
            profile.goalRateKgPerWeek * EnergyConstants.KCAL_PER_KG_BODYWEIGHT / 7.0,
            5.0,
        )

        val rawRecommended = when (profile.goalDirection) {
            GoalDirection.MAINTAIN -> currentTdee
            GoalDirection.GAIN -> currentTdee + targetDailyDeficit
            GoalDirection.LOSE -> currentTdee - targetDailyDeficit
        }
        val floor = SafetyLimits.floorFor(profile.sex)
        val recommended = maxOf(rawRecommended, floor)

        var weeksToGoal: Double? = null
        var goalDate: LocalDate? = null
        if (profile.goalDirection != GoalDirection.MAINTAIN && profile.goalRateKgPerWeek > 0.0) {
            val remaining = abs(currentWeight - profile.goalWeightKg)
            val weeks = ceilToHalf(remaining / profile.goalRateKgPerWeek)
            weeksToGoal = weeks
            goalDate = if (weeks <= 0.0) today else today.plusDays(Math.round(weeks * 7))
        }

        return TdeeState(
            initialTdeeGuess = initialGuess,
            currentWeightKg = currentWeight,
            currentTdee = currentTdee,
            targetDailyDeficit = targetDailyDeficit,
            recommendedCalories = recommended,
            recommendedCaloriesFloorApplied = recommended > rawRecommended + 0.01,
            weeksToGoal = weeksToGoal,
            goalDate = goalDate,
            weeklyHistory = weekSummaries,
        )
    }
}

object IntakeResolver {

    fun resolveQuickPick(level: IntakeLevel, currentTdeeEstimate: Double, sex: Sex): CalorieResolution {
        val raw = currentTdeeEstimate + level.offsetKcal
        return applyFloor(raw, sex)
    }

    fun resolveExact(exactCalories: Double, sex: Sex): CalorieResolution {
        return applyFloor(exactCalories, sex)
    }

    private fun applyFloor(raw: Double, sex: Sex): CalorieResolution {
        val floor = SafetyLimits.floorFor(sex)
        val floored = maxOf(raw, floor)
        return CalorieResolution(floored, floored > raw + 0.01)
    }
}
