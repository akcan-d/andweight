package com.andweight.app.ui.tips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TipsViewModel(settingsRepository: SettingsRepository) : ViewModel() {

    val seenTipTitles: StateFlow<Set<String>> = settingsRepository.seenTipTitlesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
}
