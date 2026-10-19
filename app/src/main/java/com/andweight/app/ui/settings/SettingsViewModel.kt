package com.andweight.app.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.data.SettingsRepository
import com.andweight.app.domain.AccentTheme
import com.andweight.app.domain.BackupSerializer
import com.andweight.app.domain.GoalDirection
import com.andweight.app.domain.ThemeMode
import com.andweight.app.domain.UserProfile
import com.andweight.app.domain.WeightUnit
import com.andweight.app.notifications.ReminderScheduler
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
        viewModelScope.launch {
            settingsRepository.setAccentTheme(theme)
            AppIconSwitcher.apply(application, theme)
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

    /** Null means the file wasn't recognizable as an AndWeight backup at all, so nothing was
     * touched - distinct from a recognizable file that's missing a section, which restores
     * whatever did parse (e.g. an older backup with no [settings] block still restores the
     * profile and logs). */
    suspend fun restoreFullBackup(text: String): BackupSerializer.BackupData? {
        val result = BackupSerializer.parse(text) ?: return null

        result.profile?.let { repository.saveProfile(it) }
        result.logs.forEach { log ->
            repository.saveLog(log.date, log.weightKg, log.calories, log.intakeLevel, log.wasExactEntry)
        }
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
    )

    /** Null when there's no profile yet to share progress against. */
    suspend fun buildShareSummary(): ShareSummary? {
        val currentProfile = repository.getProfileOnce() ?: return null
        val state = repository.tdeeStateFlow().first() ?: return null
        val logs = repository.logsFlow.first()
        val firstLoggedDate = logs.minOfOrNull { it.date }
        val daysActive = firstLoggedDate?.let { ChronoUnit.DAYS.between(it, LocalDate.now()).toInt() + 1 } ?: 0

        return ShareSummary(
            daysActive = daysActive,
            changeKg = state.currentWeightKg - state.earliestKnownWeightKg,
            direction = currentProfile.goalDirection,
            unit = currentProfile.weightUnit,
        )
    }
}
