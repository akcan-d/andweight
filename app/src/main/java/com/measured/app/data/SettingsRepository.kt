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
import kotlinx.coroutines.flow.combine
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
        // Superseded by SEEN_MILESTONE_IDS/MILESTONE_ID_BASELINE_DONE below - titles for AMOUNT
        // milestones embed a unit-converted number, so tracking "seen" by title string meant a
        // kg/lb switch alone made already-unlocked milestones look brand new. Left unread rather
        // than migrated: switching to the id-keyed keys below naturally replays the existing
        // "first run silently adopts the current baseline" logic once, with no special-case code.
        val SEEN_MILESTONE_TITLES = stringSetPreferencesKey("seen_milestone_titles")
        val MILESTONE_BASELINE_DONE = booleanPreferencesKey("milestone_baseline_done")
        val SEEN_MILESTONE_IDS = stringSetPreferencesKey("seen_milestone_ids")
        val MILESTONE_ID_BASELINE_DONE = booleanPreferencesKey("milestone_id_baseline_done")
        val MEAL_TIMING_MODE = stringPreferencesKey("meal_timing_mode")
        val FIRST_LAUNCH_EPOCH_DAY = longPreferencesKey("first_launch_epoch_day")
        val LAUNCH_COUNT = intPreferencesKey("launch_count")
        val WIDGET_THEME_MODE = stringPreferencesKey("widget_theme_mode")
        val WIDGET_ACCENT_THEME = stringPreferencesKey("widget_accent_theme")
        val WIDGET_HIGH_CONTRAST = booleanPreferencesKey("widget_high_contrast")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val HEALTH_CONNECT_SYNC_ENABLED = booleanPreferencesKey("health_connect_sync_enabled")
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

    /** Material You "wallpaper colors" (Android 12+) instead of one of the app's own built-in
     * palettes. Kept as its own flag rather than a new [AccentTheme] value, since the app icon
     * and widget both need a fixed, pre-drawn palette to sync to (see [AppIconSwitcher]) and a
     * per-device wallpaper-derived scheme has no fixed palette to draw an icon for - the
     * underlying [accentThemeFlow] choice stays put as that fallback while this is on. */
    val useDynamicColorFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.USE_DYNAMIC_COLOR] ?: false }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    /** Whether the user has turned sync on from Settings - independent of whether Health
     * Connect's own permission grant is actually in place (that can be revoked from outside the
     * app at any time), so [com.measured.app.ui.settings.SettingsViewModel] always has something
     * to re-check against rather than inferring "on" purely from a momentary permission check. */
    val healthConnectSyncEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.HEALTH_CONNECT_SYNC_ENABLED] ?: false }

    suspend fun healthConnectSyncEnabledOnce(): Boolean = healthConnectSyncEnabledFlow.first()

    suspend fun setHealthConnectSyncEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HEALTH_CONNECT_SYNC_ENABLED] = enabled }
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

    /** Widgets keep their own theme choice, independent of the in-app one - picking a new color
     * theme (or light/dark) for the app itself shouldn't silently repaint whatever's sitting on
     * the home screen too. They only ever change together when "Sync widget theme" is tapped
     * (see [com.measured.app.ui.settings.SettingsViewModel.syncWidgetTheme]), which copies the
     * app's current theme into these three values. Same defaults as the app's own so a widget
     * placed before anyone's touched Settings still looks reasonable. */
    val widgetThemeModeFlow: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.WIDGET_THEME_MODE]?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val widgetAccentThemeFlow: Flow<AccentTheme> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.WIDGET_ACCENT_THEME]?.let { name -> runCatching { AccentTheme.valueOf(name) }.getOrNull() } ?: AccentTheme.SAGE
    }

    val widgetHighContrastFlow: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.WIDGET_HIGH_CONTRAST] ?: false }

    /** Copies the app's current theme settings into the widget-only ones in a single atomic
     * write, so a widget redraw started right after this never sees a half-updated mix of old
     * and new values. */
    suspend fun syncWidgetThemeToApp() {
        val mode = themeModeFlow.first()
        val accent = accentThemeFlow.first()
        val highContrast = highContrastFlow.first()
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.WIDGET_THEME_MODE] = mode.name
            prefs[Keys.WIDGET_ACCENT_THEME] = accent.name
            prefs[Keys.WIDGET_HIGH_CONTRAST] = highContrast
        }
    }

    /** Whether the widget theme already matches the app's current theme - so the "Sync widget
     * theme" button can hide itself once there's nothing left to sync, the same way "Sync app
     * icon" does. */
    suspend fun isWidgetThemeSyncedOnce(): Boolean = isWidgetThemeSyncedFlow.first()

    val isWidgetThemeSyncedFlow: Flow<Boolean> = combine(
        combine(widgetThemeModeFlow, widgetAccentThemeFlow, widgetHighContrastFlow, ::Triple),
        combine(themeModeFlow, accentThemeFlow, highContrastFlow, ::Triple),
    ) { widgetTheme, appTheme -> widgetTheme == appTheme }

    val mealTimingModeFlow: Flow<MealTimingMode> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.MEAL_TIMING_MODE]?.let { name -> runCatching { MealTimingMode.valueOf(name) }.getOrNull() }
            ?: MealTimingMode.SAME_DAY
    }

    suspend fun setMealTimingMode(mode: MealTimingMode) {
        context.settingsDataStore.edit { it[Keys.MEAL_TIMING_MODE] = mode.name }
    }

    suspend fun mealTimingModeOnce(): MealTimingMode = mealTimingModeFlow.first()

    suspend fun setHighContrast(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HIGH_CONTRAST] = enabled }
    }

    /** IDs ([com.measured.app.domain.Milestone.id]) of milestones already announced via the
     * in-app notification, so reaching one only ever gets celebrated once - mirrors
     * [seenTipTitlesFlow]'s pattern, but keyed by stable id rather than display title (a title
     * can change, e.g. an AMOUNT milestone's unit-converted number after a kg/lb switch, without
     * the underlying milestone being any less already-seen). */
    suspend fun seenMilestoneIdsOnce(): Set<String> =
        context.settingsDataStore.data.map { it[Keys.SEEN_MILESTONE_IDS] ?: emptySet() }.first()

    suspend fun markMilestonesSeen(ids: Set<String>) {
        context.settingsDataStore.edit { it[Keys.SEEN_MILESTONE_IDS] = (it[Keys.SEEN_MILESTONE_IDS] ?: emptySet()) + ids }
    }

    /** Whether whatever was already achieved has been recorded as a starting point - without this,
     * someone upgrading from a version before this feature existed would get every milestone
     * they'd already reached dumped on them at once as "new", instead of only being notified about
     * ones reached from here on. */
    suspend fun isMilestoneBaselineDoneOnce(): Boolean =
        context.settingsDataStore.data.map { it[Keys.MILESTONE_ID_BASELINE_DONE] ?: false }.first()

    suspend fun setMilestoneBaselineDone() {
        context.settingsDataStore.edit { it[Keys.MILESTONE_ID_BASELINE_DONE] = true }
    }

    /** When this install's app process first ever started, and how many times it's started since
     * - tracked independently of any logged history, so restoring a backup full of old weeks
     * (see [com.measured.app.domain.SupporterPrompt]) can't make this install look older or more
     * used than it actually is. Recorded once per process start in AndWeightApplication, not per
     * screen visit. */
    suspend fun recordAppLaunchOnce() {
        context.settingsDataStore.edit { prefs ->
            if (prefs[Keys.FIRST_LAUNCH_EPOCH_DAY] == null) {
                prefs[Keys.FIRST_LAUNCH_EPOCH_DAY] = LocalDate.now().toEpochDay()
            }
            prefs[Keys.LAUNCH_COUNT] = (prefs[Keys.LAUNCH_COUNT] ?: 0) + 1
        }
    }

    suspend fun firstLaunchDateOnce(): LocalDate? =
        context.settingsDataStore.data.map { it[Keys.FIRST_LAUNCH_EPOCH_DAY]?.let(LocalDate::ofEpochDay) }.first()

    suspend fun launchCountOnce(): Int =
        context.settingsDataStore.data.map { it[Keys.LAUNCH_COUNT] ?: 0 }.first()
}
