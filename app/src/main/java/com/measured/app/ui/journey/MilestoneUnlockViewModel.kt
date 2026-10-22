package com.measured.app.ui.journey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.measured.app.data.AndWeightRepository
import com.measured.app.data.SettingsRepository
import com.measured.app.domain.JourneyMilestones
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Watches for any milestone that becomes achieved - surfaced as a brief in-app notification (see
 * MainScreen's SnackbarHost) rather than a dialog, since this is good news to glance at, not
 * something that needs an explicit dismissal. A title is only ever announced once, tracked the
 * same way as seen tips.
 *
 * Reacts live to [AndWeightRepository.logsFlow]/[AndWeightRepository.profileFlow] rather than
 * checking once at launch - an earlier version only checked in `init {}` against a one-shot
 * snapshot, so a milestone reached by a log saved *during* the current session never announced
 * itself until the app was restarted.
 */
class MilestoneUnlockViewModel(
    private val repository: AndWeightRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    var newMilestoneMessage by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val today = LocalDate.now()
            combine(repository.profileFlow, repository.logsFlow, repository.tdeeStateFlow(today)) { profile, logs, state ->
                Triple(profile, logs, state)
            }.collect { (profile, logs, state) ->
                if (profile == null || state == null) return@collect

                val achieved = JourneyMilestones.compute(profile, state, logs, today).filter { it.achieved }
                val achievedIds = achieved.map { it.id }.toSet()

                if (!settingsRepository.isMilestoneBaselineDoneOnce()) {
                    // First time this check has ever run - whatever's already achieved becomes
                    // the starting point silently, rather than announcing it all as if it just
                    // happened.
                    settingsRepository.markMilestonesSeen(achievedIds)
                    settingsRepository.setMilestoneBaselineDone()
                    return@collect
                }

                val seenIds = settingsRepository.seenMilestoneIdsOnce()
                val newlyAchieved = achieved.filter { it.id !in seenIds }
                if (newlyAchieved.isNotEmpty()) {
                    newMilestoneMessage = if (newlyAchieved.size == 1) {
                        "Milestone unlocked: ${newlyAchieved.first().title}"
                    } else {
                        "${newlyAchieved.size} new milestones unlocked"
                    }
                    settingsRepository.markMilestonesSeen(newlyAchieved.map { it.id }.toSet())
                }
            }
        }
    }

    fun onMessageShown() {
        newMilestoneMessage = null
    }
}
