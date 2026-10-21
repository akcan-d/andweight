package com.measured.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.measured.app.data.AndWeightRepository
import com.measured.app.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Whether to show a small "today still needs a check-in" badge on the Status tab - lets the app
 * remind someone without physically rearranging the Today screen's cards to do it (see
 * TodayScreen's fixed card order). Reacts live to new logs, same reasoning as
 * [com.measured.app.ui.journey.MilestoneUnlockViewModel].
 */
class CheckInReminderViewModel(
    repository: AndWeightRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val today = LocalDate.now()

    val showBadge: StateFlow<Boolean> = combine(repository.logsFlow, settingsRepository.checkInBadgeEnabledFlow) { logs, enabled ->
        if (!enabled) return@combine false
        val todayLog = logs.find { it.date == today }
        todayLog?.weightKg == null || todayLog.calories == null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
