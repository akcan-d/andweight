package com.andweight.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.SettingsRepository
import com.andweight.app.domain.Tip
import com.andweight.app.domain.Tips
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Drives the once-a-day tip pop-up shown when the main screen first appears. */
class TipOfTheDayViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    var tipToShow by mutableStateOf<Tip?>(null)
        private set

    var dontShowAgain by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val enabled = settingsRepository.isTipsEnabledOnce()
            val lastShown = settingsRepository.getLastTipShownDate()
            if (enabled && lastShown != LocalDate.now()) {
                tipToShow = Tips.all.random()
            }
        }
    }

    fun onToggleDontShowAgain() {
        dontShowAgain = !dontShowAgain
    }

    fun onDismiss() {
        viewModelScope.launch {
            settingsRepository.setLastTipShownDate(LocalDate.now())
            if (dontShowAgain) {
                settingsRepository.setTipsEnabled(false)
            }
            tipToShow = null
        }
    }
}
