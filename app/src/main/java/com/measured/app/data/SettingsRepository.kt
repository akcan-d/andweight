package com.measured.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.measured.app.domain.AccentTheme
import com.measured.app.domain.MealTimingMode
import com.measured.app.domain.ThemeMode
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
        val REMINDER_PROMPT_SHOWN = booleanPreferencesKey("reminder_prompt_shown")
        val SEEN_TIP_TITLES = stringSetPreferencesKey("seen_tip_titles")
        val IS_SUPPORTER = booleanPreferencesKey("is_supporter")
        val ACCENT_THEME = stringPreferencesKey("accent_theme")
        val SUPPORTER_PROMPT_SHOWN_COUNT = intPreferencesKey("supporter_prompt_shown_count")
        val LAST_SUPPORTER_PROMPT_EPOCH_DAY = longPreferencesKey("last_supporter_prompt_epoch_day")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val SEEN_MILESTONE_TITLES = stringSetPreferencesKey("seen_milestone_titles")
        val MILESTONE_BASELINE_DONE = booleanPreferencesKey("milestone_baseline_done")
        val MEAL_TIMING_MODE = stringPreferencesKey("meal_timing_mode")
    }

    val tipsEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.TIPS_ENABLED] ?: true }

    val themeModeFlow: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    // Defaults to off rather than on - it can only be meaningfully turned on through a path that
    // actually requests the notification permission (the one-time opt-in prompt, or this toggle),
    // never silently on a first launch nobody was asked about.
    val reminderEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.REMINDER_ENABLED] ?: false }

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

    suspend fun hasShownReminderPromptOnce(): Boolean =
        context.settingsDataStore.data.map { it[Keys.REMINDER_PROMPT_SHOWN] ?: false }.first()

    suspend fun setReminderPromptShown(shown: Boolean) {
        context.settingsDataStore.edit { it[Keys.REMINDER_PROMPT_SHOWN] = shown }
    }

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

    /** Titles of tips that have actually been shown as the tip-of-the-day pop-up - not just present
     * in the full list, so browsing the list doesn't itself mark everything as "seen". */
    val seenTipTitlesFlow: Flow<Set<String>> = context.settingsDataStore.data.map { it[Keys.SEEN_TIP_TITLES] ?: emptySet() }

    suspend fun markTipSeen(title: String) {
        context.settingsDataStore.edit { it[Keys.SEEN_TIP_TITLES] = (it[Keys.SEEN_TIP_TITLES] ?: emptySet()) + title }
    }

    /** Clears seen/shown tip history so the tip-of-the-day rotation and the checkmarks in the full
     * tips list both start over from scratch. */
    suspend fun resetTipHistory() {
        context.settingsDataStore.edit {
            it.remove(Keys.SEEN_TIP_TITLES)
            it.remove(Keys.TIP_SHOWN_COUNT)
            it.remove(Keys.LAST_TIP_SHOWN_EPOCH_DAY)
        }
    }

    // There's no real billing wired up yet (see SettingsScreen) - this flag is what will
    // eventually be set from a verified purchase, but for now it's just a persisted switch.
    val isSupporterFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.IS_SUPPORTER] ?: false }

    suspend fun isSupporterOnce(): Boolean = isSupporterFlow.first()

    suspend fun setSupporter(isSupporter: Boolean) {
        context.settingsDataStore.edit { it[Keys.IS_SUPPORTER] = isSupporter }
    }

    val accentThemeFlow: Flow<AccentTheme> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.ACCENT_THEME]?.let { name -> runCatching { AccentTheme.valueOf(name) }.getOrNull() } ?: AccentTheme.SAGE
    }

    suspend fun accentThemeOnce(): AccentTheme = accentThemeFlow.first()

    suspend fun setAccentTheme(theme: AccentTheme) {
        context.settingsDataStore.edit { it[Keys.ACCENT_THEME] = theme.name }
    }

    suspend fun getSupporterPromptShownCount(): Int =
        context.settingsDataStore.data.map { it[Keys.SUPPORTER_PROMPT_SHOWN_COUNT] ?: 0 }.first()

    suspend fun getLastSupporterPromptDate(): LocalDate? =
        context.settingsDataStore.data.map { it[Keys.LAST_SUPPORTER_PROMPT_EPOCH_DAY] }.first()?.let { LocalDate.ofEpochDay(it) }

    suspend fun recordSupporterPromptShown(date: LocalDate) {
        context.settingsDataStore.edit {
            it[Keys.SUPPORTER_PROMPT_SHOWN_COUNT] = (it[Keys.SUPPORTER_PROMPT_SHOWN_COUNT] ?: 0) + 1
            it[Keys.LAST_SUPPORTER_PROMPT_EPOCH_DAY] = date.toEpochDay()
        }
    }

    // A free accessibility option, not a supporter perk - swaps the warm, slightly muted neutrals
    // for a plain near-black/near-white pair with bolder outlines, for anyone who finds the
    // default palette too soft to read comfortably.
    val highContrastFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.HIGH_CONTRAST] ?: false }

    suspend fun highContrastOnce(): Boolean = highContrastFlow.first()

    val mealTimingModeFlow: Flow<MealTimingMode> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.MEAL_TIMING_MODE]?.let { name -> runCatching { MealTimingMode.valueOf(name) }.getOrNull() }
            ?: MealTimingMode.PREVIOUS_DAY
    }

    suspend fun setMealTimingMode(mode: MealTimingMode) {
        context.settingsDataStore.edit { it[Keys.MEAL_TIMING_MODE] = mode.name }
    }

    suspend fun mealTimingModeOnce(): MealTimingMode = mealTimingModeFlow.first()

    suspend fun setHighContrast(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HIGH_CONTRAST] = enabled }
    }

    /** Titles of milestones already announced via the in-app notification, so reaching one only
     * ever gets celebrated once - mirrors [seenTipTitlesFlow]'s pattern. */
    suspend fun seenMilestoneTitlesOnce(): Set<String> =
        context.settingsDataStore.data.map { it[Keys.SEEN_MILESTONE_TITLES] ?: emptySet() }.first()

    suspend fun markMilestonesSeen(titles: Set<String>) {
        context.settingsDataStore.edit { it[Keys.SEEN_MILESTONE_TITLES] = (it[Keys.SEEN_MILESTONE_TITLES] ?: emptySet()) + titles }
    }

    /** Whether whatever was already achieved has been recorded as a starting point - without this,
     * someone upgrading from a version before this feature existed would get every milestone
     * they'd already reached dumped on them at once as "new", instead of only being notified about
     * ones reached from here on. */
    suspend fun isMilestoneBaselineDoneOnce(): Boolean =
        context.settingsDataStore.data.map { it[Keys.MILESTONE_BASELINE_DONE] ?: false }.first()

    suspend fun setMilestoneBaselineDone() {
        context.settingsDataStore.edit { it[Keys.MILESTONE_BASELINE_DONE] = true }
    }
}
