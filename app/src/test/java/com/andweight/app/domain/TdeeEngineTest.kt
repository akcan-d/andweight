package com.andweight.app.domain

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
        startDate = startDate,
        weightUnit = WeightUnit.KG,
        startWeightKg = 122.7,
        goalWeightKg = 88.8,
        goalRateKgPerWeek = 2.3,
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
    fun `target daily deficit converts goal rate to a daily kcal number`() {
        val state = TdeeEngine.compute(profile, emptyList(), startDate)
        // 2.3 kg/week * 7716.17 kcal/kg / 7 days, rounded to nearest 5
        assertEquals(2535.0, state.targetDailyDeficit, 0.0)
    }

    @Test
    fun `recommended calories is current TDEE minus deficit when losing`() {
        // A modest 0.5 kg/week goal, so the deficit stays well clear of the safety floor.
        val modestProfile = profile.copy(goalRateKgPerWeek = 0.5)
        val state = TdeeEngine.compute(modestProfile, emptyList(), startDate)
        assertEquals(state.currentTdee - state.targetDailyDeficit, state.recommendedCalories, 0.0)
        assertTrue(!state.recommendedCaloriesFloorApplied)
    }

    @Test
    fun `recommended calories never drops below the safety floor for women`() {
        val aggressiveProfile = profile.copy(goalRateKgPerWeek = 5.0, sex = Sex.FEMALE)
        val state = TdeeEngine.compute(aggressiveProfile, emptyList(), startDate)
        assertTrue(state.recommendedCalories >= SafetyLimits.FEMALE_FLOOR_KCAL)
        assertTrue(state.recommendedCaloriesFloorApplied)
    }

    @Test
    fun `weeks to goal is null when goal is to maintain weight`() {
        val maintainProfile = profile.copy(goalWeightKg = profile.startWeightKg, goalRateKgPerWeek = 0.0)
        val state = TdeeEngine.compute(maintainProfile, emptyList(), startDate)
        assertNull(state.weeksToGoal)
        assertNull(state.goalDate)
        assertEquals(state.currentTdee, state.recommendedCalories, 0.0)
    }

    @Test
    fun `quick-pick intake levels offset the current TDEE estimate`() {
        val resolution = IntakeResolver.resolveQuickPick(IntakeLevel.LESS_THAN_NORMAL, 2500.0, Sex.MALE)
        assertEquals(2150.0, resolution.calories, 0.0)
        assertTrue(!resolution.floorApplied)
    }

    @Test
    fun `starving quick-pick is clamped to the safety floor for a low TDEE`() {
        val resolution = IntakeResolver.resolveQuickPick(IntakeLevel.STARVING, 1800.0, Sex.FEMALE)
        // 1800 - 750 = 1050, below the 1200 floor for women
        assertEquals(1200.0, resolution.calories, 0.0)
        assertTrue(resolution.floorApplied)
    }

    @Test
    fun `exact entry mode is also clamped to the safety floor`() {
        val resolution = IntakeResolver.resolveExact(900.0, Sex.MALE)
        assertEquals(1500.0, resolution.calories, 0.0)
        assertTrue(resolution.floorApplied)
    }
}
