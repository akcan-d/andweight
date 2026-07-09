package com.andweight.app.ui.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.domain.CalorieResolution
import com.andweight.app.domain.DailyLog
import com.andweight.app.domain.IntakeLevel
import com.andweight.app.domain.IntakeResolver
import com.andweight.app.domain.TdeeEngine
import com.andweight.app.domain.TdeeState
import com.andweight.app.domain.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CheckInFormState(
    val weightText: String = "",
    val selectedIntake: IntakeLevel? = null,
    val useExact: Boolean = false,
    val exactCaloriesText: String = "",
    val errorMessage: String? = null,
    val floorWasApplied: Boolean = false,
)

class TodayViewModel(private val repository: AndWeightRepository) : ViewModel() {

    /** Fixed for the lifetime of this screen - the actual current date, used for the stats card
     * and as the latest day you're allowed to browse to. */
    val actualToday: LocalDate = LocalDate.now()

    val profile: StateFlow<UserProfile?> = repository.profileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tdeeState: StateFlow<TdeeState?> = repository.tdeeStateFlow(actualToday)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val logs: StateFlow<List<DailyLog>> = repository.logsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The day the check-in card is currently showing - may differ from [actualToday] once the
     * person has paged backwards to fill in a missed day. */
    var selectedDate by mutableStateOf(actualToday)
        private set

    var existingLog by mutableStateOf<DailyLog?>(null)
        private set

    /** True while the input form is showing; false when displaying a saved day as a summary. */
    var isEditing by mutableStateOf(true)
        private set

    var formState by mutableStateOf(CheckInFormState())
        private set

    /** True right after a save whose resolved calories were clamped up to the safety floor -
     * shown once on the summary view, then cleared as soon as the day changes or is re-edited. */
    var lastSaveHitFloor by mutableStateOf(false)
        private set

    init {
        loadDay(selectedDate)
    }

    /** No lower bound - people should be able to back-fill history from before they started
     * using the app, e.g. weight they tracked elsewhere. */
    fun canGoToPreviousDay(): Boolean = true

    fun canGoToNextDay(): Boolean = selectedDate < actualToday

    fun canJumpToToday(): Boolean = selectedDate != actualToday

    fun goToPreviousDay() {
        selectedDate = selectedDate.minusDays(1)
        lastSaveHitFloor = false
        loadDay(selectedDate)
    }

    fun goToNextDay() {
        if (!canGoToNextDay()) return
        selectedDate = selectedDate.plusDays(1)
        lastSaveHitFloor = false
        loadDay(selectedDate)
    }

    fun jumpToToday() {
        if (!canJumpToToday()) return
        selectedDate = actualToday
        lastSaveHitFloor = false
        loadDay(selectedDate)
    }

    fun onStartEditing() {
        lastSaveHitFloor = false
        isEditing = true
    }

    fun deleteDay() {
        val dateBeingDeleted = selectedDate
        viewModelScope.launch {
            repository.deleteLog(dateBeingDeleted)
            if (dateBeingDeleted == selectedDate) {
                lastSaveHitFloor = false
                loadDay(selectedDate)
            }
        }
    }

    fun onCancelEditing() {
        if (existingLog == null) return // nothing to go back to
        isEditing = false
        formState = CheckInFormState()
    }

    fun onWeightChange(text: String) {
        formState = formState.copy(weightText = text, errorMessage = null)
    }

    fun onIntakeSelected(level: IntakeLevel) {
        formState = formState.copy(selectedIntake = level, useExact = false, errorMessage = null)
    }

    fun onToggleExact() {
        formState = formState.copy(useExact = !formState.useExact, errorMessage = null)
    }

    fun onExactCaloriesChange(text: String) {
        formState = formState.copy(exactCaloriesText = text, errorMessage = null)
    }

    /**
     * Weight and meal info are independent - you can save just one and add the other later.
     * This is what makes "weigh in the morning, log what you ate at night" work: the morning
     * save carries only a weight, and editing that same day at night adds the meal info without
     * touching (or requiring you to re-enter) the weight that's already there.
     */
    fun saveCheckIn() {
        val currentProfile = profile.value ?: return
        val dateBeingSaved = selectedDate

        val weightKg = formState.weightText.toDoubleOrNull()?.let { currentProfile.weightUnit.toKg(it) }
        val hasCalorieAnswer = if (formState.useExact) {
            formState.exactCaloriesText.isNotBlank()
        } else {
            formState.selectedIntake != null
        }

        if (weightKg == null && !hasCalorieAnswer) {
            formState = formState.copy(errorMessage = "Enter your weight, how much you ate, or both.")
            return
        }

        var resolution: CalorieResolution? = null
        if (hasCalorieAnswer) {
            // Resolve against the TDEE estimate excluding this day's own (about to be replaced)
            // entry, so re-editing the same day repeatedly is idempotent instead of drifting -
            // the previous save must not feed into the basis used to compute the new one.
            val baselineTdee = TdeeEngine.compute(
                currentProfile,
                logs.value.filterNot { it.date == dateBeingSaved },
                actualToday,
            ).currentTdee

            resolution = if (formState.useExact) {
                val exact = formState.exactCaloriesText.toDoubleOrNull()
                if (exact == null) {
                    formState = formState.copy(errorMessage = "That doesn't look like a number - enter that day's calories.")
                    return
                }
                IntakeResolver.resolveExact(exact, currentProfile.sex)
            } else {
                IntakeResolver.resolveQuickPick(formState.selectedIntake!!, baselineTdee, currentProfile.sex)
            }
        }

        viewModelScope.launch {
            repository.saveLog(
                date = dateBeingSaved,
                weightKg = weightKg,
                calories = resolution?.calories,
                intakeLevel = if (formState.useExact) null else formState.selectedIntake,
                wasExact = formState.useExact && hasCalorieAnswer,
            )
            if (dateBeingSaved == selectedDate) {
                lastSaveHitFloor = resolution?.floorApplied == true
                loadDay(selectedDate)
            }
        }
    }

    private fun loadDay(date: LocalDate) {
        viewModelScope.launch {
            val unit = profile.value?.weightUnit ?: repository.getProfileOnce()?.weightUnit
            val saved = repository.getLogForDate(date)
            if (selectedDate != date) return@launch // a later navigation superseded this load

            existingLog = saved
            isEditing = saved == null
            formState = if (saved == null || unit == null) {
                CheckInFormState()
            } else {
                CheckInFormState(
                    weightText = saved.weightKg?.let { kg -> formatWeight(unit.fromKg(kg)) } ?: "",
                    selectedIntake = saved.intakeLevel,
                    useExact = saved.wasExactEntry,
                    exactCaloriesText = if (saved.wasExactEntry) saved.calories?.toInt()?.toString() ?: "" else "",
                )
            }
        }
    }

    private fun formatWeight(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
