package com.andweight.app.ui.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.domain.CalorieResolution
import com.andweight.app.domain.ConfidenceAssessment
import com.andweight.app.domain.ConfidenceEvaluator
import com.andweight.app.domain.DailyLog
import com.andweight.app.domain.IntakeLevel
import com.andweight.app.domain.IntakeResolver
import com.andweight.app.domain.SafetyLimits
import com.andweight.app.domain.TdeeEngine
import com.andweight.app.domain.TdeeState
import com.andweight.app.domain.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

// A generous sanity range rather than a tight one - this only exists to catch an obvious typo
// (a stray minus sign, an extra digit) before it triggers the "below the safety minimum" warning
// or skews that week's TDEE average, not to second-guess a real, unusually large or small day.
private const val MIN_PLAUSIBLE_CALORIES = 0.0
private const val MAX_PLAUSIBLE_CALORIES = 15000.0

private fun isPlausibleCalorieAmount(value: Double): Boolean =
    value in MIN_PLAUSIBLE_CALORIES..MAX_PLAUSIBLE_CALORIES

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

    val confidence: StateFlow<ConfidenceAssessment?> = combine(profile, tdeeState, logs) { userProfile, state, logList ->
        if (userProfile == null || state == null) null else ConfidenceEvaluator.assess(userProfile, state, logList, actualToday)
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

    init {
        loadDay(selectedDate)
    }

    /** Derived from the saved log itself rather than a transient post-save flag - an exact entry's
     * calories are kept exactly as typed, so whether it's below the safety floor can be (and
     * should be) shown every time the day is viewed, not just right after saving. Quick-pick
     * entries are silently clamped up to the floor instead (see [IntakeResolver.resolveQuickPick]),
     * so there's nothing to warn about for those. */
    val belowFloorWarning: Boolean
        get() {
            val log = existingLog ?: return false
            val currentProfile = profile.value ?: return false
            if (!log.wasExactEntry || log.calories == null) return false
            return log.calories < SafetyLimits.floorFor(currentProfile.sex) - 0.01
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
        loadDay(selectedDate)
    }

    fun goToNextDay() {
        if (!canGoToNextDay()) return
        selectedDate = selectedDate.plusDays(1)
        loadDay(selectedDate)
    }

    fun jumpToToday() {
        if (!canJumpToToday()) return
        selectedDate = actualToday
        loadDay(selectedDate)
    }

    /** Jumps straight to an arbitrary date - used when arriving from the Journey tab's "edit this
     * day" action, rather than stepping one day at a time via [goToPreviousDay]/[goToNextDay]. */
    fun jumpToDate(date: LocalDate) {
        if (date == selectedDate) return
        selectedDate = date
        loadDay(selectedDate)
    }

    fun deleteDay() {
        val dateBeingDeleted = selectedDate
        viewModelScope.launch {
            repository.deleteLog(dateBeingDeleted)
            if (dateBeingDeleted == selectedDate) {
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
            if (exact == null || !isPlausibleCalorieAmount(exact)) {
                mealState = mealState.copy(errorMessage = "That doesn't look like a valid number of calories.")
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
                loadDay(selectedDate)
            }
        }
    }

    // --- Combined save, for a day where neither weight nor meal has been saved yet ---

    /** Used only while [existingLog] is null - a blank day shows a single combined form instead
     * of two separate per-field forms, so this saves whichever of weight/meal has valid input
     * (either, or both) in one write, rather than delegating to [saveWeight]/[saveMeal]
     * independently, which could race against each other's read of [existingLog]. */
    fun saveDay() {
        val currentProfile = profile.value ?: return
        val dateBeingSaved = selectedDate

        val hasWeightInput = weightState.text.isNotBlank()
        val parsedWeight = weightState.text.toDoubleOrNull()
        if (hasWeightInput && (parsedWeight == null || parsedWeight <= 0.0)) {
            weightState = weightState.copy(errorMessage = "Enter a valid weight.")
            return
        }

        val hasMealInput = if (mealState.useExact) {
            mealState.exactCaloriesText.isNotBlank()
        } else {
            mealState.selectedIntake != null
        }

        if (!hasWeightInput && !hasMealInput) {
            mealState = mealState.copy(errorMessage = "Enter your weight, log a meal, or both.")
            return
        }

        val weightKg = if (hasWeightInput) currentProfile.weightUnit.toKg(parsedWeight!!) else null

        viewModelScope.launch {
            var resolution: CalorieResolution? = null
            if (hasMealInput) {
                val baselineTdee = TdeeEngine.compute(
                    currentProfile,
                    logs.value.filterNot { it.date == dateBeingSaved },
                    actualToday,
                ).currentTdee

                resolution = if (mealState.useExact) {
                    val exact = mealState.exactCaloriesText.toDoubleOrNull()
                    if (exact == null || !isPlausibleCalorieAmount(exact)) {
                        mealState = mealState.copy(errorMessage = "That doesn't look like a valid number of calories.")
                        return@launch
                    }
                    IntakeResolver.resolveExact(exact, currentProfile.sex)
                } else {
                    IntakeResolver.resolveQuickPick(mealState.selectedIntake!!, baselineTdee, currentProfile.sex)
                }
            }

            repository.saveLog(
                date = dateBeingSaved,
                weightKg = weightKg,
                calories = resolution?.calories,
                intakeLevel = if (hasMealInput && !mealState.useExact) mealState.selectedIntake else null,
                wasExact = hasMealInput && mealState.useExact,
            )
            if (dateBeingSaved == selectedDate) {
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
