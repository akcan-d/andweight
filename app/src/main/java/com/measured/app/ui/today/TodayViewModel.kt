package com.measured.app.ui.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.measured.app.data.AndWeightRepository
import com.measured.app.domain.CalorieResolution
import com.measured.app.domain.ConfidenceAssessment
import com.measured.app.domain.ConfidenceEvaluator
import com.measured.app.domain.DailyLog
import com.measured.app.domain.IntakeLevel
import com.measured.app.domain.IntakeResolver
import com.measured.app.domain.SafetyLimits
import com.measured.app.domain.TdeeEngine
import com.measured.app.domain.TdeeState
import com.measured.app.domain.UserProfile
import com.measured.app.domain.WeightUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

    /** The meal section always asks about the day *before* [selectedDate], never [selectedDate]
     * itself - by the time you're checking in, yesterday's eating is finished and known for
     * certain, where today's isn't yet. This lets one check-in session (typically a morning
     * weigh-in) also finalize yesterday's intake, instead of needing a second visit later once
     * today is actually over. */
    val mealDate: LocalDate
        get() = selectedDate.minusDays(1)

    /** The row for [selectedDate] - holds this day's weight (and may also already hold this same
     * day's calories, filed the following day once that session happens). */
    var weightLog by mutableStateOf<DailyLog?>(null)
        private set

    /** The row for [mealDate] - holds the calories/intake level reported *about* that day, which
     * may be a different row than [weightLog] since it's keyed to the day before. */
    var mealLog by mutableStateOf<DailyLog?>(null)
        private set

    /** True once both halves of this check-in have something - [selectedDate]'s weight and
     * [mealDate]'s meal info - used by the UI to decide between the combined blank-day form and
     * the two separate per-field sections. */
    val isBlankDay: Boolean
        get() = weightLog?.weightKg == null && mealLog?.calories == null

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

    // Reacts to the saved log for whichever day is selected, rather than loading it once per
    // navigation action - so a day's entry disappearing or changing because of something done
    // elsewhere (e.g. deleting today's entry from the Journey tab) is picked up immediately here
    // too, instead of needing to navigate away and back to force a reload.
    init {
        viewModelScope.launch {
            combine(logs, profile, snapshotFlow { selectedDate }) { logList, userProfile, date ->
                SyncInputs(
                    weightLog = logList.firstOrNull { it.date == date },
                    mealLog = logList.firstOrNull { it.date == date.minusDays(1) },
                    unit = userProfile?.weightUnit,
                )
            }
                .distinctUntilChanged()
                .collect(::syncDraftState)
        }
    }

    private data class SyncInputs(val weightLog: DailyLog?, val mealLog: DailyLog?, val unit: WeightUnit?)

    /** Derived from the saved log itself rather than a transient post-save flag - an exact entry's
     * calories are kept exactly as typed, so whether it's below the safety floor can be (and
     * should be) shown every time the day is viewed, not just right after saving. Quick-pick
     * entries are silently clamped up to the floor instead (see [IntakeResolver.resolveQuickPick]),
     * so there's nothing to warn about for those. */
    val belowFloorWarning: Boolean
        get() {
            val log = mealLog ?: return false
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
    }

    fun goToNextDay() {
        if (!canGoToNextDay()) return
        selectedDate = selectedDate.plusDays(1)
    }

    fun jumpToToday() {
        if (!canJumpToToday()) return
        selectedDate = actualToday
    }

    /** Jumps straight to an arbitrary date - used when arriving from the Journey tab's "edit this
     * day" action, rather than stepping one day at a time via [goToPreviousDay]/[goToNextDay]. */
    fun jumpToDate(date: LocalDate) {
        selectedDate = date
    }

    /** Weight and meal info now live on two different rows ([selectedDate] and [mealDate]), so
     * clearing "this day" means clearing each one's own contribution - leaving the other field
     * untouched if that row also holds data from its other half of a check-in. A row left with
     * nothing in either field is deleted outright rather than kept around empty. */
    fun deleteDay() {
        viewModelScope.launch {
            weightLog?.let { row ->
                if (row.weightKg != null) {
                    if (row.calories != null) {
                        repository.saveLog(selectedDate, weightKg = null, calories = row.calories, intakeLevel = row.intakeLevel, wasExact = row.wasExactEntry)
                    } else {
                        repository.deleteLog(selectedDate)
                    }
                }
            }
            mealLog?.let { row ->
                if (row.calories != null) {
                    if (row.weightKg != null) {
                        repository.saveLog(mealDate, weightKg = row.weightKg, calories = null, intakeLevel = null, wasExact = false)
                    } else {
                        repository.deleteLog(mealDate)
                    }
                }
            }
        }
    }

    /** A quick way to change just the goal weight from the status card, instead of needing the
     * full Edit Profile screen for a one-number change. [UserProfile.goalDirection] is derived
     * from how this compares to the current weight, so lose/maintain/gain follows automatically -
     * nothing else needs updating here. */
    fun saveGoalWeight(newGoalWeightKg: Double) {
        viewModelScope.launch {
            val currentProfile = profile.value ?: return@launch
            repository.saveProfile(currentProfile.copy(goalWeightKg = newGoalWeightKg))
        }
    }

    // --- Weight ---

    fun onStartEditingWeight() {
        isEditingWeight = true
    }

    fun onCancelEditingWeight() {
        val savedWeight = weightLog?.weightKg ?: return // nothing to go back to
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
        val existing = weightLog

        viewModelScope.launch {
            repository.saveLog(
                date = dateBeingSaved,
                weightKg = weightKg,
                calories = existing?.calories,
                intakeLevel = existing?.intakeLevel,
                wasExact = existing?.wasExactEntry ?: false,
            )
        }
    }

    // --- Meal ---

    fun onStartEditingMeal() {
        isEditingMeal = true
    }

    fun onCancelEditingMeal() {
        val existing = mealLog
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
        val dateBeingSaved = mealDate

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

        val existing = mealLog

        viewModelScope.launch {
            repository.saveLog(
                date = dateBeingSaved,
                weightKg = existing?.weightKg,
                calories = resolution.calories,
                intakeLevel = if (mealState.useExact) null else mealState.selectedIntake,
                wasExact = mealState.useExact,
            )
        }
    }

    // --- Combined save, for a day where neither weight nor meal has been saved yet ---

    /** Used only while [isBlankDay] is true - a blank day shows a single combined form instead
     * of two separate per-field forms, so this saves whichever of weight/meal has valid input
     * (either, or both) in one write apiece, rather than delegating to [saveWeight]/[saveMeal]
     * independently, which could race against each other's read of [weightLog]/[mealLog]. Weight
     * and meal info land on two different dates ([selectedDate] and [mealDate]), so this writes
     * up to two separate rows rather than one. */
    fun saveDay() {
        val currentProfile = profile.value ?: return
        val weightDate = selectedDate
        val calorieDate = mealDate

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

        // Snapshotted before launching, not re-read from the live properties inside the coroutine
        // below - saving weight first (a suspending write) gives the reactive sync in init{} a
        // chance to run in between the two writes, and since this day's meal genuinely hasn't been
        // saved yet at that point, it would reset mealState back to blank right out from under
        // this function, losing the selection it was about to save.
        val mealSnapshot = mealState
        val existingMealWeightKg = mealLog?.weightKg

        viewModelScope.launch {
            if (hasWeightInput) {
                repository.saveLog(
                    date = weightDate,
                    weightKg = weightKg,
                    calories = weightLog?.calories,
                    intakeLevel = weightLog?.intakeLevel,
                    wasExact = weightLog?.wasExactEntry ?: false,
                )
            }

            if (hasMealInput) {
                val baselineTdee = TdeeEngine.compute(
                    currentProfile,
                    logs.value.filterNot { it.date == calorieDate },
                    actualToday,
                ).currentTdee

                val resolution: CalorieResolution = if (mealSnapshot.useExact) {
                    val exact = mealSnapshot.exactCaloriesText.toDoubleOrNull()
                    if (exact == null || !isPlausibleCalorieAmount(exact)) {
                        mealState = mealState.copy(errorMessage = "That doesn't look like a valid number of calories.")
                        return@launch
                    }
                    IntakeResolver.resolveExact(exact, currentProfile.sex)
                } else {
                    IntakeResolver.resolveQuickPick(mealSnapshot.selectedIntake!!, baselineTdee, currentProfile.sex)
                }

                repository.saveLog(
                    date = calorieDate,
                    weightKg = existingMealWeightKg,
                    calories = resolution.calories,
                    intakeLevel = if (mealSnapshot.useExact) null else mealSnapshot.selectedIntake,
                    wasExact = mealSnapshot.useExact,
                )
            }
        }
    }

    private fun syncDraftState(inputs: SyncInputs) {
        val (savedWeightLog, savedMealLog, unit) = inputs
        weightLog = savedWeightLog
        mealLog = savedMealLog

        isEditingWeight = savedWeightLog?.weightKg == null
        weightState = if (savedWeightLog?.weightKg != null && unit != null) {
            WeightEntryState(text = formatWeight(unit.fromKg(savedWeightLog.weightKg)))
        } else {
            WeightEntryState()
        }

        isEditingMeal = savedMealLog?.calories == null
        mealState = if (savedMealLog?.calories != null) {
            MealEntryState(
                selectedIntake = savedMealLog.intakeLevel,
                useExact = savedMealLog.wasExactEntry,
                exactCaloriesText = if (savedMealLog.wasExactEntry) savedMealLog.calories.toInt().toString() else "",
            )
        } else {
            MealEntryState()
        }
    }

    private fun formatWeight(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
