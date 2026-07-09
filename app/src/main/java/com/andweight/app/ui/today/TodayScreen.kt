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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andweight.app.domain.AdviceSentiment
import com.andweight.app.domain.CoachingAdvisor
import com.andweight.app.domain.IntakeLevel
import com.andweight.app.domain.Phrasing
import com.andweight.app.domain.TdeeState
import com.andweight.app.domain.UserProfile
import com.andweight.app.ui.AppViewModelProvider
import java.time.LocalDate
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

    val items = buildList<@Composable () -> Unit> {
        add { StatusCard(profile, tdeeState) }
        add { CheckInCard(viewModel, profile) }
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
    var showDetails by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (profile == null || tdeeState == null) {
                CircularProgressIndicator()
                return@Column
            }
            val unit = profile.weightUnit

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Your current stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Hide details" else "Show details")
                }
            }

            val advice = CoachingAdvisor.advise(profile, tdeeState)
            Text(
                advice.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = when (advice.sentiment) {
                    AdviceSentiment.POSITIVE -> MaterialTheme.colorScheme.primary
                    AdviceSentiment.CAUTION -> MaterialTheme.colorScheme.error
                    AdviceSentiment.NEUTRAL -> MaterialTheme.colorScheme.onSurface
                },
            )

            if (showDetails) {
                StatRow("Current weight", "${oneDecimal(unit.fromKg(tdeeState.currentWeightKg))} ${unit.name.lowercase()}")
                StatRow("Estimated TDEE", "${tdeeState.currentTdee.roundToInt()} kcal/day")
                StatRow("Today's target", "${tdeeState.recommendedCalories.roundToInt()} kcal")
                StatRow(
                    "Forecast",
                    when {
                        tdeeState.weeksToGoal == null -> "Maintaining"
                        tdeeState.weeksToGoal <= 0.0 -> "Goal reached!"
                        else -> "~${formatWeeksNumeric(tdeeState.weeksToGoal)} to go"
                    },
                )
            } else {
                Text(Phrasing.weightTrendSentence(tdeeState.currentWeightKg - profile.startWeightKg, unit), style = MaterialTheme.typography.bodyMedium)
                Text(Phrasing.calorieTargetSentence(tdeeState.recommendedCalories, tdeeState.currentTdee), style = MaterialTheme.typography.bodyMedium)
                Text(Phrasing.forecastSentence(tdeeState.weeksToGoal, tdeeState.goalDate), style = MaterialTheme.typography.bodyMedium)
            }

            if (tdeeState.recommendedCaloriesFloorApplied) {
                Text(
                    "Your goal pace would call for fewer calories than is safe, so today's target has been raised to a safe minimum.",
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
private fun CheckInCard(viewModel: TodayViewModel, profile: UserProfile?) {
    val form = viewModel.formState

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DayPager(viewModel)

            if (viewModel.isEditing) {
                CheckInForm(form, viewModel, canCancel = viewModel.existingLog != null)
            } else {
                DaySummary(viewModel, profile)
            }
        }
    }
}

@Composable
private fun DayPager(viewModel: TodayViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = viewModel::goToPreviousDay, enabled = viewModel.canGoToPreviousDay()) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
        }
        Text(
            dayLabel(viewModel.selectedDate, viewModel.actualToday),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = viewModel::goToNextDay, enabled = viewModel.canGoToNextDay()) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
        }
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(dateFormatter)
}

@Composable
private fun DaySummary(viewModel: TodayViewModel, profile: UserProfile?) {
    val log = viewModel.existingLog ?: return
    val unit = profile?.weightUnit

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (log.weightKg != null && unit != null) {
            StatRow("Weight logged", Phrasing.formatWeight(log.weightKg, unit))
        }
        val intakeDescription = when {
            log.wasExactEntry -> "You logged an exact amount: ${log.calories?.roundToInt()} kcal"
            log.intakeLevel != null -> "You said: \"${log.intakeLevel.label}\""
            else -> "No meal info logged"
        }
        Text(intakeDescription, style = MaterialTheme.typography.bodyMedium)

        if (viewModel.lastSaveHitFloor) {
            Text(
                "Raised to a safe minimum for this day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        OutlinedButton(onClick = viewModel::onStartEditing, modifier = Modifier.fillMaxWidth()) {
            Text("Edit this day")
        }
    }
}

@Composable
private fun CheckInForm(form: CheckInFormState, viewModel: TodayViewModel, canCancel: Boolean) {
    OutlinedTextField(
        value = form.weightText,
        onValueChange = viewModel::onWeightChange,
        label = { Text("Weight that day (optional)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )

    if (!form.useExact) {
        Text("How much did you feel like you ate that day?", style = MaterialTheme.typography.labelLarge)
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
            label = { Text("Calories eaten that day") },
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

    Button(onClick = viewModel::saveCheckIn, modifier = Modifier.fillMaxWidth()) {
        Text("Save")
    }

    if (canCancel) {
        TextButton(onClick = viewModel::onCancelEditing, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

private fun oneDecimal(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

private fun formatWeeksNumeric(weeks: Double): String {
    return if (weeks == weeks.toLong().toDouble()) "${weeks.toLong()} weeks" else "$weeks weeks"
}
