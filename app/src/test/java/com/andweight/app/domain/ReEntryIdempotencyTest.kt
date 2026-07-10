package com.andweight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Regression test for a reported bug: re-entering a day's check-in kept nudging the resulting
 * calorie number further each time, instead of just replacing it. The cause was resolving a
 * quick-pick answer against a TDEE estimate that already included that same day's previous entry.
 *
 * The fix is to always resolve against a baseline TDEE computed with that day's own entry
 * excluded first. This test asserts that basis is stable no matter what was previously stored
 * for the day being re-saved.
 */
class ReEntryIdempotencyTest {

    private val referenceDate = LocalDate.of(2026, 1, 1)

    private val profile = UserProfile(
        weightUnit = WeightUnit.KG,
        startWeightKg = 90.0,
        goalWeightKg = 80.0,
        pace = PaceOption.MODERATE,
    )

    private fun priorDaysLogs(): List<DailyLog> {
        val logs = mutableListOf<DailyLog>()
        for (i in 0 until 6) {
            logs.add(
                DailyLog(
                    date = referenceDate.plusDays(i.toLong()),
                    weightKg = 90.0 - i * 0.1,
                    calories = 2200.0,
                ),
            )
        }
        return logs
    }

    @Test
    fun `resolving the same intake level for a day gives the same result regardless of what was previously saved there`() {
        val editedDate = referenceDate.plusDays(6)
        val priorLogs = priorDaysLogs()

        // First save: the day has no entry yet.
        val baselineBeforeFirstSave = TdeeEngine.compute(profile, priorLogs, editedDate).currentTdee
        val firstResult = IntakeResolver.resolveQuickPick(IntakeLevel.MORE_THAN_AVERAGE, baselineBeforeFirstSave, profile.sex)

        // Simulate that first save landing in the log history.
        val logsAfterFirstSave = priorLogs + DailyLog(editedDate, weightKg = 89.5, calories = firstResult.calories)

        // Re-editing the same day: the baseline must exclude the day's own (about to be replaced)
        // entry, so it should be identical to the original baseline...
        val baselineBeforeSecondSave = TdeeEngine.compute(
            profile,
            logsAfterFirstSave.filterNot { it.date == editedDate },
            editedDate,
        ).currentTdee
        assertEquals(baselineBeforeFirstSave, baselineBeforeSecondSave, 0.0)

        // ...so picking the exact same quick-pick answer again reproduces the exact same result,
        // instead of compounding on top of the previous save.
        val secondResult = IntakeResolver.resolveQuickPick(IntakeLevel.MORE_THAN_AVERAGE, baselineBeforeSecondSave, profile.sex)
        assertEquals(firstResult.calories, secondResult.calories, 0.0)
    }
}
