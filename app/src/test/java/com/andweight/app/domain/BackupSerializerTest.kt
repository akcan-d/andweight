package com.andweight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupSerializerTest {

    private val profile = UserProfile(
        weightUnit = WeightUnit.KG,
        startWeightKg = 90.0,
        goalWeightKg = 80.0,
        pace = PaceOption.MODERATE,
        sex = Sex.MALE,
        heightCm = 180.0,
        age = 30,
        activityLevel = ActivityLevel.MODERATE,
        bodyFatPercent = 22.0,
    )

    private val settings = BackupSerializer.Settings(
        themeMode = ThemeMode.DARK,
        tipsEnabled = false,
        reminderEnabled = true,
        reminderHour = 8,
        reminderMinute = 30,
    )

    private val logs = listOf(
        DailyLog(LocalDate.of(2026, 1, 1), weightKg = 90.0, calories = 2000.0, intakeLevel = IntakeLevel.AVERAGE),
        DailyLog(LocalDate.of(2026, 1, 2), weightKg = 89.5, calories = null),
    )

    @Test
    fun `round trips profile, settings and logs exactly`() {
        val text = BackupSerializer.build(profile, settings, logs)
        val result = BackupSerializer.parse(text)!!

        assertEquals(profile, result.profile)
        assertEquals(settings, result.settings)
        assertEquals(0, result.skippedLogRowCount)
        assertEquals(2, result.logs.size)
        assertEquals(90.0, result.logs[0].weightKg!!, 0.01)
        assertEquals(2000.0, result.logs[0].calories!!, 0.01)
        assertEquals(IntakeLevel.AVERAGE, result.logs[0].intakeLevel)
        assertEquals(89.5, result.logs[1].weightKg!!, 0.01)
        assertNull(result.logs[1].calories)
    }

    @Test
    fun `a profile with no optional fields set round trips too`() {
        val bareProfile = UserProfile(weightUnit = WeightUnit.LB, startWeightKg = 150.0, goalWeightKg = 140.0)
        val text = BackupSerializer.build(bareProfile, null, emptyList())
        val result = BackupSerializer.parse(text)!!

        assertEquals(bareProfile, result.profile)
        assertNull(result.settings)
        assertTrue(result.logs.isEmpty())
    }

    @Test
    fun `a file without the magic header is not recognized as a backup`() {
        assertNull(BackupSerializer.parse("date,weight_kg,calories\n2026-01-01,90,2000\n"))
    }

    @Test
    fun `skips unreadable log rows but keeps the rest`() {
        val text = BackupSerializer.build(profile, null, emptyList()) +
            "not,a,valid,row,here\n2026-02-01,88.0,,,false\n"
        val result = BackupSerializer.parse(text)!!

        assertEquals(1, result.skippedLogRowCount)
        assertEquals(1, result.logs.size)
        assertEquals(LocalDate.of(2026, 2, 1), result.logs[0].date)
    }
}
