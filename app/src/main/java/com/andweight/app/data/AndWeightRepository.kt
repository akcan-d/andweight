package com.andweight.app.data

import android.content.Context
import com.andweight.app.domain.ActivityLevel
import com.andweight.app.domain.DailyLog
import com.andweight.app.domain.IntakeLevel
import com.andweight.app.domain.Sex
import com.andweight.app.domain.TdeeEngine
import com.andweight.app.domain.TdeeState
import com.andweight.app.domain.UserProfile
import com.andweight.app.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class AndWeightRepository(context: Context) {

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
    }

    suspend fun getProfileOnce(): UserProfile? = profileDao.getProfileOnce()?.toDomain()

    suspend fun getLogForDate(date: LocalDate): DailyLog? =
        logDao.getByDate(date.toEpochDayLong())?.toDomain()

    suspend fun deleteLog(date: LocalDate) {
        logDao.deleteByDate(date.toEpochDayLong())
    }

    suspend fun saveLog(date: LocalDate, weightKg: Double?, calories: Double?, intakeLevel: IntakeLevel?, wasExact: Boolean) {
        logDao.upsert(
            DailyLogEntity(
                dateEpochDay = date.toEpochDayLong(),
                weightKg = weightKg,
                calories = calories,
                intakeLevel = intakeLevel?.name,
                wasExactEntry = wasExact,
            ),
        )
    }
}

private fun ProfileEntity.toDomain(): UserProfile = UserProfile(
    startDate = startDateEpochDay.toLocalDate(),
    weightUnit = WeightUnit.valueOf(weightUnit),
    startWeightKg = startWeightKg,
    goalWeightKg = goalWeightKg,
    goalRateKgPerWeek = goalRateKgPerWeek,
    sex = Sex.valueOf(sex),
    heightCm = heightCm,
    age = age,
    activityLevel = ActivityLevel.valueOf(activityLevel),
)

private fun UserProfile.toEntity(): ProfileEntity = ProfileEntity(
    startDateEpochDay = startDate.toEpochDayLong(),
    weightUnit = weightUnit.name,
    startWeightKg = startWeightKg,
    goalWeightKg = goalWeightKg,
    goalRateKgPerWeek = goalRateKgPerWeek,
    sex = sex.name,
    heightCm = heightCm,
    age = age,
    activityLevel = activityLevel.name,
)

private fun DailyLogEntity.toDomain(): DailyLog = DailyLog(
    date = dateEpochDay.toLocalDate(),
    weightKg = weightKg,
    calories = calories,
    intakeLevel = intakeLevel?.let { runCatching { IntakeLevel.valueOf(it) }.getOrNull() },
    wasExactEntry = wasExactEntry,
)
