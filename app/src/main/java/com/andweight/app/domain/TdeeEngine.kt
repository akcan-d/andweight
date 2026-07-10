package com.andweight.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
        return mround(guess, 5.0).coerceAtLeast(0.0)
    }

    fun compute(profile: UserProfile, logs: List<DailyLog>, today: LocalDate): TdeeState {
        val initialGuess = initialTdeeGuess(profile)
        val logsByDate = logs.associateBy { it.date }

        var lastKnownAvgWeight = profile.startWeightKg
        var lastKnownAvgCalories = initialGuess
        var earliestKnownWeightKg = profile.startWeightKg
        var sawWeightData = false
        val finalizedTdees = mutableListOf<Double>()
        val weekSummaries = mutableListOf<WeekSummary>()

        // Weekly windows are anchored to the oldest logged day, rather than a separately stored
        // "start date" - so backfilling history from before you started using the app just
        // folds naturally into the weekly averages instead of needing a manually-adjusted anchor.
        val firstLoggedDate = logs.minOfOrNull { it.date }
        val anchorDate = firstLoggedDate ?: today

        val lastLoggedDate = logs.maxOfOrNull { it.date }
        val daysToToday = ChronoUnit.DAYS.between(anchorDate, today)
        val daysToLastLog = lastLoggedDate?.let { ChronoUnit.DAYS.between(anchorDate, it) } ?: 0L
        val daysSpan = maxOf(daysToToday, daysToLastLog, 0L)
        val weekCount = (daysSpan / 7 + 1).toInt()

        for (weekIndex in 0 until weekCount) {
            val weekStart = anchorDate.plusDays(weekIndex * 7L)
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

            if (hasWeight && !sawWeightData) {
                earliestKnownWeightKg = avgWeight!!
                sawWeightData = true
            }

            var tdeeThisWeek: Double? = null
            if (hasWeight && hasCalories) {
                val naiveTdee = avgCalories!! + (-weightDelta!! * EnergyConstants.KCAL_PER_KG_BODYWEIGHT) / 7.0
                val window = (listOf(naiveTdee) + finalizedTdees.asReversed()).take(6)
                val rolling = window.sum() / window.size
                tdeeThisWeek = mround(rolling, 5.0).coerceAtLeast(0.0)
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

        // The weekly rate is a percentage of body weight, so it's re-derived against *current*
        // weight rather than read back as a number fixed at onboarding - someone losing weight
        // eats a little closer to maintenance each week as they get lighter, not the same fixed
        // deficit forever.
        val currentWeeklyRateKg = if (profile.goalDirection != GoalDirection.MAINTAIN && profile.pace != null) {
            PaceGuidance.rateForOption(profile.pace, currentWeight, profile.goalDirection)
        } else {
            0.0
        }

        val targetDailyDeficit = mround(
            currentWeeklyRateKg * EnergyConstants.KCAL_PER_KG_BODYWEIGHT / 7.0,
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
        if (profile.goalDirection != GoalDirection.MAINTAIN && profile.pace != null) {
            val weeks = simulateWeeksToGoal(currentWeight, profile.goalWeightKg, profile.pace, profile.goalDirection)
            if (weeks != null) {
                val rounded = ceilToHalf(weeks)
                weeksToGoal = rounded
                goalDate = if (rounded <= 0.0) today else today.plusDays(Math.round(rounded * 7))
            }
        }

        return TdeeState(
            initialTdeeGuess = initialGuess,
            currentWeightKg = currentWeight,
            earliestKnownWeightKg = earliestKnownWeightKg,
            currentTdee = currentTdee,
            targetDailyDeficit = targetDailyDeficit,
            recommendedCalories = recommended,
            recommendedCaloriesFloorApplied = recommended > rawRecommended + 0.01,
            weeksToGoal = weeksToGoal,
            goalDate = goalDate,
            weeklyHistory = weekSummaries,
        )
    }

    /**
     * Projects a goal date week by week rather than with a single division, since the weekly
     * rate is a percentage of body weight and shrinks (for loss) or grows (for gain) as the
     * simulated weight moves - a fixed-rate division would over/underestimate how long it
     * actually takes once you're no longer at your starting weight.
     */
    private fun simulateWeeksToGoal(
        startWeightKg: Double,
        goalWeightKg: Double,
        pace: PaceOption,
        direction: GoalDirection,
    ): Double? {
        var weight = startWeightKg
        var weeks = 0.0
        repeat(2000) {
            val remaining = if (direction == GoalDirection.LOSE) weight - goalWeightKg else goalWeightKg - weight
            if (remaining <= 0.0) return weeks
            val rate = PaceGuidance.rateForOption(pace, weight, direction)
            if (rate <= 0.0) return null
            if (remaining <= rate) return weeks + remaining / rate
            weight += if (direction == GoalDirection.LOSE) -rate else rate
            weeks += 1.0
        }
        return null
    }
}

object IntakeResolver {

    /** A quick-pick answer is a formula-derived estimate, not a factual report - so it's safe (and
     * intended) to clamp it up to the safety floor. */
    fun resolveQuickPick(level: IntakeLevel, currentTdeeEstimate: Double, sex: Sex): CalorieResolution {
        val raw = currentTdeeEstimate + level.offsetKcal
        val floor = SafetyLimits.floorFor(sex)
        val floored = maxOf(raw, floor)
        return CalorieResolution(floored, floorApplied = floored > raw + 0.01)
    }

    /** An exact entry is what someone actually ate, so it's kept as typed even if it's below the
     * safety floor - the UI shows a caution instead of silently overriding a real number. */
    fun resolveExact(exactCalories: Double, sex: Sex): CalorieResolution {
        val floor = SafetyLimits.floorFor(sex)
        return CalorieResolution(exactCalories, floorApplied = false, belowFloor = exactCalories < floor - 0.01)
    }
}
