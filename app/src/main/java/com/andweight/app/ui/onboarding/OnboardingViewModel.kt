package com.andweight.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.domain.ActivityLevel
import com.andweight.app.domain.GoalDirection
import com.andweight.app.domain.HeightConversion
import com.andweight.app.domain.PaceOption
import com.andweight.app.domain.Sex
import com.andweight.app.domain.UserProfile
import com.andweight.app.domain.WeightUnit
import kotlinx.coroutines.launch
import java.time.LocalDate

data class OnboardingUiState(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val startWeightText: String = "",
    /** Explicitly chosen by the person, rather than inferred from the two weight fields - so the
     * stated intent ("I want to lose weight") is always the source of truth, and a goal weight
     * that doesn't match it is treated as a typo to correct rather than a silent reinterpretation. */
    val goalDirection: GoalDirection? = null,
    val goalWeightText: String = "",
    val selectedPace: PaceOption? = null,
    val sex: Sex = Sex.UNSPECIFIED,
    val heightCmText: String = "",
    val heightFeetText: String = "",
    val heightInchesText: String = "",
    val ageText: String = "",
    val activityLevel: ActivityLevel = ActivityLevel.LIGHT,
    val showAdvanced: Boolean = false,
    val errorMessage: String? = null,
    val didSave: Boolean = false,
) {
    /** Height in cm from whichever field(s) are active for the current [weightUnit] - metric
     * uses the single cm field, imperial combines feet + inches. */
    val heightCm: Double?
        get() = if (weightUnit.isMetric) {
            heightCmText.toDoubleOrNull()
        } else {
            val feet = heightFeetText.toDoubleOrNull() ?: 0.0
            val inches = heightInchesText.toDoubleOrNull() ?: 0.0
            if (heightFeetText.isBlank() && heightInchesText.isBlank()) null else HeightConversion.feetInchesToCm(feet, inches)
        }
}

class OnboardingViewModel(private val repository: AndWeightRepository) : ViewModel() {

    var uiState by mutableStateOf(OnboardingUiState())
        private set

    fun onWeightUnitChange(unit: WeightUnit) {
        val previousUnit = uiState.weightUnit
        if (unit == previousUnit) return
        val previousHeightCm = uiState.heightCm
        uiState = uiState.copy(
            weightUnit = unit,
            startWeightText = convertText(uiState.startWeightText, previousUnit, unit),
            goalWeightText = convertText(uiState.goalWeightText, previousUnit, unit),
        )
        // Re-derive the height fields for the newly selected unit, so switching Metric/Imperial
        // carries over whatever height was already entered instead of silently blanking it.
        uiState = if (previousHeightCm == null) {
            uiState
        } else if (unit.isMetric) {
            uiState.copy(heightCmText = formatOneDecimal(previousHeightCm), heightFeetText = "", heightInchesText = "")
        } else {
            val (feet, inches) = HeightConversion.cmToFeetAndInches(previousHeightCm)
            uiState.copy(heightCmText = "", heightFeetText = feet.toString(), heightInchesText = formatInches(inches))
        }
    }

    private fun formatOneDecimal(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    /** Inches need finer precision than the other one-decimal fields - at one decimal, a
     * cm -> ft/in -> cm round trip loses enough to visibly drift (186 cm became 185.9 cm). */
    private fun formatInches(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
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

    fun onGoalDirectionChange(direction: GoalDirection) {
        uiState = uiState.copy(goalDirection = direction, errorMessage = null)
    }

    fun onPaceSelected(option: PaceOption) {
        uiState = uiState.copy(selectedPace = option, errorMessage = null)
    }

    fun onSexChange(sex: Sex) {
        uiState = uiState.copy(sex = sex)
    }

    fun onHeightCmChange(text: String) {
        uiState = uiState.copy(heightCmText = text)
    }

    fun onHeightFeetChange(text: String) {
        uiState = uiState.copy(heightFeetText = text)
    }

    fun onHeightInchesChange(text: String) {
        uiState = uiState.copy(heightInchesText = text)
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

        if (startWeight == null || startWeight <= 0.0) {
            uiState = uiState.copy(errorMessage = "Enter your current weight.")
            return
        }
        val startWeightKg = unit.toKg(startWeight)

        val direction = uiState.goalDirection
        if (direction == null) {
            uiState = uiState.copy(errorMessage = "Choose whether you want to lose, maintain, or gain weight.")
            return
        }

        val goalWeightKg: Double
        if (direction == GoalDirection.MAINTAIN) {
            goalWeightKg = startWeightKg
        } else {
            val goalWeight = uiState.goalWeightText.toDoubleOrNull()
            if (goalWeight == null || goalWeight <= 0.0) {
                uiState = uiState.copy(errorMessage = "Enter your goal weight.")
                return
            }
            val candidateKg = unit.toKg(goalWeight)
            val matchesDirection = if (direction == GoalDirection.LOSE) {
                candidateKg < startWeightKg - 0.01
            } else {
                candidateKg > startWeightKg + 0.01
            }
            if (!matchesDirection) {
                val hint = if (direction == GoalDirection.LOSE) "lower" else "higher"
                uiState = uiState.copy(errorMessage = "Your goal weight should be $hint than your current weight to ${direction.name.lowercase()} weight.")
                return
            }
            goalWeightKg = candidateKg
        }

        val pace = uiState.selectedPace
        if (direction != GoalDirection.MAINTAIN && pace == null) {
            uiState = uiState.copy(errorMessage = "Choose a pace for reaching your goal.")
            return
        }

        val age = uiState.ageText.toIntOrNull()
        if (age != null && age < 18) {
            uiState = uiState.copy(
                errorMessage = "This app isn't designed for people under 18 - growing bodies have " +
                    "different nutritional needs than what it accounts for. Please talk to a doctor " +
                    "before using a calorie-tracking app, or clear the age field to continue without it.",
            )
            return
        }

        val profile = UserProfile(
            weightUnit = unit,
            startWeightKg = startWeightKg,
            goalWeightKg = goalWeightKg,
            pace = if (direction == GoalDirection.MAINTAIN) null else pace,
            sex = uiState.sex,
            heightCm = uiState.heightCm,
            age = age,
            activityLevel = uiState.activityLevel,
        )

        viewModelScope.launch {
            repository.saveProfile(profile)
            // Seed today's weight from what was just entered, so the person lands on a filled-in
            // summary rather than being asked to type the same number again immediately. No
            // calories yet - onboarding never asked "how much did you eat today", so that stays
            // open for them to fill in later the same way any other day's meal info is added.
            // Today's date also becomes the start date once logged - there's no separate stored
            // "start date" field, it's just whichever day has the oldest logged weight.
            repository.saveLog(
                date = LocalDate.now(),
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
