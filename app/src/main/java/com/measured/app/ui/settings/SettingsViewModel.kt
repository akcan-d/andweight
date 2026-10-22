package com.measured.app.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.measured.app.data.AndWeightRepository
import com.measured.app.data.SettingsRepository
import com.measured.app.domain.AccentTheme
import com.measured.app.domain.BackupSerializer
import com.measured.app.domain.GoalDirection
import com.measured.app.domain.JourneyMilestones
import com.measured.app.domain.MealTimingMode
import com.measured.app.domain.ThemeMode
import com.measured.app.domain.UserProfile
import com.measured.app.domain.WeightUnit
import com.measured.app.notifications.ReminderScheduler
import com.measured.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val repository: AndWeightRepository,
    private val application: Application,
) : ViewModel() {

    val tipsEnabled: StateFlow<Boolean> = settingsRepository.tipsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val reminderEnabled: StateFlow<Boolean> = settingsRepository.reminderEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val reminderTime: StateFlow<Pair<Int, Int>> = settingsRepository.reminderTimeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 21 to 0)

    val profile: StateFlow<UserProfile?> = repository.profileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isSupporter: StateFlow<Boolean> = settingsRepository.isSupporterFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val accentTheme: StateFlow<AccentTheme> = settingsRepository.accentThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccentTheme.SAGE)

    val highContrast: StateFlow<Boolean> = settingsRepository.highContrastFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isWidgetThemeSynced: StateFlow<Boolean> = settingsRepository.isWidgetThemeSyncedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val mealTimingMode: StateFlow<MealTimingMode> = settingsRepository.mealTimingModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealTimingMode.PREVIOUS_DAY)

    /** Set once erasing everything finishes, so navigation back to onboarding can happen from its
     * own effect rather than directly off this coroutine. */
    var didEraseData by mutableStateOf(false)
        private set

    fun setTipsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTipsEnabled(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReminderEnabled(enabled)
            if (enabled) {
                val (hour, minute) = settingsRepository.getReminderTimeOnce()
                ReminderScheduler.schedule(application, hour, minute)
            } else {
                ReminderScheduler.cancel(application)
            }
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.setReminderTime(hour, minute)
            if (settingsRepository.isReminderEnabledOnce()) {
                ReminderScheduler.schedule(application, hour, minute)
            }
        }
    }

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch {
            val currentProfile = profile.value ?: return@launch
            repository.saveProfile(currentProfile.copy(weightUnit = unit))
        }
    }

    fun resetTipHistory() {
        viewModelScope.launch { settingsRepository.resetTipHistory() }
    }

    // There's no real billing wired up yet - this is a placeholder switch for testing the
    // supporter perks (new accent themes/icons, the extra Journey chart) until a real purchase
    // flow replaces it.
    fun setSupporter(isSupporter: Boolean) {
        viewModelScope.launch { settingsRepository.setSupporter(isSupporter) }
    }

    fun setAccentTheme(theme: AccentTheme) {
        // Just the in-app colors - changing themes never touches the launcher icon on its own
        // anymore. Anyone who wants the icon to match uses the explicit one-time sync button
        // instead (see syncIconToTheme), since restarting the app on every theme change (even
        // just to try out colors) was reported as disruptive.
        viewModelScope.launch { settingsRepository.setAccentTheme(theme) }
    }

    fun setHighContrast(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHighContrast(enabled) }
    }

    fun setMealTimingMode(mode: MealTimingMode) {
        viewModelScope.launch { settingsRepository.setMealTimingMode(mode) }
    }

    /** One-shot: makes the launcher icon match whatever theme is selected right now, then does
     * nothing further - it does not keep following future theme changes. Skips the restart if
     * the icon already happens to match, so tapping this when there's nothing to fix is a
     * harmless no-op instead of a pointless restart. */
    fun syncIconToTheme() {
        viewModelScope.launch {
            val currentTheme = settingsRepository.accentThemeOnce()
            if (!AppIconSwitcher.isAlreadyApplied(application, currentTheme)) {
                AppIconSwitcher.applyAndRestart(application, currentTheme)
            }
        }
    }

    /** Widgets keep their own theme, independent of the in-app one (see
     * [SettingsRepository.widgetThemeModeFlow]) - this is the only thing that ever copies the
     * app's current theme onto them, then forces an immediate redraw so the change doesn't sit
     * waiting for the widgets' next scheduled update. Skips the write+redraw entirely if they
     * already match, the same "nothing to fix" shortcut [syncIconToTheme] uses. */
    fun syncWidgetTheme() {
        viewModelScope.launch {
            if (settingsRepository.isWidgetThemeSyncedOnce()) return@launch
            settingsRepository.syncWidgetThemeToApp()
            WidgetUpdater.refresh(application)
        }
    }

    fun eraseAllData() {
        viewModelScope.launch {
            repository.eraseAllData()
            didEraseData = true
        }
    }

    /** Unlike a plain CSV, this also captures the profile and a few preferences, so restoring onto a
     * fresh install doesn't mean redoing onboarding. */
    suspend fun buildFullBackup(): String {
        val currentSettings = BackupSerializer.Settings(
            themeMode = settingsRepository.themeModeFlow.first(),
            tipsEnabled = settingsRepository.isTipsEnabledOnce(),
            reminderEnabled = settingsRepository.isReminderEnabledOnce(),
            reminderHour = settingsRepository.getReminderTimeOnce().first,
            reminderMinute = settingsRepository.getReminderTimeOnce().second,
        )
        return BackupSerializer.build(repository.getProfileOnce(), currentSettings, repository.logsFlow.first())
    }

    /** Null means the file wasn't recognizable as a Measured backup at all, so nothing was
     * touched - distinct from a recognizable file that's missing a section, which restores
     * whatever did parse (e.g. an older backup with no [settings] block still restores the
     * profile and logs). The profile and settings are replaced outright, but each day's log is
     * merged field-by-field via [AndWeightRepository.restoreLog] rather than overwritten
     * wholesale, so a field the backup doesn't carry for a given day can't erase one that only
     * exists on this device. */
    suspend fun restoreFullBackup(text: String): BackupSerializer.BackupData? {
        val result = BackupSerializer.parse(text) ?: return null

        result.profile?.let { repository.saveProfile(it) }
        result.logs.forEach { log -> repository.restoreLog(log) }
        result.settings?.let { settings ->
            settingsRepository.setThemeMode(settings.themeMode)
            settingsRepository.setTipsEnabled(settings.tipsEnabled)
            settingsRepository.setReminderEnabled(settings.reminderEnabled)
            settingsRepository.setReminderTime(settings.reminderHour, settings.reminderMinute)
            if (settings.reminderEnabled) {
                ReminderScheduler.schedule(application, settings.reminderHour, settings.reminderMinute)
            } else {
                ReminderScheduler.cancel(application)
            }
        }

        return result
    }

    data class ShareSummary(
        val daysActive: Int,
        val changeKg: Double,
        val direction: GoalDirection,
        val unit: WeightUnit,
        val milestoneCount: Int,
    )

    /** Null when there's no profile yet to share progress against. */
    suspend fun buildShareSummary(): ShareSummary? {
        val currentProfile = repository.getProfileOnce() ?: return null
        val state = repository.tdeeStateFlow().first() ?: return null
        val logs = repository.logsFlow.first()
        val firstLoggedDate = logs.minOfOrNull { it.date }
        val daysActive = firstLoggedDate?.let { ChronoUnit.DAYS.between(it, LocalDate.now()).toInt() + 1 } ?: 0
        val milestoneCount = JourneyMilestones.compute(currentProfile, state, logs, LocalDate.now()).count { it.achieved }

        return ShareSummary(
            daysActive = daysActive,
            changeKg = state.currentWeightKg - state.earliestKnownWeightKg,
            direction = currentProfile.goalDirection,
            unit = currentProfile.weightUnit,
            milestoneCount = milestoneCount,
        )
    }
}
