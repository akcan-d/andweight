package com.andweight.app.ui.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.domain.ConfidenceAssessment
import com.andweight.app.domain.ConfidenceEvaluator
import com.andweight.app.domain.DailyLog
import com.andweight.app.domain.IntakeLevel
import com.andweight.app.domain.IntakeResolver
import com.andweight.app.domain.TdeeEngine
import com.andweight.app.domain.TdeeState
import com.andweight.app.domain.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WeightEntryState(
    val text: String = "",
    val errorMessage: String? = null,
)

data class MealEntryState(
    val selectedIntake: IntakeLevel? = null,
    val useExact: Boolean = false,
    val exactCaloriesText: String = "",
    val errorMessage: String? = null,
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

    val confidence: StateFlow<ConfidenceAssessment?> = combine(tdeeState, logs) { state, logList ->
        state?.let { ConfidenceEvaluator.assess(it, logList, actualToday) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The day the check-in card is currently showing - may differ from [actualToday] once the
     * person has paged backwards to fill in a missed day. */
    var selectedDate by mutableStateOf(actualToday)
        private set

    var existingLog by mutableStateOf<DailyLog?>(null)
        private set

    // Weight and meal info are tracked - and edited - completely independently, so logging your
    // weight in the morning and adding what you ate that night are two separate, unblocked steps.
    var isEditingWeight by mutableStateOf(true)
        private set
    var weightState by mutableStateOf(WeightEntryState())
        private set

    var isEditingMeal by mutableStateOf(true)
        private set
    var mealState by mutableStateOf(MealEntryState())
        private set

    /** True right after a save whose resolved calories were clamped up to the safety floor -
     * shown once on the summary view, then cleared as soon as the day changes or is re-edited. */
    var lastSaveHitFloor by mutableStateOf(false)
        private set

    init {
        loadDay(selectedDate)
    }

    val isDayComplete: Boolean
        get() = !isEditingWeight && !isEditingMeal

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

    // --- Weight ---

    fun onStartEditingWeight() {
        isEditingWeight = true
    }

    fun onCancelEditingWeight() {
        val savedWeight = existingLog?.weightKg ?: return // nothing to go back to
        val unit = profile.value?.weightUnit ?: return
        isEditingWeight = false
        weightState = WeightEntryState(text = formatWeight(unit.fromKg(savedWeight)))
    }

    fun onWeightTextChange(text: String) {
        weightState = weightState.copy(text = text, errorMessage = null)
    }

    fun saveWeight() {
        val currentProfile = profile.value ?: return
        val dateBeingSaved = selectedDate
        val parsed = weightState.text.toDoubleOrNull()
        if (parsed == null || parsed <= 0.0) {
            weightState = weightState.copy(errorMessage = "Enter a valid weight.")
            return
        }
        val weightKg = currentProfile.weightUnit.toKg(parsed)
        val existing = existingLog

        viewModelScope.launch {
            repository.saveLog(
                date = dateBeingSaved,
                weightKg = weightKg,
                calories = existing?.calories,
                intakeLevel = existing?.intakeLevel,
                wasExact = existing?.wasExactEntry ?: false,
            )
            if (dateBeingSaved == selectedDate) loadDay(selectedDate)
        }
    }

    // --- Meal ---

    fun onStartEditingMeal() {
        lastSaveHitFloor = false
        isEditingMeal = true
    }

    fun onCancelEditingMeal() {
        val existing = existingLog
        if (existing?.calories == null) return // nothing to go back to
        isEditingMeal = false
        mealState = MealEntryState(
            selectedIntake = existing.intakeLevel,
            useExact = existing.wasExactEntry,
            exactCaloriesText = if (existing.wasExactEntry) existing.calories.toInt().toString() else "",
        )
    }

    fun onIntakeSelected(level: IntakeLevel) {
        mealState = mealState.copy(selectedIntake = level, useExact = false, errorMessage = null)
    }

    fun onToggleExact() {
        mealState = mealState.copy(useExact = !mealState.useExact, errorMessage = null)
    }

    fun onExactCaloriesChange(text: String) {
        mealState = mealState.copy(exactCaloriesText = text, errorMessage = null)
    }

    fun saveMeal() {
        val currentProfile = profile.value ?: return
        val dateBeingSaved = selectedDate

        val hasCalorieAnswer = if (mealState.useExact) {
            mealState.exactCaloriesText.isNotBlank()
        } else {
            mealState.selectedIntake != null
        }
        if (!hasCalorieAnswer) {
            mealState = mealState.copy(errorMessage = "Choose how much you ate, or enter an exact number.")
            return
        }

        // Resolve against the TDEE estimate excluding this day's own (about to be replaced) entry,
        // so re-editing the same day repeatedly is idempotent instead of drifting - the previous
        // save must not feed into the basis used to compute the new one.
        val baselineTdee = TdeeEngine.compute(
            currentProfile,
            logs.value.filterNot { it.date == dateBeingSaved },
            actualToday,
        ).currentTdee

        val resolution = if (mealState.useExact) {
            val exact = mealState.exactCaloriesText.toDoubleOrNull()
            if (exact == null) {
                mealState = mealState.copy(errorMessage = "That doesn't look like a number.")
                return
            }
            IntakeResolver.resolveExact(exact, currentProfile.sex)
        } else {
            IntakeResolver.resolveQuickPick(mealState.selectedIntake!!, baselineTdee, currentProfile.sex)
        }

        val existing = existingLog

        viewModelScope.launch {
            repository.saveLog(
                date = dateBeingSaved,
                weightKg = existing?.weightKg,
                calories = resolution.calories,
                intakeLevel = if (mealState.useExact) null else mealState.selectedIntake,
                wasExact = mealState.useExact,
            )
            if (dateBeingSaved == selectedDate) {
                lastSaveHitFloor = resolution.floorApplied
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

            isEditingWeight = saved?.weightKg == null
            weightState = if (saved?.weightKg != null && unit != null) {
                WeightEntryState(text = formatWeight(unit.fromKg(saved.weightKg)))
            } else {
                WeightEntryState()
            }

            isEditingMeal = saved?.calories == null
            mealState = if (saved?.calories != null) {
                MealEntryState(
                    selectedIntake = saved.intakeLevel,
                    useExact = saved.wasExactEntry,
                    exactCaloriesText = if (saved.wasExactEntry) saved.calories.toInt().toString() else "",
                )
            } else {
                MealEntryState()
            }
        }
    }

    private fun formatWeight(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
