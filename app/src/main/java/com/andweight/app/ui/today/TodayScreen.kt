package com.andweight.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    onEditProfile: () -> Unit,
    initialDate: LocalDate? = null,
    onInitialDateConsumed: () -> Unit = {},
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val profile by viewModel.profile.collectAsState()
    val tdeeState by viewModel.tdeeState.collectAsState()
    val confidence by viewModel.confidence.collectAsState()

    // Journey's "edit this day" action lands here exactly like a normal tab switch (see
    // MainScreen) - this just jumps the day picker to the requested date once, rather than
    // treating it as a separate screen the person needs to back out of.
    LaunchedEffect(initialDate) {
        if (initialDate != null) {
            viewModel.jumpToDate(initialDate)
            onInitialDateConsumed()
        }
    }

    // Whatever day is currently open leads with the check-in card while it still needs input -
    // that's the actionable thing, especially for a new user who hasn't logged anything yet and
    // would otherwise have to scroll past a stats card to find it. Once the day is fully logged,
    // the status card leads instead, since there's nothing left to act on and the progress is now
    // the more interesting thing to see first.
    val items = buildList<@Composable () -> Unit> {
        if (viewModel.isDayComplete) {
            add { StatusCard(profile, tdeeState, confidence, onEditProfile) }
            add { CheckInCard(viewModel, profile) }
        } else {
            add { CheckInCard(viewModel, profile) }
            add { StatusCard(profile, tdeeState, confidence, onEditProfile) }
        }
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
private fun StatusCard(profile: UserProfile?, tdeeState: TdeeState?, confidence: ConfidenceAssessment?, onEditProfile: () -> Unit) {
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
                    ConfidenceBadge(confidence, onEditProfile)
                }
            }

            HeroSection(profile, tdeeState)

            HorizontalDivider()

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

            StatRow("Today's target", "${tdeeState.recommendedCalories.roundToInt()} kcal")

            if (tdeeState.recommendedCaloriesFloorApplied) {
                Text(
                    "Your goal pace would call for fewer calories than is safe, so today's target has been raised to a safe minimum.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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
                Text(
                    Phrasing.weightTrendSentence(tdeeState.currentWeightKg - tdeeState.earliestKnownWeightKg, unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            tdeeState.weeksToGoal == null -> {
                Text("Maintaining", style = MaterialTheme.typography.headlineMedium)
                Text("Aiming to hold steady at your current weight", style = MaterialTheme.typography.bodyMedium)
            }
            tdeeState.weeksToGoal <= 0.0 -> {
                Text("Goal reached!", style = MaterialTheme.typography.headlineMedium)
                Text("Nice work getting here.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> {
                val daysToGoal = tdeeState.goalDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }?.coerceAtLeast(0) ?: 0
                val countdown = Phrasing.goalCountdown(daysToGoal)
                ProgressRing(
                    progress = goalProgress(profile, tdeeState) ?: 0f,
                    value = if (countdown.isApproximate) "~${countdown.amount}" else "${countdown.amount}",
                    label = "${countdown.unitLabel} to go",
                )
                tdeeState.goalDate?.let {
                    Text(
                        "Target date: ${Phrasing.formatTargetDate(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** A ring instead of a linear bar for the goal countdown - it's the one number on this screen
 * meant to be taken in at a glance, so it gets the most visual weight. */
@Composable
private fun ProgressRing(progress: Float, value: String, label: String) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val ringColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.size(172.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().size(172.dp)) {
            val strokeWidth = 16.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            if (viewModel.existingLog == null) {
                // Neither field has been saved yet - one combined form with a single Save button,
                // rather than two separate per-field forms that would otherwise both show at once.
                BlankDayForm(viewModel, profile)
            } else {
                WeightSection(viewModel, profile)
                MealSection(viewModel)
            }

            if (viewModel.existingLog != null) {
                DeleteDayButton(viewModel)
            }
        }
    }
}

@Composable
private fun AllSetBanner() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
    var showDatePicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = viewModel::goToPreviousDay, enabled = viewModel.canGoToPreviousDay()) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous day")
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                dayLabel(viewModel.selectedDate, viewModel.actualToday),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Jump to a specific date")
            }
            if (viewModel.canJumpToToday()) {
                IconButton(onClick = viewModel::jumpToToday) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Jump to today")
                }
            }
        }

        IconButton(onClick = viewModel::goToNextDay, enabled = viewModel.canGoToNextDay()) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next day")
        }
    }

    if (showDatePicker) {
        DayPickerDialog(
            initialDate = viewModel.selectedDate,
            latestSelectableDate = viewModel.actualToday,
            onConfirm = { date ->
                viewModel.jumpToDate(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(
    initialDate: LocalDate,
    latestSelectableDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return !date.isAfter(latestSelectableDate)
            }
        },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = state)
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(dateFormatter)
}

@Composable
private fun BlankDayForm(viewModel: TodayViewModel, profile: UserProfile?) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weight", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            WeightInputField(viewModel, profile)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Meals", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            MealInputFields(viewModel)
        }
        Button(onClick = viewModel::saveDay, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
    }
}

@Composable
private fun WeightInputField(viewModel: TodayViewModel, profile: UserProfile?) {
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
}

@Composable
private fun WeightSection(viewModel: TodayViewModel, profile: UserProfile?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Weight", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

        if (viewModel.isEditingWeight) {
            WeightInputField(viewModel, profile)
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
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit weight")
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
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit meal info")
                }
            }
        }

        if (viewModel.belowFloorWarning) {
            Text(
                "This is below the general safety minimum for a full day's eating. We've kept the " +
                    "exact number you entered - if this wasn't a one-off, it's worth talking to a doctor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun MealForm(viewModel: TodayViewModel) {
    MealInputFields(viewModel)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::saveMeal) { Text("Save") }
        if (viewModel.existingLog?.calories != null) {
            TextButton(onClick = viewModel::onCancelEditingMeal) { Text("Cancel") }
        }
    }
}

@Composable
private fun MealInputFields(viewModel: TodayViewModel) {
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
private fun ConfidenceBadge(confidence: ConfidenceAssessment, onEditProfile: () -> Unit) {
    var showExplanation by remember { mutableStateOf(false) }
    val color = when (confidence.level) {
        ConfidenceLevel.LOW -> Color(0xFFE53935)
        ConfidenceLevel.MEDIUM -> Color(0xFFF9A825)
        ConfidenceLevel.HIGH -> Color(0xFF43A047)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { showExplanation = true }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(confidence.level.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
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
            dismissButton = if (confidence.promptProfileDetails) {
                {
                    TextButton(onClick = {
                        showExplanation = false
                        onEditProfile()
                    }) {
                        Text("Edit profile")
                    }
                }
            } else {
                null
            },
        )
    }
}

private fun oneDecimal(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
