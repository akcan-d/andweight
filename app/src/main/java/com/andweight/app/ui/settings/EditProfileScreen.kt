package com.andweight.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state = viewModel.uiState

    LaunchedEffect(state.didSave) {
        if (state.didSave) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { topBarPadding ->
        if (!state.loaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading...")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = topBarPadding.calculateTopPadding() + 12.dp,
                    bottom = contentPadding.calculateBottomPadding() + 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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

            Text("What's your goal?", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.goalDirection == GoalDirection.LOSE,
                    onClick = { viewModel.onGoalDirectionChange(GoalDirection.LOSE) },
                    label = { Text("Lose weight") },
                )
                FilterChip(
                    selected = state.goalDirection == GoalDirection.MAINTAIN,
                    onClick = { viewModel.onGoalDirectionChange(GoalDirection.MAINTAIN) },
                    label = { Text("Maintain") },
                )
                FilterChip(
                    selected = state.goalDirection == GoalDirection.GAIN,
                    onClick = { viewModel.onGoalDirectionChange(GoalDirection.GAIN) },
                    label = { Text("Gain weight") },
                )
            }

            val direction = state.goalDirection
            val startWeightKg = state.startWeightText.toDoubleOrNull()?.let { state.weightUnit.toKg(it) }

            if (direction == GoalDirection.LOSE || direction == GoalDirection.GAIN) {
                OutlinedTextField(
                    value = state.goalWeightText,
                    onValueChange = viewModel::onGoalWeightChange,
                    label = { Text("Goal weight (${state.weightUnit.name.lowercase()})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (direction != null && direction != GoalDirection.MAINTAIN && startWeightKg != null) {
                Text("Pace", style = MaterialTheme.typography.labelLarge)
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
                    "We'll help you hold steady around your current weight rather than lose or gain.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

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

            Text(
                "Height, age, and activity level below only feed your starting calorie estimate, " +
                    "before we have any real results of yours to go on. Once you've logged enough " +
                    "days, your target adapts from what's actually happened to your weight - so " +
                    "there's no need to update activity level just because a week was busier or " +
                    "quieter than usual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.weightUnit.isMetric) {
                OutlinedTextField(
                    value = state.heightCmText,
                    onValueChange = viewModel::onHeightCmChange,
                    label = { Text("Height (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.heightFeetText,
                        onValueChange = viewModel::onHeightFeetChange,
                        label = { Text("Height (ft)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.heightInchesText,
                        onValueChange = viewModel::onHeightInchesChange,
                        label = { Text("Height (in)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

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

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save changes")
            }
        }
    }
}

private fun formatRate(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString() else rounded.toString()
}
