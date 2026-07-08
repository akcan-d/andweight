package com.andweight.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.domain.TdeeState
import com.andweight.app.domain.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class HistoryViewModel(repository: AndWeightRepository) : ViewModel() {

    val profile: StateFlow<UserProfile?> = repository.profileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tdeeState: StateFlow<TdeeState?> = repository.tdeeStateFlow(LocalDate.now())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
