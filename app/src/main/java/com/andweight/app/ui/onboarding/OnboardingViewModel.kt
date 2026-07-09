package com.andweight.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.domain.ActivityLevel
import com.andweight.app.domain.GoalDirection
import com.andweight.app.domain.PaceGuidance
import com.andweight.app.domain.PaceOption
import com.andweight.app.domain.Sex
import com.andweight.app.domain.UserProfile
import com.andweight.app.domain.WeightUnit
import kotlinx.coroutines.launch
import java.time.LocalDate

data class OnboardingUiState(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val startWeightText: String = "",
    val goalWeightText: String = "",
    val selectedPace: PaceOption? = null,
    val sex: Sex = Sex.UNSPECIFIED,
    val heightCmText: String = "",
    val ageText: String = "",
    val activityLevel: ActivityLevel = ActivityLevel.LIGHT,
    val showAdvanced: Boolean = false,
    val errorMessage: String? = null,
    val didSave: Boolean = false,
) {
    /** Best-effort direction based on whatever's currently typed, used to decide whether to show
     * the pace picker at all and to preview each option's resulting rate. Null until both weights
     * are valid numbers. */
    val previewDirection: GoalDirection?
        get() {
            val start = startWeightText.toDoubleOrNull() ?: return null
            val goal = goalWeightText.toDoubleOrNull() ?: return null
            return when {
                goal > start + 0.01 -> GoalDirection.GAIN
                goal < start - 0.01 -> GoalDirection.LOSE
                else -> GoalDirection.MAINTAIN
            }
        }
}

class OnboardingViewModel(private val repository: AndWeightRepository) : ViewModel() {

    var uiState by mutableStateOf(OnboardingUiState())
        private set

    fun onWeightUnitChange(unit: WeightUnit) {
        val previousUnit = uiState.weightUnit
        if (unit == previousUnit) return
        uiState = uiState.copy(
            weightUnit = unit,
            startWeightText = convertText(uiState.startWeightText, previousUnit, unit),
            goalWeightText = convertText(uiState.goalWeightText, previousUnit, unit),
        )
    }

    /** Converts whatever number is already typed into a field to the newly selected unit, so
     * switching Kg/Lb doesn't silently leave stale digits behind in the wrong unit. */
    private fun convertText(text: String, from: WeightUnit, to: WeightUnit): String {
        val value = text.toDoubleOrNull() ?: return text
        val converted = to.fromKg(from.toKg(value))
        val rounded = Math.round(converted * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    fun onStartWeightChange(text: String) {
        uiState = uiState.copy(startWeightText = text, errorMessage = null)
    }

    fun onGoalWeightChange(text: String) {
        uiState = uiState.copy(goalWeightText = text, errorMessage = null)
    }

    fun onPaceSelected(option: PaceOption) {
        uiState = uiState.copy(selectedPace = option, errorMessage = null)
    }

    fun onSexChange(sex: Sex) {
        uiState = uiState.copy(sex = sex)
    }

    fun onHeightChange(text: String) {
        uiState = uiState.copy(heightCmText = text)
    }

    fun onAgeChange(text: String) {
        uiState = uiState.copy(ageText = text)
    }

    fun onActivityLevelChange(level: ActivityLevel) {
        uiState = uiState.copy(activityLevel = level)
    }

    fun onToggleAdvanced() {
        uiState = uiState.copy(showAdvanced = !uiState.showAdvanced)
    }

    fun save() {
        val unit = uiState.weightUnit
        val startWeight = uiState.startWeightText.toDoubleOrNull()
        val goalWeight = uiState.goalWeightText.toDoubleOrNull()

        if (startWeight == null || startWeight <= 0.0) {
            uiState = uiState.copy(errorMessage = "Enter your current weight.")
            return
        }
        if (goalWeight == null || goalWeight <= 0.0) {
            uiState = uiState.copy(errorMessage = "Enter your goal weight.")
            return
        }

        val startWeightKg = unit.toKg(startWeight)
        val goalWeightKg = unit.toKg(goalWeight)
        val direction = when {
            goalWeightKg > startWeightKg + 0.01 -> GoalDirection.GAIN
            goalWeightKg < startWeightKg - 0.01 -> GoalDirection.LOSE
            else -> GoalDirection.MAINTAIN
        }

        val pace = uiState.selectedPace
        if (direction != GoalDirection.MAINTAIN && pace == null) {
            uiState = uiState.copy(errorMessage = "Choose a pace for reaching your goal.")
            return
        }

        val goalRateKgPerWeek = if (direction == GoalDirection.MAINTAIN) {
            0.0
        } else {
            PaceGuidance.rateForOption(pace!!, startWeightKg, direction)
        }

        val height = uiState.heightCmText.toDoubleOrNull()
        val age = uiState.ageText.toIntOrNull()

        val profile = UserProfile(
            startDate = LocalDate.now(),
            weightUnit = unit,
            startWeightKg = startWeightKg,
            goalWeightKg = goalWeightKg,
            goalRateKgPerWeek = goalRateKgPerWeek,
            sex = uiState.sex,
            heightCm = height,
            age = age,
            activityLevel = uiState.activityLevel,
        )

        viewModelScope.launch {
            repository.saveProfile(profile)
            // Seed today's weight from what was just entered, so the person lands on a filled-in
            // summary rather than being asked to type the same number again immediately. No
            // calories yet - onboarding never asked "how much did you eat today", so that stays
            // open for them to fill in later the same way any other day's meal info is added.
            repository.saveLog(
                date = profile.startDate,
                weightKg = profile.startWeightKg,
                calories = null,
                intakeLevel = null,
                wasExact = false,
            )
            // Trigger navigation via a state change observed in its own effect (see OnboardingScreen),
            // rather than calling a navigation callback directly off this coroutine - navigating right
            // after a suspend database call resumes is not guaranteed to land back on the main thread.
            uiState = uiState.copy(didSave = true)
        }
    }
}
