package com.measured.app.ui.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.measured.app.data.AndWeightRepository
import com.measured.app.data.SettingsRepository
import com.measured.app.domain.DailyLog
import com.measured.app.domain.DataFillRate
import com.measured.app.domain.DataFillRateResult
import com.measured.app.domain.HealthBenefit
import com.measured.app.domain.HealthBenefits
import com.measured.app.domain.JourneyMilestones
import com.measured.app.domain.Milestone
import com.measured.app.domain.TdeeState
import com.measured.app.domain.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class JourneyViewModel(
    private val repository: AndWeightRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Fixed for the lifetime of this screen - matches [com.measured.app.ui.today.TodayViewModel.actualToday]. */
    val actualToday: LocalDate = LocalDate.now()

    val profile: StateFlow<UserProfile?> = repository.profileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tdeeState: StateFlow<TdeeState?> = repository.tdeeStateFlow(actualToday)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val logs: StateFlow<List<DailyLog>> = repository.logsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val milestones: StateFlow<List<Milestone>> = combine(profile, tdeeState, logs) { userProfile, state, logList ->
        if (userProfile == null || state == null) {
            emptyList()
        } else {
            JourneyMilestones.compute(userProfile, state, logList, actualToday)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val healthBenefits: StateFlow<List<HealthBenefit>> = combine(profile, tdeeState) { userProfile, state ->
        if (userProfile == null || state == null) {
            emptyList()
        } else {
            HealthBenefits.compute(userProfile, state)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dataFillRate: StateFlow<DataFillRateResult?> = logs
        .map { DataFillRate.compute(it, actualToday) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isSupporter: StateFlow<Boolean> = settingsRepository.isSupporterFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun deleteLog(date: LocalDate) {
        viewModelScope.launch { repository.deleteLog(date) }
    }

    // There's no real billing wired up yet - same placeholder as SettingsViewModel.setSupporter,
    // used here so tapping "Become a supporter" from a locked-chart prompt doesn't need a trip
    // back to Settings first.
    fun setSupporter(isSupporter: Boolean) {
        viewModelScope.launch { settingsRepository.setSupporter(isSupporter) }
    }
}
