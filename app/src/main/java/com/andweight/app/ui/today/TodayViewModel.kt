package com.andweight.app.ui.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.domain.IntakeLevel
import com.andweight.app.domain.IntakeResolver
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
    val justSaved: Boolean = false,
    val floorWasApplied: Boolean = false,
)

class TodayViewModel(private val repository: AndWeightRepository) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val profile: StateFlow<UserProfile?> = repository.profileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tdeeState: StateFlow<TdeeState?> = repository.tdeeStateFlow(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var formState by mutableStateOf(CheckInFormState())
        private set

    init {
        viewModelScope.launch {
            val existingProfile = repository.getProfileOnce()
            val existing = repository.getLogForDate(today)
            if (existingProfile != null && existing != null) {
                formState = formState.copy(
                    weightText = existing.weightKg?.let { kg ->
                        formatWeight(existingProfile.weightUnit.fromKg(kg))
                    } ?: "",
                    justSaved = true,
                )
            }
        }
    }

    fun onWeightChange(text: String) {
        formState = formState.copy(weightText = text, justSaved = false)
    }

    fun onIntakeSelected(level: IntakeLevel) {
        formState = formState.copy(selectedIntake = level, useExact = false, justSaved = false)
    }

    fun onToggleExact() {
        formState = formState.copy(useExact = !formState.useExact, justSaved = false)
    }

    fun onExactCaloriesChange(text: String) {
        formState = formState.copy(exactCaloriesText = text, justSaved = false)
    }

    fun saveCheckIn() {
        val currentProfile = profile.value ?: return
        val currentTdee = tdeeState.value?.currentTdee ?: return

        val weightKg = formState.weightText.toDoubleOrNull()?.let { currentProfile.weightUnit.toKg(it) }

        val resolution = if (formState.useExact) {
            val exact = formState.exactCaloriesText.toDoubleOrNull()
            if (exact == null) {
                formState = formState.copy(errorMessage = "Enter today's calories, or switch back to the quick pick.")
                return
            }
            IntakeResolver.resolveExact(exact, currentProfile.sex)
        } else {
            val level = formState.selectedIntake
            if (level == null) {
                formState = formState.copy(errorMessage = "Choose how much you felt like you ate today.")
                return
            }
            IntakeResolver.resolveQuickPick(level, currentTdee, currentProfile.sex)
        }

        viewModelScope.launch {
            repository.saveLog(
                date = today,
                weightKg = weightKg,
                calories = resolution.calories,
                intakeLevel = if (formState.useExact) null else formState.selectedIntake,
                wasExact = formState.useExact,
            )
            formState = formState.copy(
                errorMessage = null,
                justSaved = true,
                floorWasApplied = resolution.floorApplied,
            )
        }
    }

    private fun formatWeight(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
