package com.measured.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * These expected TDEE values (3865 / 4070 / 4245 / 3865) are read directly out of the M column
 * of the original "Beslenme.xlsx" TDEE 3.0 sheet for a real user's first four weeks of data
 * (start weight 122.7 kg). They pin the engine to the spreadsheet's actual behavior, not just
 * to a reading of the formulas.
 */
class TdeeEngineTest {

    private val startDate = LocalDate.of(2019, 1, 22)

    private val profile = UserProfile(
        weightUnit = WeightUnit.KG,
        startWeightKg = 122.7,
        goalWeightKg = 88.8,
        pace = PaceOption.MODERATE,
    )

    private val week1Weight = listOf(122.7, 122.3, 121.9, 121.5, 121.1, 120.7, 120.3)
    private val week1Cal = listOf(null, null, null, null, 1313.0, 1166.0, 1247.0)
    private val week2Weight = listOf(119.9, 119.5, 119.1, 118.7, 118.3, 117.9, 117.5)
    private val week2Cal = listOf(1411.0, 1174.0, 1228.0, 1243.0, 939.0, 1199.0, 1147.0)
    private val week3Weight = listOf(116.6, 116.3, 116.0, 115.8, 115.5, 115.19999999999999, 115.0)
    private val week3Cal = listOf(2004.0, 959.0, 1568.0, 1493.0, 1396.0, 2268.0, 1280.0)
    private val week4Weight = listOf(114.7, 114.4, 114.1, 113.79999999999998, 113.5, 113.2, 113.0)
    private val week4Cal = listOf(965.0, 1950.0, 988.0, 1531.0, 756.0, 687.0, 1031.0)

    private fun buildLogs(): List<DailyLog> {
        val weeks = listOf(
            week1Weight to week1Cal,
            week2Weight to week2Cal,
            week3Weight to week3Cal,
            week4Weight to week4Cal,
        )
        val logs = mutableListOf<DailyLog>()
        weeks.forEachIndexed { weekIdx, (weights, cals) ->
            val weekStart = startDate.plusDays(weekIdx * 7L)
            for (day in 0 until 7) {
                logs.add(DailyLog(weekStart.plusDays(day.toLong()), weights[day], cals[day]))
            }
        }
        return logs
    }

    @Test
    fun `matches spreadsheet weekly TDEE exactly for four real weeks of data`() {
        val logs = buildLogs()
        val today = startDate.plusDays(27) // last day of week 4
        val state = TdeeEngine.compute(profile, logs, today)

        val weeklyTdees = state.weeklyHistory.map { it.tdee }
        assertEquals(listOf(3865.0, 4070.0, 4245.0, 3865.0), weeklyTdees)
        assertEquals(3865.0, state.currentTdee, 0.0)
        assertEquals(113.81428571428572, state.currentWeightKg, 1e-9)
    }

    @Test
    fun `initial guess before any data is weight times maintenance constant`() {
        val state = TdeeEngine.compute(profile, emptyList(), startDate)
        // 122.7 kg * 28.6601 kcal/kg, rounded to nearest 5 = 3515
        assertEquals(3515.0, state.initialTdeeGuess, 0.0)
        assertEquals(3515.0, state.currentTdee, 0.0)
        assertEquals(profile.startWeightKg, state.currentWeightKg, 0.0)
    }

    @Test
    fun `a fully skipped week carries the previous state forward unchanged`() {
        val logs = buildLogs() // 4 weeks of real data, then nothing for week 5
        val today = startDate.plusDays(34) // one full week later, no entries logged
        val state = TdeeEngine.compute(profile, logs, today)

        assertEquals(3865.0, state.currentTdee, 0.0)
        assertEquals(113.81428571428572, state.currentWeightKg, 1e-9)
        // the skipped week should not appear as an entry with data
        assertTrue(state.weeklyHistory.none { it.weekIndex == 5 && (it.hasWeightData || it.hasCalorieData) })
    }

    @Test
    fun `target daily deficit converts the pace's current weekly rate to a daily kcal number`() {
        val fastestProfile = profile.copy(pace = PaceOption.FASTEST)
        val state = TdeeEngine.compute(fastestProfile, emptyList(), startDate)
        // FASTEST at 122.7 kg: 1.5% of bodyweight (1.8405, under the 2kg cap) * 7716.17 kcal/kg / 7,
        // rounded to nearest 5.
        assertEquals(2030.0, state.targetDailyDeficit, 0.0)
    }

    @Test
    fun `recommended calories is current TDEE minus deficit when losing`() {
        // The slowest tier, so the deficit stays well clear of the safety floor.
        val relaxedProfile = profile.copy(pace = PaceOption.RELAXED)
        val state = TdeeEngine.compute(relaxedProfile, emptyList(), startDate)
        assertEquals(state.currentTdee - state.targetDailyDeficit, state.recommendedCalories, 0.0)
        assertTrue(!state.recommendedCaloriesFloorApplied)
    }

    @Test
    fun `recommended calories never drops below the safety floor for women`() {
        // A lighter starting weight keeps the initial maintenance guess low while the fastest
        // safe deficit is still large relative to it, so it's enough to force flooring even
        // though the weekly rate itself is capped for safety.
        val aggressiveProfile = profile.copy(startWeightKg = 80.0, goalWeightKg = 60.0, pace = PaceOption.FASTEST, sex = Sex.FEMALE)
        val state = TdeeEngine.compute(aggressiveProfile, emptyList(), startDate)
        assertTrue(state.recommendedCalories >= SafetyLimits.FEMALE_FLOOR_KCAL)
        assertTrue(state.recommendedCaloriesFloorApplied)
    }

    @Test
    fun `weeks to goal is null when goal is to maintain weight`() {
        val maintainProfile = profile.copy(goalWeightKg = profile.startWeightKg, pace = null)
        val state = TdeeEngine.compute(maintainProfile, emptyList(), startDate)
        assertNull(state.weeksToGoal)
        assertNull(state.goalDate)
        assertEquals(state.currentTdee, state.recommendedCalories, 0.0)
    }

    @Test
    fun `weeks to goal accounts for the weekly rate shrinking as weight approaches goal`() {
        // A big enough gap that the percentage-of-bodyweight rate meaningfully tapers rather than
        // staying flat at the cap the whole way, so a naive fixed-rate division would understate
        // how long it actually takes.
        val bigLossProfile = profile.copy(startWeightKg = 150.0, goalWeightKg = 100.0, pace = PaceOption.FASTEST)
        val state = TdeeEngine.compute(bigLossProfile, emptyList(), startDate)
        val weeksToGoal = requireNotNull(state.weeksToGoal)

        val startingRateWeeks = (150.0 - 100.0) / PaceGuidance.rateForOption(PaceOption.FASTEST, 150.0, GoalDirection.LOSE)
        assertTrue(weeksToGoal > startingRateWeeks)
    }

    @Test
    fun `earliestKnownWeightKg reflects the first week that actually had a weight entry`() {
        val logs = buildLogs()
        val state = TdeeEngine.compute(profile, logs, startDate.plusDays(27))
        // Week 1's average weight (see the shared fixture above), not the profile's start weight.
        assertEquals(121.5, state.earliestKnownWeightKg, 1e-9)
    }

    @Test
    fun `earliestKnownWeightKg falls back to the profile's start weight when nothing is logged`() {
        val state = TdeeEngine.compute(profile, emptyList(), startDate)
        assertEquals(profile.startWeightKg, state.earliestKnownWeightKg, 0.0)
    }

    @Test
    fun `estimated TDEE never goes negative even in an extreme scenario`() {
        // Contrived: weight rising sharply while eating very little would, unclamped, imply a
        // deeply negative maintenance calorie estimate. It must be floored at zero instead.
        val logs = listOf(
            DailyLog(startDate, weightKg = 90.0, calories = 500.0),
            DailyLog(startDate.plusDays(6), weightKg = 140.0, calories = 500.0),
        )
        val extremeProfile = profile.copy(startWeightKg = 90.0)
        val state = TdeeEngine.compute(extremeProfile, logs, startDate.plusDays(6))
        assertTrue(state.currentTdee >= 0.0)
        assertTrue(state.weeklyHistory.all { (it.tdee ?: 0.0) >= 0.0 })
    }

    @Test
    fun `quick-pick intake levels offset the current TDEE estimate`() {
        val resolution = IntakeResolver.resolveQuickPick(IntakeLevel.LESS_THAN_NORMAL, 2500.0, Sex.MALE)
        // 2500 * (1 - 0.18) = 2050
        assertEquals(2050.0, resolution.calories, 0.0)
        assertTrue(!resolution.floorApplied)
    }

    @Test
    fun `starving quick-pick is clamped to the safety floor for a low TDEE`() {
        val resolution = IntakeResolver.resolveQuickPick(IntakeLevel.STARVING, 1800.0, Sex.FEMALE)
        // 1800 * (1 - 0.40) = 1080, below the 1200 floor for women
        assertEquals(1200.0, resolution.calories, 0.0)
        assertTrue(resolution.floorApplied)
    }

    @Test
    fun `exact entry mode keeps the actual value and flags it instead of clamping`() {
        val resolution = IntakeResolver.resolveExact(900.0, Sex.MALE)
        assertEquals(900.0, resolution.calories, 0.0)
        assertTrue(!resolution.floorApplied)
        assertTrue(resolution.belowFloor)
    }

    @Test
    fun `exact entry above the safety floor is not flagged`() {
        val resolution = IntakeResolver.resolveExact(1800.0, Sex.MALE)
        assertEquals(1800.0, resolution.calories, 0.0)
        assertTrue(!resolution.belowFloor)
    }

    @Test
    fun `initial guess with no demographic details at all uses the crude bodyweight formula`() {
        val bareProfile = profile.copy(sex = Sex.UNSPECIFIED, heightCm = null, age = null)
        val guess = TdeeEngine.initialTdeeGuess(bareProfile)
        assertEquals(3515.0, guess, 0.0) // same as the "no data at all" spreadsheet-matching test above
    }

    @Test
    fun `a single demographic detail already sharpens the estimate away from the crude formula`() {
        val bareGuess = TdeeEngine.initialTdeeGuess(profile.copy(sex = Sex.UNSPECIFIED, heightCm = null, age = null))
        val heightOnlyGuess = TdeeEngine.initialTdeeGuess(profile.copy(sex = Sex.UNSPECIFIED, heightCm = 170.0, age = null))
        assertTrue(heightOnlyGuess != bareGuess)
    }

    @Test
    fun `sex left unspecified still uses Mifflin-St Jeor with an averaged sex term once other details are present`() {
        val partialProfile = profile.copy(sex = Sex.UNSPECIFIED, heightCm = 170.0, age = 40)
        // BMR = 10*122.7 + 6.25*170 - 5*40 + (-78), * LIGHT multiplier, rounded to nearest 5.
        val expectedBmr = 10.0 * 122.7 + 6.25 * 170.0 - 5.0 * 40.0 - 78.0
        val expected = TdeeEngine.mround(expectedBmr * ActivityLevel.LIGHT.multiplier, 5.0)
        assertEquals(expected, TdeeEngine.initialTdeeGuess(partialProfile), 0.0)
    }

    @Test
    fun `fully specified demographics match Mifflin-St Jeor exactly`() {
        val fullProfile = profile.copy(sex = Sex.FEMALE, heightCm = 165.0, age = 30, activityLevel = ActivityLevel.MODERATE)
        val expectedBmr = 10.0 * 122.7 + 6.25 * 165.0 - 5.0 * 30.0 - 161.0
        val expected = TdeeEngine.mround(expectedBmr * ActivityLevel.MODERATE.multiplier, 5.0)
        assertEquals(expected, TdeeEngine.initialTdeeGuess(fullProfile), 0.0)
    }

    @Test
    fun `body fat percent uses Katch-McArdle from lean body mass`() {
        val bodyFatProfile = profile.copy(bodyFatPercent = 25.0, activityLevel = ActivityLevel.MODERATE)
        // Lean mass = 122.7 * 0.75, BMR = 370 + 21.6 * leanMassKg, * MODERATE multiplier.
        val leanMassKg = 122.7 * 0.75
        val expectedBmr = 370.0 + 21.6 * leanMassKg
        val expected = TdeeEngine.mround(expectedBmr * ActivityLevel.MODERATE.multiplier, 5.0)
        assertEquals(expected, TdeeEngine.initialTdeeGuess(bodyFatProfile), 0.0)
    }

    @Test
    fun `body fat percent takes priority over sex, height, and age when both are present`() {
        val bothProfile = profile.copy(bodyFatPercent = 20.0, sex = Sex.MALE, heightCm = 180.0, age = 25)
        val bodyFatOnly = profile.copy(bodyFatPercent = 20.0)
        assertEquals(TdeeEngine.initialTdeeGuess(bodyFatOnly), TdeeEngine.initialTdeeGuess(bothProfile), 0.0)
    }
}
