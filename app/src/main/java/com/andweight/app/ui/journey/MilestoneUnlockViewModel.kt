package com.andweight.app.ui.journey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.data.SettingsRepository
import com.andweight.app.domain.JourneyMilestones
import com.andweight.app.domain.TdeeEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Checks once, at launch, for any milestone that became achieved since the last time this ran -
 * surfaced as a brief in-app notification (see MainScreen's SnackbarHost) rather than a dialog,
 * since this is good news to glance at, not something that needs an explicit dismissal. A title is
 * only ever announced once, tracked the same way as seen tips.
 */
class MilestoneUnlockViewModel(
    private val repository: AndWeightRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    var newMilestoneMessage by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val profile = repository.getProfileOnce() ?: return@launch
            val today = LocalDate.now()
            val logs = repository.logsFlow.first()
            val state = TdeeEngine.compute(profile, logs, today)
            val achievedTitles = JourneyMilestones.compute(profile, state, logs, today)
                .filter { it.achieved }
                .map { it.title }
                .toSet()

            if (!settingsRepository.isMilestoneBaselineDoneOnce()) {
                // First time this check has ever run - whatever's already achieved becomes the
                // starting point silently, rather than announcing it all as if it just happened.
                settingsRepository.markMilestonesSeen(achievedTitles)
                settingsRepository.setMilestoneBaselineDone()
                return@launch
            }

            val seenTitles = settingsRepository.seenMilestoneTitlesOnce()
            val newlyAchieved = achievedTitles - seenTitles
            if (newlyAchieved.isNotEmpty()) {
                newMilestoneMessage = if (newlyAchieved.size == 1) {
                    "Milestone unlocked: ${newlyAchieved.first()}"
                } else {
                    "${newlyAchieved.size} new milestones unlocked"
                }
                settingsRepository.markMilestonesSeen(newlyAchieved)
            }
        }
    }

    fun onMessageShown() {
        newMilestoneMessage = null
    }
}
