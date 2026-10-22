package com.measured.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.measured.app.data.AndWeightRepository
import com.measured.app.data.SettingsRepository
import com.measured.app.domain.SupporterPrompt
import com.measured.app.domain.TdeeEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Drives the occasional "support the app?" nudge - see [SupporterPrompt] for the actual gating
 * (rare, tied to a real track record, capped at a handful of showings ever). The "shown" bookkeeping
 * is recorded the moment the prompt is decided on, not on dismiss, so an app kill mid-dialog can't
 * cause it to show again immediately.
 */
class SupporterPromptViewModel(
    private val repository: AndWeightRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    var showPrompt by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val today = LocalDate.now()
            val profile = repository.getProfileOnce()
            val logs = repository.logsFlow.first()
            val state = profile?.let { TdeeEngine.compute(it, logs, today) }
            val completedWeeks = state?.weeklyHistory?.count { it.hasWeightData && it.hasCalorieData } ?: 0

            val isSupporter = settingsRepository.isSupporterOnce()
            val shownCount = settingsRepository.getSupporterPromptShownCount()
            val lastShown = settingsRepository.getLastSupporterPromptDate()
            val daysSinceLastShown = lastShown?.let { ChronoUnit.DAYS.between(it, today) }
            val firstLaunchDate = settingsRepository.firstLaunchDateOnce()
            val daysSinceFirstLaunch = firstLaunchDate?.let { ChronoUnit.DAYS.between(it, today) } ?: 0L
            val launchCount = settingsRepository.launchCountOnce()

            val shouldShow = SupporterPrompt.shouldShow(
                completedWeeks,
                isSupporter,
                shownCount,
                daysSinceLastShown,
                daysSinceFirstLaunch,
                launchCount,
            )
            if (shouldShow) {
                showPrompt = true
                settingsRepository.recordSupporterPromptShown(today)
            }
        }
    }

    fun onDismiss() {
        showPrompt = false
    }

    fun onBecomeSupporter() {
        showPrompt = false
        viewModelScope.launch { settingsRepository.setSupporter(true) }
    }
}
