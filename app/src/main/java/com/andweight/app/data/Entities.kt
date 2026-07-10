package com.andweight.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Single-row table: this app tracks one person, so id is always fixed at 0. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    val weightUnit: String,
    val startWeightKg: Double,
    val goalWeightKg: Double,
    /** Null when maintaining - see [com.andweight.app.domain.UserProfile.pace]. */
    val pace: String?,
    val sex: String,
    val heightCm: Double?,
    val age: Int?,
    val activityLevel: String,
)

@Entity(tableName = "daily_log")
data class DailyLogEntity(
    @PrimaryKey val dateEpochDay: Long,
    val weightKg: Double?,
    val calories: Double?,
    /** Which quick-pick answer produced [calories], or null if this was an exact/manual entry. */
    val intakeLevel: String?,
    val wasExactEntry: Boolean,
)

fun LocalDate.toEpochDayLong(): Long = this.toEpochDay()
fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)
