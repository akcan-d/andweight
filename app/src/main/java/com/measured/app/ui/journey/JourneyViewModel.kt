package com.measured.app.ui.journey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.measured.app.data.AndWeightRepository
import com.measured.app.data.SettingsRepository
import com.measured.app.domain.DailyLog
import com.measured.app.domain.DataFillRate
import com.measured.app.domain.DataFillRateResult
import com.measured.app.domain.HealthBenefit
import com.measured.app.domain.HealthBenefits
import com.measured.app.domain.IntakeLevel
import com.measured.app.domain.IntakeResolver
import com.measured.app.domain.JourneyMilestones
import com.measured.app.domain.Milestone
import com.measured.app.domain.TdeeEngine
import com.measured.app.domain.TdeeState
import com.measured.app.domain.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

// Same generous sanity range as TodayViewModel's own exact-calorie check - this only exists to
// catch an obvious typo before it skews that day's row, not to second-guess a real entry.
private const val MIN_PLAUSIBLE_CALORIES = 0.0
private const val MAX_PLAUSIBLE_CALORIES = 15000.0
private fun isPlausibleCalorieAmount(value: Double) = value in MIN_PLAUSIBLE_CALORIES..MAX_PLAUSIBLE_CALORIES

data class HistoryWeightEditState(val text: String = "", val errorMessage: String? = null)

data class HistoryMealEditState(
    val selectedIntake: IntakeLevel? = null,
    val useExact: Boolean = false,
    val exactCaloriesText: String = "",
    val errorMessage: String? = null,
)

class JourneyViewModel(
    private val repository: AndWeightRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Fixed for the lifetime of this screen - matches [com.measured.app.ui.today.TodayViewModel.actualToday]. */
    val actualToday: LocalDate = LocalDate.now()

    val profile: StateFlow<UserProfile?> = repository.profileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tdeeState: StateFlow<TdeeState?> = repository.tdeeStateFlow(actualToday)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val logs: StateFlow<List<DailyLog>> = repository.logsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val milestones: StateFlow<List<Milestone>> = combine(profile, tdeeState, logs) { userProfile, state, logList ->
        if (userProfile == null || state == null) {
            emptyList()
        } else {
            JourneyMilestones.compute(userProfile, state, logList, actualToday)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val healthBenefits: StateFlow<List<HealthBenefit>> = combine(profile, tdeeState) { userProfile, state ->
        if (userProfile == null || state == null) {
            emptyList()
        } else {
            HealthBenefits.compute(userProfile, state)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dataFillRate: StateFlow<DataFillRateResult?> = logs
        .map { DataFillRate.compute(it, actualToday) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isSupporter: StateFlow<Boolean> = settingsRepository.isSupporterFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun deleteLog(date: LocalDate) {
        viewModelScope.launch { repository.deleteLog(date) }
    }

    /** Which day's inline edit form (if any) is currently open in [com.measured.app.ui.journey.JourneyHistoryScreen] -
     * editing happens right there in the calendar's detail panel rather than jumping to another
     * tab, so flicking through and fixing several days in a row doesn't mean leaving History each
     * time. */
    var editingDate by mutableStateOf<LocalDate?>(null)
        private set
    var weightEditState by mutableStateOf(HistoryWeightEditState())
        private set
    var mealEditState by mutableStateOf(HistoryMealEditState())
        private set

    fun startEditing(date: LocalDate) {
        val existing = logs.value.firstOrNull { it.date == date }
        val unit = profile.value?.weightUnit
        weightEditState = HistoryWeightEditState(
            text = existing?.weightKg?.let { kg -> unit?.let { formatWeight(it.fromKg(kg)) } ?: kg.toString() } ?: "",
        )
        mealEditState = HistoryMealEditState(
            selectedIntake = existing?.intakeLevel,
            useExact = existing?.wasExactEntry ?: false,
            exactCaloriesText = if (existing?.wasExactEntry == true) existing.calories?.toInt()?.toString() ?: "" else "",
        )
        editingDate = date
    }

    fun cancelEditing() {
        editingDate = null
    }

    fun onWeightEditTextChange(text: String) {
        weightEditState = weightEditState.copy(text = text, errorMessage = null)
    }

    fun onMealIntakeSelected(level: IntakeLevel) {
        mealEditState = mealEditState.copy(selectedIntake = level, useExact = false, errorMessage = null)
    }

    fun onMealToggleExact() {
        mealEditState = mealEditState.copy(useExact = !mealEditState.useExact, errorMessage = null)
    }

    fun onMealExactCaloriesChange(text: String) {
        mealEditState = mealEditState.copy(exactCaloriesText = text, errorMessage = null)
    }

    /** Both fields save together in one combined write, unlike Today's separate per-field forms -
     * a historical day being fixed up is usually a single correction pass, not two independent
     * edits in progress at once. Blank input for either field means "leave whatever's already
     * saved alone", not "clear it". */
    fun saveEditedDay() {
        val date = editingDate ?: return
        val currentProfile = profile.value ?: return
        val existing = logs.value.firstOrNull { it.date == date }

        val weightKg: Double? = if (weightEditState.text.isBlank()) {
            existing?.weightKg
        } else {
            val parsed = weightEditState.text.toDoubleOrNull()
            if (parsed == null || parsed <= 0.0) {
                weightEditState = weightEditState.copy(errorMessage = "Enter a valid weight.")
                return
            }
            currentProfile.weightUnit.toKg(parsed)
        }

        var calories = existing?.calories
        var intakeLevel = existing?.intakeLevel
        var wasExact = existing?.wasExactEntry ?: false

        val hasCalorieInput = if (mealEditState.useExact) {
            mealEditState.exactCaloriesText.isNotBlank()
        } else {
            mealEditState.selectedIntake != null
        }
        if (hasCalorieInput) {
            if (mealEditState.useExact) {
                val exact = mealEditState.exactCaloriesText.toDoubleOrNull()
                if (exact == null || !isPlausibleCalorieAmount(exact)) {
                    mealEditState = mealEditState.copy(errorMessage = "That doesn't look like a valid number of calories.")
                    return
                }
                val resolution = IntakeResolver.resolveExact(exact, currentProfile.sex)
                calories = resolution.calories
                intakeLevel = null
                wasExact = true
            } else {
                // Excludes this same day's own (about to be replaced) entry from the baseline, so
                // re-editing a day repeatedly is idempotent instead of drifting - matches
                // TodayViewModel.saveMeal's same reasoning.
                val baselineTdee = TdeeEngine.compute(currentProfile, logs.value.filterNot { it.date == date }, actualToday).currentTdee
                val resolution = IntakeResolver.resolveQuickPick(mealEditState.selectedIntake!!, baselineTdee, currentProfile.sex)
                calories = resolution.calories
                intakeLevel = mealEditState.selectedIntake
                wasExact = false
            }
        }

        viewModelScope.launch {
            repository.saveLog(date = date, weightKg = weightKg, calories = calories, intakeLevel = intakeLevel, wasExact = wasExact)
        }
        editingDate = null
    }

    private fun formatWeight(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    // There's no real billing wired up yet - same placeholder as SettingsViewModel.setSupporter,
    // used here so tapping "Become a supporter" from a locked-chart prompt doesn't need a trip
    // back to Settings first.
    fun setSupporter(isSupporter: Boolean) {
        viewModelScope.launch { settingsRepository.setSupporter(isSupporter) }
    }
}
