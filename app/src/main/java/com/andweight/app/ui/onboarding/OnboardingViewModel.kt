package com.andweight.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.domain.ActivityLevel
import com.andweight.app.domain.Sex
import com.andweight.app.domain.UserProfile
import com.andweight.app.domain.WeightUnit
import kotlinx.coroutines.launch
import java.time.LocalDate

data class OnboardingUiState(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val startWeightText: String = "",
    val goalWeightText: String = "",
    val goalRateText: String = "",
    val sex: Sex = Sex.UNSPECIFIED,
    val heightCmText: String = "",
    val ageText: String = "",
    val activityLevel: ActivityLevel = ActivityLevel.LIGHT,
    val showAdvanced: Boolean = false,
    val errorMessage: String? = null,
)

class OnboardingViewModel(private val repository: AndWeightRepository) : ViewModel() {

    var uiState by mutableStateOf(OnboardingUiState())
        private set

    fun onWeightUnitChange(unit: WeightUnit) {
        uiState = uiState.copy(weightUnit = unit)
    }

    fun onStartWeightChange(text: String) {
        uiState = uiState.copy(startWeightText = text)
    }

    fun onGoalWeightChange(text: String) {
        uiState = uiState.copy(goalWeightText = text)
    }

    fun onGoalRateChange(text: String) {
        uiState = uiState.copy(goalRateText = text)
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

    fun save(onSaved: () -> Unit) {
        val startWeight = uiState.startWeightText.toDoubleOrNull()
        val goalWeight = uiState.goalWeightText.toDoubleOrNull()
        val goalRate = uiState.goalRateText.toDoubleOrNull()

        if (startWeight == null || startWeight <= 0.0) {
            uiState = uiState.copy(errorMessage = "Enter your current weight.")
            return
        }
        if (goalWeight == null || goalWeight <= 0.0) {
            uiState = uiState.copy(errorMessage = "Enter your goal weight.")
            return
        }
        val needsRate = kotlin.math.abs(goalWeight - startWeight) > 0.01
        if (needsRate && (goalRate == null || goalRate <= 0.0)) {
            uiState = uiState.copy(errorMessage = "Enter your target weekly rate of change.")
            return
        }

        val unit = uiState.weightUnit
        val height = uiState.heightCmText.toDoubleOrNull()
        val age = uiState.ageText.toIntOrNull()

        val profile = UserProfile(
            startDate = LocalDate.now(),
            weightUnit = unit,
            startWeightKg = unit.toKg(startWeight),
            goalWeightKg = unit.toKg(goalWeight),
            goalRateKgPerWeek = unit.toKg(goalRate ?: 0.0),
            sex = uiState.sex,
            heightCm = height,
            age = age,
            activityLevel = uiState.activityLevel,
        )

        viewModelScope.launch {
            repository.saveProfile(profile)
            onSaved()
        }
    }
}
