package com.measured.app.ui.onboarding

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.measured.app.data.AndWeightRepository
import com.measured.app.data.SettingsRepository
import com.measured.app.domain.ActivityLevel
import com.measured.app.domain.BackupSerializer
import com.measured.app.domain.GoalDirection
import com.measured.app.domain.HeightConversion
import com.measured.app.domain.PaceOption
import com.measured.app.domain.Sex
import com.measured.app.domain.UserProfile
import com.measured.app.domain.WeightUnit
import com.measured.app.notifications.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate

// Generous sanity ranges, not strict ones - these only exist to catch an obvious typo (an extra
// digit, a missed decimal point) before it feeds a nonsensical number into the TDEE formula, not
// to second-guess a real, unusually small or large person.
private const val MIN_WEIGHT_KG = 20.0
private const val MAX_WEIGHT_KG = 400.0
private const val MIN_HEIGHT_CM = 50.0
private const val MAX_HEIGHT_CM = 250.0
private const val MAX_AGE = 120

data class OnboardingUiState(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val startWeightText: String = "",
    /** Left blank means "maintain this weight" - lose/maintain/gain is inferred from comparing
     * this to [startWeightText] rather than asked as its own separate question, with the
     * inferred direction echoed back on screen (see OnboardingScreen) so a typo'd goal weight is
     * still visible, just not a hard validation error. */
    val goalWeightText: String = "",
    val selectedPace: PaceOption? = null,
    val sex: Sex = Sex.UNSPECIFIED,
    val heightCmText: String = "",
    val heightFeetText: String = "",
    val heightInchesText: String = "",
    val ageText: String = "",
    val activityLevel: ActivityLevel = ActivityLevel.LIGHT,
    val bodyFatPercentText: String = "",
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

    /** Null only when there's nothing to infer from yet (current weight not entered). A blank
     * goal weight infers MAINTAIN - see [goalWeightText]. */
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

class OnboardingViewModel(
    private val repository: AndWeightRepository,
    private val settingsRepository: SettingsRepository,
    private val application: Application,
) : ViewModel() {

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

    fun onBodyFatPercentChange(text: String) {
        uiState = uiState.copy(bodyFatPercentText = text, errorMessage = null)
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
        if (startWeightKg < MIN_WEIGHT_KG || startWeightKg > MAX_WEIGHT_KG) {
            uiState = uiState.copy(errorMessage = "Enter a realistic current weight.")
            return
        }

        val goalWeightKg: Double
        if (uiState.goalWeightText.isBlank()) {
            goalWeightKg = startWeightKg
        } else {
            val goalWeight = uiState.goalWeightText.toDoubleOrNull()
            if (goalWeight == null || goalWeight <= 0.0) {
                uiState = uiState.copy(errorMessage = "Enter your goal weight, or leave it blank to maintain your current weight.")
                return
            }
            val candidateKg = unit.toKg(goalWeight)
            if (candidateKg < MIN_WEIGHT_KG || candidateKg > MAX_WEIGHT_KG) {
                uiState = uiState.copy(errorMessage = "Enter a realistic goal weight.")
                return
            }
            goalWeightKg = candidateKg
        }

        // Lose/maintain/gain is inferred from how the two weights compare, not asked separately
        // (see OnboardingUiState.inferredDirection, echoed on screen so a typo is still visible).
        val direction = when {
            goalWeightKg < startWeightKg - 0.01 -> GoalDirection.LOSE
            goalWeightKg > startWeightKg + 0.01 -> GoalDirection.GAIN
            else -> GoalDirection.MAINTAIN
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
        if (age != null && age > MAX_AGE) {
            uiState = uiState.copy(errorMessage = "Enter a realistic age, or clear the field to continue without it.")
            return
        }

        val heightCm = uiState.heightCm
        if (heightCm != null && (heightCm < MIN_HEIGHT_CM || heightCm > MAX_HEIGHT_CM)) {
            uiState = uiState.copy(errorMessage = "Enter a realistic height, or clear the field to continue without it.")
            return
        }

        val bodyFatText = uiState.bodyFatPercentText
        val bodyFatPercent = if (bodyFatText.isBlank()) null else bodyFatText.toDoubleOrNull()
        if (bodyFatText.isNotBlank() && (bodyFatPercent == null || bodyFatPercent < 3.0 || bodyFatPercent > 70.0)) {
            uiState = uiState.copy(errorMessage = "Enter a body fat percentage between 3 and 70, or clear the field.")
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
            bodyFatPercent = bodyFatPercent,
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

    /** An alternative to [save] for anyone who already has a Measured backup file - skips re-typing
     * everything the file already has. Mirrors [com.measured.app.ui.settings.SettingsViewModel]'s
     * restore, including the same field-level merge for logs via
     * [AndWeightRepository.restoreLog] - harmless here since there's nothing on the device yet to
     * merge against, but keeping one restore behavior means a file always restores the same way
     * regardless of where it's loaded from. */
    fun restoreFromBackup(text: String) {
        viewModelScope.launch {
            val result = BackupSerializer.parse(text)
            if (result == null) {
                uiState = uiState.copy(errorMessage = "That file doesn't look like a Measured backup.")
                return@launch
            }
            if (result.profile == null) {
                uiState = uiState.copy(errorMessage = "This backup doesn't include your profile info, so it can't finish setup on its own.")
                return@launch
            }

            repository.saveProfile(result.profile)
            result.logs.forEach { log -> repository.restoreLog(log) }
            result.settings?.let { settings ->
                settingsRepository.setThemeMode(settings.themeMode)
                settingsRepository.setTipsEnabled(settings.tipsEnabled)
                settingsRepository.setReminderEnabled(settings.reminderEnabled)
                settingsRepository.setReminderTime(settings.reminderHour, settings.reminderMinute)
                if (settings.reminderEnabled) {
                    ReminderScheduler.schedule(application, settings.reminderHour, settings.reminderMinute)
                } else {
                    ReminderScheduler.cancel(application)
                }
            }

            uiState = uiState.copy(didSave = true)
        }
    }
}
