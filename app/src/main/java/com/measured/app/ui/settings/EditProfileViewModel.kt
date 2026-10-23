package com.measured.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.measured.app.data.AndWeightRepository
import com.measured.app.domain.ActivityLevel
import com.measured.app.domain.GoalDirection
import com.measured.app.domain.HeightConversion
import com.measured.app.domain.PaceOption
import com.measured.app.domain.Sex
import com.measured.app.domain.UserProfile
import com.measured.app.domain.WeightUnit
import kotlinx.coroutines.launch

// Mirrors OnboardingViewModel's ranges - see that file for the reasoning.
private const val MIN_WEIGHT_KG = 20.0
private const val MAX_WEIGHT_KG = 400.0
private const val MIN_HEIGHT_CM = 50.0
private const val MAX_HEIGHT_CM = 250.0
private const val MAX_AGE = 120

data class EditProfileUiState(
    /** False until the existing profile has loaded - the form has nothing sensible to show
     * before then, so the screen shows a loading state instead of a flash of empty fields. */
    val loaded: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val startWeightText: String = "",
    val goalWeightText: String = "",
    val selectedPace: PaceOption? = null,
    val sex: Sex = Sex.UNSPECIFIED,
    val heightCmText: String = "",
    val heightFeetText: String = "",
    val heightInchesText: String = "",
    val ageText: String = "",
    val activityLevel: ActivityLevel = ActivityLevel.LIGHT,
    val bodyFatPercentText: String = "",
    /** Only ever set for the one case worth interrupting typing for (an under-18 age) - everything
     * else here autosaves silently (see [EditProfileViewModel.autoSave]) rather than nagging about
     * an in-progress, not-yet-valid number the person hasn't finished typing. */
    val errorMessage: String? = null,
) {
    val heightCm: Double?
        get() = if (weightUnit.isMetric) {
            heightCmText.toDoubleOrNull()
        } else {
            val feet = heightFeetText.toDoubleOrNull() ?: 0.0
            val inches = heightInchesText.toDoubleOrNull() ?: 0.0
            if (heightFeetText.isBlank() && heightInchesText.isBlank()) null else HeightConversion.feetInchesToCm(feet, inches)
        }

    /** Null only when there's nothing to infer from yet. Mirrors
     * [com.measured.app.ui.onboarding.OnboardingUiState.inferredDirection]. */
    val inferredDirection: GoalDirection?
        get() {
            val start = startWeightText.toDoubleOrNull() ?: return null
            if (goalWeightText.isBlank()) return GoalDirection.MAINTAIN
            val goal = goalWeightText.toDoubleOrNull() ?: return null
            return when {
                goal < start - 0.01 -> GoalDirection.LOSE
                goal > start + 0.01 -> GoalDirection.GAIN
                else -> GoalDirection.MAINTAIN
            }
        }
}

class EditProfileViewModel(private val repository: AndWeightRepository) : ViewModel() {

    var uiState by mutableStateOf(EditProfileUiState())
        private set

    init {
        viewModelScope.launch {
            val profile = repository.getProfileOnce() ?: return@launch
            val unit = profile.weightUnit
            val heightCm = profile.heightCm
            val heightCmText: String
            val heightFeetText: String
            val heightInchesText: String
            if (heightCm == null) {
                heightCmText = ""
                heightFeetText = ""
                heightInchesText = ""
            } else if (unit.isMetric) {
                heightCmText = formatOneDecimal(heightCm)
                heightFeetText = ""
                heightInchesText = ""
            } else {
                val (feet, inches) = HeightConversion.cmToFeetAndInches(heightCm)
                heightCmText = ""
                heightFeetText = feet.toString()
                heightInchesText = formatInches(inches)
            }

            uiState = EditProfileUiState(
                loaded = true,
                weightUnit = unit,
                startWeightText = formatOneDecimal(unit.fromKg(profile.startWeightKg)),
                goalWeightText = formatOneDecimal(unit.fromKg(profile.goalWeightKg)),
                selectedPace = profile.pace,
                sex = profile.sex,
                heightCmText = heightCmText,
                heightFeetText = heightFeetText,
                heightInchesText = heightInchesText,
                ageText = profile.age?.toString() ?: "",
                activityLevel = profile.activityLevel,
                bodyFatPercentText = profile.bodyFatPercent?.toString() ?: "",
            )
        }
    }

    fun onWeightUnitChange(unit: WeightUnit) {
        val previousUnit = uiState.weightUnit
        if (unit == previousUnit) return
        val previousHeightCm = uiState.heightCm
        uiState = uiState.copy(
            weightUnit = unit,
            startWeightText = convertText(uiState.startWeightText, previousUnit, unit),
            goalWeightText = convertText(uiState.goalWeightText, previousUnit, unit),
        )
        uiState = if (previousHeightCm == null) {
            uiState
        } else if (unit.isMetric) {
            uiState.copy(heightCmText = formatOneDecimal(previousHeightCm), heightFeetText = "", heightInchesText = "")
        } else {
            val (feet, inches) = HeightConversion.cmToFeetAndInches(previousHeightCm)
            uiState.copy(heightCmText = "", heightFeetText = feet.toString(), heightInchesText = formatInches(inches))
        }
        autoSave()
    }

    private fun convertText(text: String, from: WeightUnit, to: WeightUnit): String {
        val value = text.toDoubleOrNull() ?: return text
        return formatOneDecimal(to.fromKg(from.toKg(value)))
    }

    fun onStartWeightChange(text: String) {
        uiState = uiState.copy(startWeightText = text)
        autoSave()
    }

    fun onGoalWeightChange(text: String) {
        uiState = uiState.copy(goalWeightText = text)
        autoSave()
    }

    fun onPaceSelected(option: PaceOption) {
        uiState = uiState.copy(selectedPace = option)
        autoSave()
    }

    fun onSexChange(sex: Sex) {
        uiState = uiState.copy(sex = sex)
        autoSave()
    }

    fun onHeightCmChange(text: String) {
        uiState = uiState.copy(heightCmText = text)
        autoSave()
    }

    fun onHeightFeetChange(text: String) {
        uiState = uiState.copy(heightFeetText = text)
        autoSave()
    }

    fun onHeightInchesChange(text: String) {
        uiState = uiState.copy(heightInchesText = text)
        autoSave()
    }

    fun onAgeChange(text: String) {
        uiState = uiState.copy(ageText = text)
        autoSave()
    }

    fun onActivityLevelChange(level: ActivityLevel) {
        uiState = uiState.copy(activityLevel = level)
        autoSave()
    }

    fun onBodyFatPercentChange(text: String) {
        uiState = uiState.copy(bodyFatPercentText = text)
        autoSave()
    }

    /** Builds a profile from the current form state, same validation [save] used to require
     * before a manual "Save changes" tap - the difference now is this runs after every field
     * change instead of waiting for one. Returns null (and persists nothing) for a field that's
     * simply mid-typing and not a valid number yet, rather than erroring on it - someone typing
     * "2" on the way to "20" hasn't done anything wrong, there's just nothing complete to save
     * yet. The one exception is an under-18 age, which gets a passive note on screen (see
     * [EditProfileScreen]) rather than blocking anything, since there's no longer a save action to
     * block. */
    private fun buildProfileIfValid(): UserProfile? {
        val unit = uiState.weightUnit
        val startWeight = uiState.startWeightText.toDoubleOrNull() ?: return null
        if (startWeight <= 0.0) return null
        val startWeightKg = unit.toKg(startWeight)
        if (startWeightKg < MIN_WEIGHT_KG || startWeightKg > MAX_WEIGHT_KG) return null

        val goalWeightKg: Double
        if (uiState.goalWeightText.isBlank()) {
            goalWeightKg = startWeightKg
        } else {
            val goalWeight = uiState.goalWeightText.toDoubleOrNull() ?: return null
            if (goalWeight <= 0.0) return null
            val candidateKg = unit.toKg(goalWeight)
            if (candidateKg < MIN_WEIGHT_KG || candidateKg > MAX_WEIGHT_KG) return null
            goalWeightKg = candidateKg
        }

        // Lose/maintain/gain is inferred from how the two weights compare, not asked separately
        // (see EditProfileUiState.inferredDirection, echoed on screen so a typo is still visible).
        val direction = when {
            goalWeightKg < startWeightKg - 0.01 -> GoalDirection.LOSE
            goalWeightKg > startWeightKg + 0.01 -> GoalDirection.GAIN
            else -> GoalDirection.MAINTAIN
        }

        val pace = uiState.selectedPace
        if (direction != GoalDirection.MAINTAIN && pace == null) return null

        val age = uiState.ageText.toIntOrNull()
        if (age != null && age > MAX_AGE) return null

        val heightCm = uiState.heightCm
        if (heightCm != null && (heightCm < MIN_HEIGHT_CM || heightCm > MAX_HEIGHT_CM)) return null

        val bodyFatText = uiState.bodyFatPercentText
        val bodyFatPercent = if (bodyFatText.isBlank()) null else bodyFatText.toDoubleOrNull()
        if (bodyFatText.isNotBlank() && (bodyFatPercent == null || bodyFatPercent < 3.0 || bodyFatPercent > 70.0)) return null

        return UserProfile(
            weightUnit = unit,
            startWeightKg = startWeightKg,
            goalWeightKg = goalWeightKg,
            pace = if (direction == GoalDirection.MAINTAIN) null else pace,
            sex = uiState.sex,
            heightCm = heightCm,
            age = age,
            activityLevel = uiState.activityLevel,
            bodyFatPercent = bodyFatPercent,
        )
    }

    private fun autoSave() {
        val age = uiState.ageText.toIntOrNull()
        uiState = uiState.copy(
            errorMessage = if (age != null && age < 18) {
                "This app isn't designed for people under 18 - growing bodies have different " +
                    "nutritional needs than what it accounts for. Please talk to a doctor before " +
                    "using a calorie-tracking app, or clear the age field to continue without it."
            } else {
                null
            },
        )

        val profile = buildProfileIfValid() ?: return
        if (profile.age != null && profile.age < 18) return

        viewModelScope.launch { repository.saveProfile(profile) }
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
