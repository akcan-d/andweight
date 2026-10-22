package com.measured.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.measured.app.data.AndWeightRepository
import com.measured.app.data.SettingsRepository
import com.measured.app.domain.CheckInStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Whether to show a small "today still needs a check-in" badge on the Status tab - lets the app
 * remind someone without physically rearranging the Today screen's cards to do it (see
 * TodayScreen's fixed card order). Reacts live to new logs and to the MealTimingMode setting,
 * same reasoning as [com.measured.app.ui.journey.MilestoneUnlockViewModel].
 */
class CheckInReminderViewModel(
    repository: AndWeightRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val today = LocalDate.now()

    // Eagerly, not WhileSubscribed - this needs to be correct the moment MainScreen reads it on
    // first composition, not lag behind until something else happens to be observing it.
    val showBadge: StateFlow<Boolean> = combine(repository.logsFlow, settingsRepository.mealTimingModeFlow) { logs, mode ->
        val mealDate = CheckInStatus.mealDateFor(today, mode)
        val todayLog = logs.firstOrNull { it.date == today }
        val mealLog = if (mealDate == today) todayLog else logs.firstOrNull { it.date == mealDate }
        !CheckInStatus.isComplete(todayLog, mealLog)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
