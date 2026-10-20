package com.measured.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.measured.app.data.SettingsRepository
import com.measured.app.domain.Tip
import com.measured.app.domain.Tips
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Drives the once-a-day tip pop-up shown when the main screen first appears. The first several
 * times it fires, tips are shown in a fixed order (the most fundamental ones first, per
 * [Tips.PRIORITY_COUNT]) rather than randomly, so new users see the basics before anything else.
 */
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
                val shownCount = settingsRepository.getTipShownCount()
                val tip = if (shownCount < Tips.PRIORITY_COUNT) {
                    Tips.all[shownCount]
                } else {
                    Tips.all.random()
                }
                tipToShow = tip
                settingsRepository.incrementTipShownCount()
                settingsRepository.markTipSeen(tip.title)
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
