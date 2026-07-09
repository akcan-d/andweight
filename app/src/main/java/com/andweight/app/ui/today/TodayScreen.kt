package com.andweight.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andweight.app.domain.AdviceSentiment
import com.andweight.app.domain.CoachingAdvisor
import com.andweight.app.domain.ConfidenceAssessment
import com.andweight.app.domain.ConfidenceLevel
import com.andweight.app.domain.DailyLog
import com.andweight.app.domain.GoalDirection
import com.andweight.app.domain.IntakeLevel
import com.andweight.app.domain.Phrasing
import com.andweight.app.domain.TdeeState
import com.andweight.app.domain.UserProfile
import com.andweight.app.ui.AppViewModelProvider
import com.andweight.app.ui.common.IntakeChip
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val profile by viewModel.profile.collectAsState()
    val tdeeState by viewModel.tdeeState.collectAsState()
    val confidence by viewModel.confidence.collectAsState()

    val items = buildList<@Composable () -> Unit> {
        add { StatusCard(profile, tdeeState, confidence) }
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
private fun StatusCard(profile: UserProfile?, tdeeState: TdeeState?, confidence: ConfidenceAssessment?) {
    var showDetails by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (profile == null || tdeeState == null) {
                CircularProgressIndicator()
                return@Column
            }
            val unit = profile.weightUnit

            if (confidence != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ConfidenceBadge(confidence)
                }
            }

            HeroSection(profile, tdeeState)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val advice = CoachingAdvisor.advise(profile, tdeeState)
                Text(
                    advice.message,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when (advice.sentiment) {
                        AdviceSentiment.POSITIVE -> MaterialTheme.colorScheme.primary
                        AdviceSentiment.CAUTION -> MaterialTheme.colorScheme.error
                        AdviceSentiment.NEUTRAL -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Hide details" else "Show details")
                }
            }

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
                Text(Phrasing.weightTrendSentence(tdeeState.currentWeightKg - tdeeState.earliestKnownWeightKg, unit), style = MaterialTheme.typography.bodyMedium)
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
private fun HeroSection(profile: UserProfile, tdeeState: TdeeState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            tdeeState.weeksToGoal == null -> {
                Text("Maintaining", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Aiming to hold steady at your current weight", style = MaterialTheme.typography.bodyMedium)
            }
            tdeeState.weeksToGoal <= 0.0 -> {
                Text("Goal reached!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Nice work getting here.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> {
                val days = tdeeState.goalDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }?.coerceAtLeast(0) ?: 0
                Text("$days", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (days == 1L) "day until you reach your goal" else "days until you reach your goal",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        val progress = goalProgress(profile, tdeeState)
        if (progress != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

private fun goalProgress(profile: UserProfile, tdeeState: TdeeState): Float? {
    if (profile.goalDirection == GoalDirection.MAINTAIN) return null
    val start = tdeeState.earliestKnownWeightKg
    val total = kotlin.math.abs(profile.goalWeightKg - start)
    if (total <= 0.0) return 1f
    val done = kotlin.math.abs(tdeeState.currentWeightKg - start)
    return (done / total).toFloat().coerceIn(0f, 1f)
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DayPager(viewModel)

            if (viewModel.isDayComplete && viewModel.selectedDate == viewModel.actualToday) {
                AllSetBanner()
            }

            WeightSection(viewModel, profile)
            MealSection(viewModel)

            if (viewModel.existingLog != null) {
                DeleteDayButton(viewModel)
            }
        }
    }
}

@Composable
private fun AllSetBanner() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            "You're all set for today - don't forget to check in again tomorrow!",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
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

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                dayLabel(viewModel.selectedDate, viewModel.actualToday),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (viewModel.canJumpToToday()) {
                IconButton(onClick = viewModel::jumpToToday) {
                    Icon(Icons.Filled.Today, contentDescription = "Jump to today")
                }
            }
        }

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
private fun WeightSection(viewModel: TodayViewModel, profile: UserProfile?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Weight", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

        if (viewModel.isEditingWeight) {
            val state = viewModel.weightState
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onWeightTextChange,
                label = { Text("Weight (${profile?.weightUnit?.name?.lowercase() ?: "kg"})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::saveWeight) { Text("Save") }
                if (viewModel.existingLog?.weightKg != null) {
                    TextButton(onClick = viewModel::onCancelEditingWeight) { Text("Cancel") }
                }
            }
        } else {
            val log = viewModel.existingLog
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (log?.weightKg != null && profile != null) Phrasing.formatWeight(log.weightKg, profile.weightUnit) else "-",
                    style = MaterialTheme.typography.bodyLarge,
                )
                IconButton(onClick = viewModel::onStartEditingWeight) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit weight")
                }
            }
        }
    }
}

@Composable
private fun MealSection(viewModel: TodayViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Meals", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

        if (viewModel.isEditingMeal) {
            MealForm(viewModel)
        } else {
            val log = viewModel.existingLog
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (log != null) IntakeChip(log) else Text("-")
                IconButton(onClick = viewModel::onStartEditingMeal) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit meal info")
                }
            }
        }

        if (viewModel.lastSaveHitFloor) {
            Text(
                "Raised to a safe minimum for this day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun MealForm(viewModel: TodayViewModel) {
    val state = viewModel.mealState

    if (!state.useExact) {
        Text(
            "Compared to what's typical for you lately - not an exact calorie count.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IntakeLevel.entries.forEach { level ->
                FilterChip(
                    selected = state.selectedIntake == level,
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
            value = state.exactCaloriesText,
            onValueChange = viewModel::onExactCaloriesChange,
            label = { Text("Calories eaten that day") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = viewModel::onToggleExact) {
            Text("Use the quick pick instead")
        }
    }

    state.errorMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::saveMeal) { Text("Save") }
        if (viewModel.existingLog?.calories != null) {
            TextButton(onClick = viewModel::onCancelEditingMeal) { Text("Cancel") }
        }
    }
}

@Composable
private fun DeleteDayButton(viewModel: TodayViewModel) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    TextButton(
        onClick = { showDeleteConfirm = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text("Delete this day's record")
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this day?") },
            text = { Text("This removes the weight and meal info logged for ${dayLabel(viewModel.selectedDate, viewModel.actualToday)}. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteDay()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ConfidenceBadge(confidence: ConfidenceAssessment) {
    var showExplanation by remember { mutableStateOf(false) }
    val color = when (confidence.level) {
        ConfidenceLevel.LOW -> Color(0xFFE53935)
        ConfidenceLevel.MEDIUM -> Color(0xFFF9A825)
        ConfidenceLevel.HIGH -> Color(0xFF43A047)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable { showExplanation = true },
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(confidence.level.label, style = MaterialTheme.typography.labelSmall, color = color)
    }

    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text(confidence.level.label) },
            text = { Text(confidence.message) },
            confirmButton = {
                TextButton(onClick = { showExplanation = false }) {
                    Text("Got it")
                }
            },
        )
    }
}

private fun oneDecimal(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

private fun formatWeeksNumeric(weeks: Double): String {
    return if (weeks == weeks.toLong().toDouble()) "${weeks.toLong()} weeks" else "$weeks weeks"
}
