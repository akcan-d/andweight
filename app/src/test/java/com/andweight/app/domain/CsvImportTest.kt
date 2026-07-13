package com.andweight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvImportTest {

    @Test
    fun `round trips its own kg export exactly`() {
        val profile = UserProfile(weightUnit = WeightUnit.KG, startWeightKg = 90.0, goalWeightKg = 80.0)
        val logs = listOf(
            DailyLog(LocalDate.of(2026, 1, 1), weightKg = 90.0, calories = 2000.0, intakeLevel = IntakeLevel.AVERAGE),
            DailyLog(LocalDate.of(2026, 1, 2), weightKg = 89.5, calories = null),
        )
        val csv = CsvExport.buildCsv(profile, logs)
        val result = CsvImport.parse(csv)

        assertEquals(0, result.skippedRowCount)
        assertEquals(2, result.logs.size)
        assertEquals(90.0, result.logs[0].weightKg!!, 0.01)
        assertEquals(2000.0, result.logs[0].calories!!, 0.01)
        assertEquals(IntakeLevel.AVERAGE, result.logs[0].intakeLevel)
        assertEquals(89.5, result.logs[1].weightKg!!, 0.01)
        assertNull(result.logs[1].calories)
    }

    @Test
    fun `converts an imperial export back to kg`() {
        val profile = UserProfile(weightUnit = WeightUnit.LB, startWeightKg = 90.0, goalWeightKg = 80.0)
        val logs = listOf(DailyLog(LocalDate.of(2026, 1, 1), weightKg = 90.0, calories = null))
        val csv = CsvExport.buildCsv(profile, logs)
        val result = CsvImport.parse(csv)

        assertEquals(1, result.logs.size)
        assertEquals(90.0, result.logs[0].weightKg!!, 0.05) // small tolerance for the kg<->lb round trip
    }

    @Test
    fun `skips rows with an unparseable date`() {
        val csv = "date,weight_kg,calories,intake_level,exact_entry\nnot-a-date,90,2000,AVERAGE,false\n"
        val result = CsvImport.parse(csv)
        assertEquals(0, result.logs.size)
        assertEquals(1, result.skippedRowCount)
    }

    @Test
    fun `skips rows with neither a weight nor a calorie value`() {
        val csv = "date,weight_kg,calories,intake_level,exact_entry\n2026-01-01,,,, \n"
        val result = CsvImport.parse(csv)
        assertEquals(0, result.logs.size)
        assertEquals(1, result.skippedRowCount)
    }

    @Test
    fun `an unrecognized intake level is dropped but the row is still kept`() {
        val csv = "date,weight_kg,calories,intake_level,exact_entry\n2026-01-01,90,2000,NOT_A_REAL_LEVEL,false\n"
        val result = CsvImport.parse(csv)
        assertEquals(1, result.logs.size)
        assertNull(result.logs[0].intakeLevel)
    }

    @Test
    fun `empty csv produces no logs and no skipped rows`() {
        val result = CsvImport.parse("")
        assertTrue(result.logs.isEmpty())
        assertEquals(0, result.skippedRowCount)
    }

    @Test
    fun `missing required columns skips every data row`() {
        val csv = "foo,bar\n1,2\n3,4\n"
        val result = CsvImport.parse(csv)
        assertTrue(result.logs.isEmpty())
        assertEquals(2, result.skippedRowCount)
    }
}
