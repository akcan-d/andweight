package com.andweight.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.andweight.app.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private const val DEFAULT_REMINDER_HOUR = 21
private const val DEFAULT_REMINDER_MINUTE = 0

class SettingsRepository(private val context: Context) {

    private object Keys {
        val TIPS_ENABLED = booleanPreferencesKey("tips_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_TIP_SHOWN_EPOCH_DAY = longPreferencesKey("last_tip_shown_epoch_day")
        val TIP_SHOWN_COUNT = intPreferencesKey("tip_shown_count")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }

    val tipsEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.TIPS_ENABLED] ?: true }

    val themeModeFlow: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val reminderEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.REMINDER_ENABLED] ?: true }

    /** Hour (0-23) and minute of the daily reminder - defaults to 9 PM. */
    val reminderTimeFlow: Flow<Pair<Int, Int>> = context.settingsDataStore.data.map { prefs ->
        (prefs[Keys.REMINDER_HOUR] ?: DEFAULT_REMINDER_HOUR) to (prefs[Keys.REMINDER_MINUTE] ?: DEFAULT_REMINDER_MINUTE)
    }

    suspend fun setTipsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.TIPS_ENABLED] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        context.settingsDataStore.edit {
            it[Keys.REMINDER_HOUR] = hour
            it[Keys.REMINDER_MINUTE] = minute
        }
    }

    suspend fun isReminderEnabledOnce(): Boolean = reminderEnabledFlow.first()

    suspend fun getReminderTimeOnce(): Pair<Int, Int> = reminderTimeFlow.first()

    suspend fun isTipsEnabledOnce(): Boolean = tipsEnabledFlow.first()

    suspend fun getLastTipShownDate(): LocalDate? =
        context.settingsDataStore.data.map { it[Keys.LAST_TIP_SHOWN_EPOCH_DAY] }.first()?.let { LocalDate.ofEpochDay(it) }

    suspend fun setLastTipShownDate(date: LocalDate) {
        context.settingsDataStore.edit { it[Keys.LAST_TIP_SHOWN_EPOCH_DAY] = date.toEpochDay() }
    }

    /** How many times the tip pop-up has been shown - used to work through the priority tips in
     * order before switching to random. */
    suspend fun getTipShownCount(): Int =
        context.settingsDataStore.data.map { it[Keys.TIP_SHOWN_COUNT] ?: 0 }.first()

    suspend fun incrementTipShownCount() {
        context.settingsDataStore.edit { it[Keys.TIP_SHOWN_COUNT] = (it[Keys.TIP_SHOWN_COUNT] ?: 0) + 1 }
    }
}
