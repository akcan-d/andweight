package com.measured.app.data

import android.content.Context
import com.measured.app.domain.ActivityLevel
import com.measured.app.domain.DailyLog
import com.measured.app.domain.IntakeLevel
import com.measured.app.domain.PaceOption
import com.measured.app.domain.Sex
import com.measured.app.domain.TdeeEngine
import com.measured.app.domain.TdeeState
import com.measured.app.domain.UserProfile
import com.measured.app.domain.WeightUnit
import com.measured.app.health.HealthConnectManager
import com.measured.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.math.abs

/** What a Health Connect import would do, before anything is actually written - [newReadings]
 * (days with nothing logged yet) are always safe to apply; [conflictingReadings] (days already
 * logged with a different weight) need the user to pick overwrite-or-keep first. See
 * [AndWeightRepository.planHealthConnectImport]/[AndWeightRepository.applyHealthConnectImport]. */
data class HealthConnectImportPlan(
    val newReadings: List<Pair<LocalDate, Double>>,
    val conflictingReadings: List<Pair<LocalDate, Double>>,
)

class AndWeightRepository(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val healthConnectManager: HealthConnectManager,
) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(context)
    private val profileDao = db.profileDao()
    private val logDao = db.dailyLogDao()

    val profileFlow: Flow<UserProfile?> = profileDao.observeProfile().map { it?.toDomain() }

    val logsFlow: Flow<List<DailyLog>> = logDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Recomputed from the full history on every emission - see [TdeeEngine] for why that's cheap and safe. */
    fun tdeeStateFlow(today: LocalDate = LocalDate.now()): Flow<TdeeState?> =
        combine(profileFlow, logsFlow) { profile, logs ->
            profile?.let { TdeeEngine.compute(it, logs, today) }
        }

    suspend fun saveProfile(profile: UserProfile) {
        profileDao.upsert(profile.toEntity())
        WidgetUpdater.refresh(appContext)
    }

    suspend fun getProfileOnce(): UserProfile? = profileDao.getProfileOnce()?.toDomain()

    suspend fun getLogForDate(date: LocalDate): DailyLog? =
        logDao.getByDate(date.toEpochDayLong())?.toDomain()

    suspend fun deleteLog(date: LocalDate) {
        logDao.deleteByDate(date.toEpochDayLong())
        WidgetUpdater.refresh(appContext)
    }

    /** Used only when restoring from a backup file - merges the backup's row into whatever's
     * already saved for that date instead of blindly overwriting it, so a field the backup
     * doesn't carry (e.g. an older export taken before that day's meal was logged) can't wipe out
     * a field that only ever existed on this device. Weight is merged on its own; calories,
     * intake level and the exact-entry flag are merged as one group, since intake level and
     * exact-entry only mean anything together with a specific calories value. */
    suspend fun restoreLog(log: DailyLog) {
        val existing = getLogForDate(log.date)
        val weightKg = log.weightKg ?: existing?.weightKg
        val backupHasCalories = log.calories != null
        saveLog(
            date = log.date,
            weightKg = weightKg,
            calories = if (backupHasCalories) log.calories else existing?.calories,
            intakeLevel = if (backupHasCalories) log.intakeLevel else existing?.intakeLevel,
            wasExact = if (backupHasCalories) log.wasExactEntry else (existing?.wasExactEntry ?: false),
        )
    }

    suspend fun saveLog(date: LocalDate, weightKg: Double?, calories: Double?, intakeLevel: IntakeLevel?, wasExact: Boolean) {
        persistLog(date, weightKg, calories, intakeLevel, wasExact)
        // Mirrors into Health Connect so a smart scale's own app, or any other app the user has
        // granted read access to, sees the same number - every path that logs a weight (manual
        // entry, quick-pick, backup restore) goes through this one function, so this is the only
        // place that needs to know about the sync setting. No-ops quietly if sync is off or
        // permission was never granted - see HealthConnectManager.writeWeight.
        if (weightKg != null && settingsRepository.healthConnectSyncEnabledOnce()) {
            healthConnectManager.writeWeight(date, weightKg)
        }
    }

    private suspend fun persistLog(date: LocalDate, weightKg: Double?, calories: Double?, intakeLevel: IntakeLevel?, wasExact: Boolean) {
        logDao.upsert(
            DailyLogEntity(
                dateEpochDay = date.toEpochDayLong(),
                weightKg = weightKg,
                calories = calories,
                intakeLevel = intakeLevel?.name,
                wasExactEntry = wasExact,
            ),
        )
        WidgetUpdater.refresh(appContext)
    }

    /** Sorts whatever Health Connect has on file - a smart scale's own app, most likely - into
     * days that are safe to fill in right away and days that conflict with a weight already
     * logged here, without writing anything yet. A day whose Health Connect reading already
     * matches (within rounding) what's logged is left out of both lists - there's nothing to do
     * for it either way. */
    suspend fun planHealthConnectImport(): HealthConnectImportPlan {
        val newReadings = mutableListOf<Pair<LocalDate, Double>>()
        val conflictingReadings = mutableListOf<Pair<LocalDate, Double>>()
        for ((date, weightKg) in healthConnectManager.readRecentWeights()) {
            val existingWeightKg = getLogForDate(date)?.weightKg
            when {
                existingWeightKg == null -> newReadings += date to weightKg
                abs(existingWeightKg - weightKg) > 0.01 -> conflictingReadings += date to weightKg
            }
        }
        return HealthConnectImportPlan(newReadings, conflictingReadings)
    }

    /** Applies a plan from [planHealthConnectImport]: [HealthConnectImportPlan.newReadings] are
     * always written, and [HealthConnectImportPlan.conflictingReadings] only if the user chose to
     * overwrite what they'd already logged rather than keep it. Returns how many days were
     * actually written, for a one-line confirmation in Settings. Writing straight to [persistLog]
     * rather than [saveLog] skips mirroring the value right back to Health Connect, which is
     * where it came from in the first place. */
    suspend fun applyHealthConnectImport(plan: HealthConnectImportPlan, overwriteConflicts: Boolean): Int {
        val toApply = if (overwriteConflicts) plan.newReadings + plan.conflictingReadings else plan.newReadings
        for ((date, weightKg) in toApply) {
            val existing = getLogForDate(date)
            persistLog(date, weightKg, existing?.calories, existing?.intakeLevel, existing?.wasExactEntry ?: false)
        }
        return toApply.size
    }

    /** Wipes the profile and every logged day - a deliberate full reset, not to be confused with
     * deleting a single day's record. */
    suspend fun eraseAllData() {
        profileDao.deleteAll()
        logDao.deleteAll()
        WidgetUpdater.refresh(appContext)
    }
}

private fun ProfileEntity.toDomain(): UserProfile = UserProfile(
    weightUnit = WeightUnit.valueOf(weightUnit),
    startWeightKg = startWeightKg,
    goalWeightKg = goalWeightKg,
    pace = pace?.let { runCatching { PaceOption.valueOf(it) }.getOrNull() },
    sex = Sex.valueOf(sex),
    heightCm = heightCm,
    age = age,
    activityLevel = ActivityLevel.valueOf(activityLevel),
    bodyFatPercent = bodyFatPercent,
)

private fun UserProfile.toEntity(): ProfileEntity = ProfileEntity(
    weightUnit = weightUnit.name,
    startWeightKg = startWeightKg,
    goalWeightKg = goalWeightKg,
    pace = pace?.name,
    sex = sex.name,
    heightCm = heightCm,
    age = age,
    activityLevel = activityLevel.name,
    bodyFatPercent = bodyFatPercent,
)

private fun DailyLogEntity.toDomain(): DailyLog = DailyLog(
    date = dateEpochDay.toLocalDate(),
    weightKg = weightKg,
    calories = calories,
    intakeLevel = intakeLevel?.let { runCatching { IntakeLevel.valueOf(it) }.getOrNull() },
    wasExactEntry = wasExactEntry,
)
