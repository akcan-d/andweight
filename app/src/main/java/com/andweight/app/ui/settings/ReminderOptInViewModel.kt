package com.andweight.app.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.data.SettingsRepository
import com.andweight.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Drives the one-time "want a daily reminder?" prompt. Reminders default to *off* (see
 * [SettingsRepository.reminderEnabledFlow]) specifically so this prompt - not a silent default -
 * is what actually turns them on, since that's the only path that also asks for the notification
 * permission. Shown once, right after the first day has something logged (matching the moment
 * someone has just finished onboarding), rather than on the very first cold launch, which tends to
 * just get reflexively denied.
 */
class ReminderOptInViewModel(
    private val settingsRepository: SettingsRepository,
    private val repository: AndWeightRepository,
    private val application: Application,
) : ViewModel() {

    var showPrompt by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val alreadyPrompted = settingsRepository.hasShownReminderPromptOnce()
            val hasLoggedAnything = repository.logsFlow.first().isNotEmpty()
            if (!alreadyPrompted && hasLoggedAnything) {
                showPrompt = true
            }
        }
    }

    fun enableReminder(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.setReminderEnabled(true)
            settingsRepository.setReminderTime(hour, minute)
            settingsRepository.setReminderPromptShown(true)
            ReminderScheduler.schedule(application, hour, minute)
            showPrompt = false
        }
    }

    fun declineReminder() {
        viewModelScope.launch {
            settingsRepository.setReminderPromptShown(true)
            showPrompt = false
        }
    }
}
