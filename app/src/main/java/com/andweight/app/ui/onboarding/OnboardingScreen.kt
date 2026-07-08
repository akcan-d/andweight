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
                "Just a few numbers to get started - you can adjust the plan anytime.",
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
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.goalWeightText,
                onValueChange = viewModel::onGoalWeightChange,
                label = { Text("Goal weight (${state.weightUnit.name.lowercase()})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.goalRateText,
                onValueChange = viewModel::onGoalRateChange,
                label = { Text("Target change per week (${state.weightUnit.name.lowercase()})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

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
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.ageText,
                    onValueChange = viewModel::onAgeChange,
                    label = { Text("Age") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                "Safety note: recommended daily calories will never be suggested below 1200 (women) or 1500 (men), regardless of your goal.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
