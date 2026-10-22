package com.measured.app

import androidx.test.core.app.ApplicationProvider
import com.measured.app.domain.DailyLog
import com.measured.app.domain.IntakeLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * [com.measured.app.data.AndWeightRepository.restoreLog] exists specifically so restoring from a
 * backup can't silently erase a field the backup doesn't carry for a given day - these pin that
 * behavior down, since a plain overwrite would have been a much easier (and wrong) thing to write
 * here by mistake.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndWeightRepositoryRestoreLogTest {

    private val date = LocalDate.of(2026, 5, 1)

    @Test
    fun `a backup row missing calories doesn't erase calories already on the device`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AndWeightApplication>()
        app.deleteDatabase("andweight.db")

        app.repository.saveLog(date, weightKg = 80.0, calories = 2200.0, intakeLevel = IntakeLevel.AVERAGE, wasExact = false)

        // Mirrors a backup taken before that day's meal was logged - weight only.
        app.repository.restoreLog(DailyLog(date, weightKg = 79.5, calories = null, intakeLevel = null, wasExactEntry = false))

        val result = app.repository.getLogForDate(date)
        assertEquals(79.5, result?.weightKg ?: 0.0, 0.01)
        assertEquals(2200.0, result?.calories ?: 0.0, 0.01)
        assertEquals(IntakeLevel.AVERAGE, result?.intakeLevel)
    }

    @Test
    fun `a backup row missing weight doesn't erase weight already on the device`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AndWeightApplication>()
        app.deleteDatabase("andweight.db")

        app.repository.saveLog(date, weightKg = 80.0, calories = null, intakeLevel = null, wasExact = false)

        app.repository.restoreLog(DailyLog(date, weightKg = null, calories = 1900.0, intakeLevel = null, wasExactEntry = true))

        val result = app.repository.getLogForDate(date)
        assertEquals(80.0, result?.weightKg ?: 0.0, 0.01)
        assertEquals(1900.0, result?.calories ?: 0.0, 0.01)
        assertEquals(true, result?.wasExactEntry)
    }

    @Test
    fun `a backup row that does carry calories replaces the device's calorie info as a whole group`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AndWeightApplication>()
        app.deleteDatabase("andweight.db")

        app.repository.saveLog(date, weightKg = 80.0, calories = 2200.0, intakeLevel = IntakeLevel.AVERAGE, wasExact = false)

        app.repository.restoreLog(DailyLog(date, weightKg = null, calories = 1800.0, intakeLevel = null, wasExactEntry = true))

        val result = app.repository.getLogForDate(date)
        assertEquals(80.0, result?.weightKg ?: 0.0, 0.01) // weight untouched, backup didn't carry it
        assertEquals(1800.0, result?.calories ?: 0.0, 0.01) // backup's calorie info wins as a group
        assertEquals(true, result?.wasExactEntry)
        assertEquals(null, result?.intakeLevel)
    }
}
