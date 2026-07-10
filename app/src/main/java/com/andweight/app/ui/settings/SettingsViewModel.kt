package com.andweight.app.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.data.SettingsRepository
import com.andweight.app.domain.CsvExport
import com.andweight.app.domain.ThemeMode
import com.andweight.app.domain.UserProfile
import com.andweight.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    fun eraseAllData() {
        viewModelScope.launch {
            repository.eraseAllData()
            didEraseData = true
        }
    }

    suspend fun buildCsvExport(): String =
        CsvExport.buildCsv(repository.getProfileOnce(), repository.logsFlow.first())
}
