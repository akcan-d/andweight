package com.andweight.app.ui.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.domain.DailyLog
import com.andweight.app.domain.DataFillRate
import com.andweight.app.domain.DataFillRateResult
import com.andweight.app.domain.HealthBenefit
import com.andweight.app.domain.HealthBenefits
import com.andweight.app.domain.JourneyMilestones
import com.andweight.app.domain.Milestone
import com.andweight.app.domain.TdeeState
import com.andweight.app.domain.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class JourneyViewModel(private val repository: AndWeightRepository) : ViewModel() {

    /** Fixed for the lifetime of this screen - matches [com.andweight.app.ui.today.TodayViewModel.actualToday]. */
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

    fun deleteLog(date: LocalDate) {
        viewModelScope.launch { repository.deleteLog(date) }
    }
}
