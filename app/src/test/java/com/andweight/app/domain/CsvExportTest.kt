package com.andweight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CsvExportTest {

    private val profile = UserProfile(
        weightUnit = WeightUnit.KG,
        startWeightKg = 90.0,
        goalWeightKg = 80.0,
        pace = PaceOption.MODERATE,
    )

    @Test
    fun `header and rows for kg profile with mixed entries`() {
        val logs = listOf(
            DailyLog(LocalDate.of(2026, 1, 2), weightKg = 90.0, calories = 2000.0, intakeLevel = IntakeLevel.AVERAGE),
            DailyLog(LocalDate.of(2026, 1, 1), weightKg = 91.0, calories = null),
        )
        val csv = CsvExport.buildCsv(profile, logs)
        val lines = csv.trimEnd('\n').split("\n")

        assertEquals("date,weight_kg,calories,intake_level,exact_entry", lines[0])
        // Sorted by date ascending regardless of input order.
        assertEquals("2026-01-01,91,,,false", lines[1])
        assertEquals("2026-01-02,90,2000,AVERAGE,false", lines[2])
    }

    @Test
    fun `imperial profile converts weight to pounds`() {
        val lbProfile = profile.copy(weightUnit = WeightUnit.LB)
        val logs = listOf(DailyLog(LocalDate.of(2026, 1, 1), weightKg = 100.0, calories = null))
        val csv = CsvExport.buildCsv(lbProfile, logs)
        val lines = csv.trimEnd('\n').split("\n")

        assertEquals("date,weight_lb,calories,intake_level,exact_entry", lines[0])
        assertEquals("2026-01-01,220.46,,,false", lines[1])
    }

    @Test
    fun `no profile defaults to kg header`() {
        val csv = CsvExport.buildCsv(null, emptyList())
        assertEquals("date,weight_kg,calories,intake_level,exact_entry", csv.trim())
    }
}
