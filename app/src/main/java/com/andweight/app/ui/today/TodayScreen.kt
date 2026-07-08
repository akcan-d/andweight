package com.andweight.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andweight.app.domain.IntakeLevel
import com.andweight.app.domain.TdeeState
import com.andweight.app.domain.UserProfile
import com.andweight.app.ui.AppViewModelProvider
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val profile by viewModel.profile.collectAsState()
    val tdeeState by viewModel.tdeeState.collectAsState()
    val form = viewModel.formState

    val items = buildList<@Composable () -> Unit> {
        add { StatusCard(profile, tdeeState) }
        add { CheckInCard(form, viewModel) }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items) { it() }
    }
}

@Composable
private fun StatusCard(profile: UserProfile?, tdeeState: TdeeState?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (profile == null || tdeeState == null) {
                CircularProgressIndicator()
                return@Column
            }
            val unit = profile.weightUnit

            Text("Your current stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            StatRow("Current weight", "${oneDecimal(unit.fromKg(tdeeState.currentWeightKg))} ${unit.name.lowercase()}")
            StatRow("Estimated TDEE", "${tdeeState.currentTdee.roundToInt()} kcal/day")
            StatRow("Today's target", "${tdeeState.recommendedCalories.roundToInt()} kcal")

            val goalText = when {
                tdeeState.weeksToGoal == null -> "Maintaining current weight"
                tdeeState.weeksToGoal <= 0.0 -> "Goal reached!"
                else -> {
                    val date = tdeeState.goalDate?.format(dateFormatter) ?: ""
                    "~${formatWeeks(tdeeState.weeksToGoal)} to go, around $date"
                }
            }
            StatRow("Forecast", goalText)

            if (tdeeState.recommendedCaloriesFloorApplied) {
                Text(
                    "Your goal rate would call for fewer calories than is safe, so today's target has been raised to a safe minimum.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CheckInCard(form: CheckInFormState, viewModel: TodayViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Today's check-in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = form.weightText,
                onValueChange = viewModel::onWeightChange,
                label = { Text("Today's weight (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            if (!form.useExact) {
                Text("How much did you feel like you ate today?", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IntakeLevel.entries.forEach { level ->
                        FilterChip(
                            selected = form.selectedIntake == level,
                            onClick = { viewModel.onIntakeSelected(level) },
                            label = { Text(level.label) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                TextButton(onClick = viewModel::onToggleExact) {
                    Text("I'd rather enter my exact calories")
                }
            } else {
                OutlinedTextField(
                    value = form.exactCaloriesText,
                    onValueChange = viewModel::onExactCaloriesChange,
                    label = { Text("Calories eaten today") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = viewModel::onToggleExact) {
                    Text("Use the quick pick instead")
                }
            }

            form.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (form.justSaved) {
                Text("Saved for today.", color = MaterialTheme.colorScheme.primary)
            }

            Button(onClick = viewModel::saveCheckIn, modifier = Modifier.fillMaxWidth()) {
                Text("Save today's check-in")
            }
        }
    }
}

private fun oneDecimal(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

private fun formatWeeks(weeks: Double): String {
    return if (weeks == weeks.toLong().toDouble()) "${weeks.toLong()} weeks" else "$weeks weeks"
}
