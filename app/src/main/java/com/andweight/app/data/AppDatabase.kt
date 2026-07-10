package com.andweight.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, DailyLogEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun dailyLogDao(): DailyLogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Drops the profile table's now-unused start date column and replaces its stored
         * kg/week rate with a qualitative pace tier - approximated as MODERATE since the exact
         * tier originally chosen isn't recoverable from the rate alone, but re-picking it in
         * Settings is one tap. Deliberately a real migration rather than a destructive fallback:
         * that would also wipe daily_log, and someone's actual logged weight/calorie history
         * matters far more than getting their pace tier guess exactly right.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE profile_new (
                        id INTEGER NOT NULL PRIMARY KEY,
                        weightUnit TEXT NOT NULL,
                        startWeightKg REAL NOT NULL,
                        goalWeightKg REAL NOT NULL,
                        pace TEXT,
                        sex TEXT NOT NULL,
                        heightCm REAL,
                        age INTEGER,
                        activityLevel TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO profile_new (id, weightUnit, startWeightKg, goalWeightKg, pace, sex, heightCm, age, activityLevel)
                    SELECT id, weightUnit, startWeightKg, goalWeightKg,
                        CASE WHEN goalRateKgPerWeek > 0 THEN 'MODERATE' ELSE NULL END,
                        sex, heightCm, age, activityLevel
                    FROM profile
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE profile")
                db.execSQL("ALTER TABLE profile_new RENAME TO profile")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "andweight.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
        }
    }
}
