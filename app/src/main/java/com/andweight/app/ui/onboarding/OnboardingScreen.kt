package com.andweight.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andweight.app.domain.ActivityLevel
import com.andweight.app.domain.GoalDirection
import com.andweight.app.domain.PaceGuidance
import com.andweight.app.domain.PaceOption
import com.andweight.app.domain.Sex
import com.andweight.app.domain.WeightUnit
import com.andweight.app.ui.AppViewModelProvider

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state = viewModel.uiState

    LaunchedEffect(state.didSave) {
        if (state.didSave) onDone()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Let's set up AndWeight", style = MaterialTheme.typography.headlineSmall)
            Text(
                "This is a health tool first, not a race to lose weight. We'll help you find a " +
                    "sustainable pace that fits your body - you can always adjust the plan later.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("Weight unit", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.weightUnit == WeightUnit.KG,
                    onClick = { viewModel.onWeightUnitChange(WeightUnit.KG) },
                    label = { Text("Kg") },
                )
                FilterChip(
                    selected = state.weightUnit == WeightUnit.LB,
                    onClick = { viewModel.onWeightUnitChange(WeightUnit.LB) },
                    label = { Text("Lb") },
                )
            }

            OutlinedTextField(
                value = state.startWeightText,
                onValueChange = viewModel::onStartWeightChange,
                label = { Text("Current weight (${state.weightUnit.name.lowercase()})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.goalWeightText,
                onValueChange = viewModel::onGoalWeightChange,
                label = { Text("Goal weight (${state.weightUnit.name.lowercase()})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val direction = state.previewDirection
            val startWeightKg = state.startWeightText.toDoubleOrNull()?.let { state.weightUnit.toKg(it) }
            if (direction != null && direction != GoalDirection.MAINTAIN && startWeightKg != null) {
                Text("Pace", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Even the fastest option is capped at a rate considered safe for your weight - " +
                        "going faster tends to cost muscle, not just fat, and rarely stays off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaceOption.entries.forEach { option ->
                        val rateKg = PaceGuidance.rateForOption(option, startWeightKg, direction)
                        val rateInUnit = state.weightUnit.fromKg(rateKg)
                        FilterChip(
                            selected = state.selectedPace == option,
                            onClick = { viewModel.onPaceSelected(option) },
                            label = {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(option.label)
                                    Text(
                                        "~${formatRate(rateInUnit)} ${state.weightUnit.name.lowercase()}/week",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else if (direction == GoalDirection.MAINTAIN) {
                Text(
                    "Your goal matches your current weight, so we'll help you hold steady rather than lose or gain.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            TextButton(onClick = viewModel::onToggleAdvanced) {
                Text(if (state.showAdvanced) "Hide optional details" else "Add optional details (sex, height, age) for a better starting estimate")
            }

            if (state.showAdvanced) {
                Text("Sex", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.sex == Sex.FEMALE,
                        onClick = { viewModel.onSexChange(Sex.FEMALE) },
                        label = { Text("Female") },
                    )
                    FilterChip(
                        selected = state.sex == Sex.MALE,
                        onClick = { viewModel.onSexChange(Sex.MALE) },
                        label = { Text("Male") },
                    )
                    FilterChip(
                        selected = state.sex == Sex.UNSPECIFIED,
                        onClick = { viewModel.onSexChange(Sex.UNSPECIFIED) },
                        label = { Text("Prefer not to say") },
                    )
                }

                OutlinedTextField(
                    value = state.heightCmText,
                    onValueChange = viewModel::onHeightChange,
                    label = { Text("Height (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.ageText,
                    onValueChange = viewModel::onAgeChange,
                    label = { Text("Age") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Activity level", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ActivityLevel.entries.forEach { level ->
                        FilterChip(
                            selected = state.activityLevel == level,
                            onClick = { viewModel.onActivityLevelChange(level) },
                            label = { Text(level.label) },
                            modifier = Modifier.widthIn(min = 0.dp),
                        )
                    }
                }
            }

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start tracking")
            }

            Text(
                "Recommended daily calories will never go below 1200 (women) or 1500 (men). But numbers " +
                    "only tell part of the story: muscle mass and body composition matter as much as the " +
                    "scale, and losing weight quickly raises the risk of losing muscle along with fat. If " +
                    "you have any history of disordered eating, please talk to a doctor before using an " +
                    "app like this one.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun formatRate(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString() else rounded.toString()
}
