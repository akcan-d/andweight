package com.measured.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.measured.app.domain.AdviceSentiment
import com.measured.app.domain.CoachingAdvisor
import com.measured.app.domain.ConfidenceAssessment
import com.measured.app.domain.ConfidenceLevel
import com.measured.app.domain.DailyLog
import com.measured.app.domain.GoalDirection
import com.measured.app.domain.IntakeLevel
import com.measured.app.domain.Phrasing
import com.measured.app.domain.TdeeState
import com.measured.app.domain.UserProfile
import com.measured.app.domain.WeightUnit
import com.measured.app.ui.AppViewModelProvider
import com.measured.app.ui.common.InfoTooltipIcon
import com.measured.app.ui.common.IntakeChip
import com.measured.app.ui.common.SupporterOnlyDialog
import com.measured.app.ui.theme.themedFilterChipColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    onEditProfile: () -> Unit,
    initialDate: LocalDate? = null,
    onInitialDateConsumed: () -> Unit = {},
    initialShortcutTarget: String? = null,
    onInitialShortcutTargetConsumed: () -> Unit = {},
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val profile by viewModel.profile.collectAsState()
    val tdeeState by viewModel.tdeeState.collectAsState()
    val confidence by viewModel.confidence.collectAsState()
    val isSupporter by viewModel.isSupporter.collectAsState()

    // Journey's "edit this day" action lands here exactly like a normal tab switch (see
    // MainScreen) - this just jumps the day picker to the requested date once, rather than
    // treating it as a separate screen the person needs to back out of.
    LaunchedEffect(initialDate) {
        if (initialDate != null) {
            viewModel.jumpToDate(initialDate)
            onInitialDateConsumed()
        }
    }

    // The "Log weight"/"Log meal" app shortcuts land here the same way - force the relevant
    // section open for editing even if today's already been fully logged, since the whole point
    // of using the shortcut is to go straight to typing a value in.
    LaunchedEffect(initialShortcutTarget) {
        when (initialShortcutTarget) {
            "weight" -> viewModel.onStartEditingWeight()
            "meal" -> viewModel.onStartEditingMeal()
        }
        if (initialShortcutTarget != null) onInitialShortcutTargetConsumed()
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
        // Always the same order - the check-in card used to jump to the top whenever the
        // selected day still needed input, which read as the whole screen lurching around.
        // Instead, the check-in card itself visually asks for attention (a colored border) when
        // it still needs something, rather than physically relocating - so the status overview
        // can lead, with the already-self-announcing check-in card below it.
        item {
            StatusCard(
                profile,
                tdeeState,
                confidence,
                onEditProfile,
                viewModel::saveGoalWeight,
                isSupporter,
                viewModel::setSupporter,
            )
        }
        item { CheckInCard(viewModel, profile) }
    }
}

@Composable
private fun StatusCard(
    profile: UserProfile?,
    tdeeState: TdeeState?,
    confidence: ConfidenceAssessment?,
    onEditProfile: () -> Unit,
    onSaveGoalWeight: (Double) -> Unit,
    isSupporter: Boolean,
    onSetSupporter: (Boolean) -> Unit,
) {
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

            HeroSection(profile, tdeeState, onSaveGoalWeight, isSupporter, onSetSupporter)

            HorizontalDivider()

            val advice = CoachingAdvisor.advise(profile, tdeeState)
            val (adviceContainer, adviceOnContainer, adviceIcon) = when (advice.sentiment) {
                AdviceSentiment.POSITIVE -> Triple(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    Icons.Outlined.CheckCircle,
                )
                AdviceSentiment.CAUTION -> Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    Icons.Outlined.WarningAmber,
                )
                // Deliberately not the same glyph the app uses elsewhere for a tappable "i" -
                // this icon isn't interactive, and there's nothing more to show behind it.
                AdviceSentiment.NEUTRAL -> Triple(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    Icons.Outlined.Schedule,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(adviceContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(adviceIcon, contentDescription = null, tint = adviceOnContainer, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(7.dp))
                Text(
                    advice.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = adviceOnContainer,
                    textAlign = TextAlign.Center,
                )
            }

            // The exact kcal figure is a calculated detail, not the headline - the chip above
            // already says the thing that actually matters ("eat a bit less today"). The one
            // exception is the safety-floor case: when the target's been raised for safety, the
            // number is part of that disclosure, so it stays visible rather than hiding behind
            // a tap.
            if (tdeeState.recommendedCaloriesFloorApplied) {
                StatRow("Today's target", "${tdeeState.recommendedCalories.roundToInt()} kcal")
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
                if (!tdeeState.recommendedCaloriesFloorApplied) {
                    StatRow("Today's target", "${tdeeState.recommendedCalories.roundToInt()} kcal")
                }
                StatRow("Starting weight", "${oneDecimal(unit.fromKg(profile.startWeightKg))} ${unit.name.lowercase()}")
                TdeeStatRow(tdeeState.currentTdee)
                WeightTrendStatRow(tdeeState.currentWeightKg - tdeeState.earliestKnownWeightKg, unit)
            }
        }
    }
}

/** Which fact the hero display's center number/label shows - all three read the exact same
 * underlying weight-progress fraction differently, so switching between them never disagrees
 * with the ring/bar fill (see the comment below on why that fill never changes meaning). */
private enum class HeroStat(val chipLabel: String) {
    REMAINING("To go"),
    PERCENT("Percent"),
    MOVED("So far"),
}

private enum class HeroDisplayStyle { RING, BAR }

@Composable
private fun HeroSection(
    profile: UserProfile,
    tdeeState: TdeeState,
    onSaveGoalWeight: (Double) -> Unit,
    isSupporter: Boolean,
    onSetSupporter: (Boolean) -> Unit,
) {
    var heroStat by remember { mutableStateOf(HeroStat.REMAINING) }
    var displayStyle by remember { mutableStateOf(HeroDisplayStyle.RING) }
    var showSupporterLockedDialog by remember { mutableStateOf(false) }

    if (showSupporterLockedDialog) {
        SupporterOnlyDialog(
            onDismiss = { showSupporterLockedDialog = false },
            onBecomeSupporter = {
                showSupporterLockedDialog = false
                onSetSupporter(true)
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            tdeeState.weeksToGoal == null -> {
                Text("Maintaining", style = MaterialTheme.typography.headlineMedium)
                Text("Holding steady", style = MaterialTheme.typography.bodyMedium)
            }
            tdeeState.weeksToGoal <= 0.0 -> {
                Text("Goal reached!", style = MaterialTheme.typography.headlineMedium)
                Text("Nice work getting here.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> {
                // The ring/bar's fill and its center number used to measure two different things -
                // the fill was how much weight had been lost/gained so far, but the number in the
                // middle was a time countdown. Early weight loss is often faster than the average
                // pace a countdown assumes, so the two could read as flatly contradicting each
                // other (a ring that looks a quarter full next to a countdown that's barely
                // moved). The fill now always means one thing - weight progress toward the goal -
                // regardless of which of [HeroStat]'s three equivalent readings of that same
                // progress is shown as the number, so switching stats can't reintroduce that.
                val unit = profile.weightUnit
                val unitLabel = unit.name.lowercase()
                val progress = goalProgress(profile, tdeeState) ?: 0f
                val remainingKg = abs(profile.goalWeightKg - tdeeState.currentWeightKg)
                val soFarKg = tdeeState.currentWeightKg - tdeeState.earliestKnownWeightKg

                val (heroValue, heroLabel) = when (heroStat) {
                    HeroStat.REMAINING -> oneDecimal(unit.fromKg(remainingKg)) to "$unitLabel to go"
                    HeroStat.PERCENT -> "${(progress * 100).roundToInt()}%" to "to goal"
                    HeroStat.MOVED -> {
                        val label = when {
                            abs(soFarKg) < 0.05 -> "so far"
                            soFarKg < 0 -> "$unitLabel down"
                            else -> "$unitLabel up"
                        }
                        val value = if (abs(soFarKg) < 0.05) "0" else oneDecimal(unit.fromKg(abs(soFarKg)))
                        value to label
                    }
                }

                val effectiveStyle = if (isSupporter) displayStyle else HeroDisplayStyle.RING
                when (effectiveStyle) {
                    HeroDisplayStyle.RING -> ProgressRing(progress = progress, value = heroValue, label = heroLabel)
                    HeroDisplayStyle.BAR -> ProgressBarDisplay(progress = progress, value = heroValue, label = heroLabel)
                }

                // Which stat the ring/bar's center number shows, and which shape it's drawn as,
                // are two unrelated choices - they used to share one crowded row, which read as
                // one confusing control and made the display-style icons easy to miss entirely.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    HeroStat.entries.forEach { stat ->
                        FilterChip(
                            selected = heroStat == stat,
                            onClick = { heroStat = stat },
                            label = { Text(stat.chipLabel, style = MaterialTheme.typography.labelSmall) },
                            colors = themedFilterChipColors(),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    HeroDisplayStyleToggle(
                        selected = effectiveStyle,
                        isSupporter = isSupporter,
                        onChange = { displayStyle = it },
                        onLockedTap = { showSupporterLockedDialog = true },
                    )
                }

                val daysToGoal = tdeeState.goalDate?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }?.coerceAtLeast(0) ?: 0
                val countdown = Phrasing.goalCountdown(daysToGoal)
                val countdownText = if (countdown.isApproximate) "~${countdown.amount}" else "${countdown.amount}"

                // The hero display alone only shows one reading of progress - pairing it with a
                // rough time estimate gives a fuller picture without needing a second ring/bar.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MiniStat(value = countdownText, label = "${countdown.unitLabel} to go")
                }

                tdeeState.goalDate?.let {
                    Text(
                        "Target: ${Phrasing.formatTargetDate(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Previously only visible under Show details - the number someone most wants to check at
        // a glance shouldn't need an extra tap, regardless of which goal state the ring above is in.
        Text(
            "Current weight: ${oneDecimal(profile.weightUnit.fromKg(tdeeState.currentWeightKg))} ${profile.weightUnit.name.lowercase()}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        GoalWeightRow(profile, onSaveGoalWeight)
    }
}

/** Ring vs bar is a supporter perk, shown the same way as the chart style toggle in Journey -
 * still visible and tappable when locked, with a small lock badge, rather than disappearing. */
@Composable
private fun HeroDisplayStyleToggle(
    selected: HeroDisplayStyle,
    isSupporter: Boolean,
    onChange: (HeroDisplayStyle) -> Unit,
    onLockedTap: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(HeroDisplayStyle.RING) }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.DonutLarge,
                contentDescription = "Ring display",
                tint = if (selected == HeroDisplayStyle.RING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Box {
            IconButton(
                onClick = { if (isSupporter) onChange(HeroDisplayStyle.BAR) else onLockedTap() },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Outlined.LinearScale,
                    contentDescription = if (isSupporter) "Bar display" else "Bar display - supporters only",
                    tint = if (selected == HeroDisplayStyle.BAR) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (!isSupporter) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(11.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(1.dp),
                )
            }
        }
    }
}

/** A one-number quick edit for the goal weight, right where it's displayed - previously the only
 * way to change it was the full Edit Profile screen. [UserProfile.goalDirection] is derived from
 * how the saved value compares to the current weight, so lose/maintain/gain updates on its own. */
@Composable
private fun GoalWeightRow(profile: UserProfile, onSaveGoalWeight: (Double) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val unit = profile.weightUnit

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Goal: ${oneDecimal(unit.fromKg(profile.goalWeightKg))} ${unit.name.lowercase()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = { showDialog = true }, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "Edit goal weight",
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDialog) {
        EditGoalWeightDialog(
            currentGoalKg = profile.goalWeightKg,
            unit = unit,
            onDismiss = { showDialog = false },
            onSave = { newKg ->
                onSaveGoalWeight(newKg)
                showDialog = false
            },
        )
    }
}

@Composable
private fun EditGoalWeightDialog(currentGoalKg: Double, unit: WeightUnit, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(oneDecimal(unit.fromKg(currentGoalKg))) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Goal weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text("Goal weight (${unit.name.lowercase()})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = error != null,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = text.toDoubleOrNull()
                if (value == null || value <= 0.0) {
                    error = "Enter a valid weight."
                    return@TextButton
                }
                val kg = unit.toKg(value)
                if (kg < 20.0 || kg > 400.0) {
                    error = "Enter a realistic weight."
                    return@TextButton
                }
                onSave(kg)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A ring instead of a linear bar for the goal countdown - it's the one number on this screen
 * meant to be taken in at a glance, so it gets the most visual weight. */
@Composable
private fun ProgressRing(progress: Float, value: String, label: String) {
    val ringColor = MaterialTheme.colorScheme.primary
    // A tint of the ring's own color, not a theme-neutral gray - surfaceVariant is deliberately
    // low-contrast against a Card's background by Material 3's own design, so at 0% progress the
    // "empty" track could read as not there at all rather than as a track waiting to fill.
    val trackColor = ringColor.copy(alpha = 0.18f)

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

/** The supporter-only alternative to [ProgressRing] - same number/label, a straight bar instead
 * of a ring. Kept visually consistent with it (same tinted-track technique, same text styling)
 * so switching between them reads as a style choice, not two unrelated widgets. */
@Composable
private fun ProgressBarDisplay(progress: Float, value: String, label: String) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = barColor.copy(alpha = 0.18f)

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(value, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(16.dp)) {
            val strokeWidth = size.height
            val y = size.height / 2f
            drawLine(
                color = trackColor,
                start = Offset(strokeWidth / 2f, y),
                end = Offset(size.width - strokeWidth / 2f, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            val filledWidth = (size.width - strokeWidth) * progress.coerceIn(0f, 1f)
            if (filledWidth > 0f) {
                drawLine(
                    color = barColor,
                    start = Offset(strokeWidth / 2f, y),
                    end = Offset(strokeWidth / 2f + filledWidth, y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun TdeeStatRow(currentTdee: Double) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Estimated TDEE", style = MaterialTheme.typography.bodyMedium)
            InfoTooltipIcon(
                title = "Estimated TDEE",
                text = "Total Daily Energy Expenditure - roughly how many calories you'd need to eat to " +
                    "hold your weight steady right now. It starts as a formula-based guess, then adapts to " +
                    "match your own logged weight and eating over time - including moving a lot if your " +
                    "quick-pick choices (\"a bit less\", \"much more\", etc.) don't quite match what the " +
                    "scale shows, since your real results are what it trusts most.",
            )
        }
        Text("${currentTdee.roundToInt()} kcal/day", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WeightTrendStatRow(deltaKg: Double, unit: WeightUnit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Since you started", style = MaterialTheme.typography.bodyMedium)
        if (abs(deltaKg) < 0.05) {
            Text("No change", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    if (deltaKg < 0) Icons.AutoMirrored.Outlined.TrendingDown else Icons.AutoMirrored.Outlined.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(Phrasing.formatWeight(abs(deltaKg), unit), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CheckInCard(viewModel: TodayViewModel, profile: UserProfile?) {
    // Asks for attention with a border when the selected day still needs something, instead of
    // jumping to the top of the screen - a fixed layout that calmly points at what's missing
    // beats one that physically rearranges itself.
    val borderModifier = if (!viewModel.isDayComplete) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
    } else {
        Modifier
    }
    Card(modifier = Modifier.fillMaxWidth().then(borderModifier)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DayPager(viewModel)

            if (viewModel.isDayComplete && viewModel.selectedDate == viewModel.actualToday) {
                AllSetBanner()
            }

            if (viewModel.isBlankDay) {
                // Neither field has been saved yet - one combined form with a single Save button,
                // rather than two separate per-field forms that would otherwise both show at once.
                BlankDayForm(viewModel, profile)
            } else {
                WeightSection(viewModel, profile)
                MealSection(viewModel)
            }

            if (!viewModel.isBlankDay) {
                DeleteDayButton(viewModel)
            }
        }
    }
}

@Composable
private fun AllSetBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(7.dp))
        Text(
            "All set for today",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
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

/** [weightDate] and [today] decide the wording, the same way [MealSectionHeading] labels the meal
 * section - showing each section's own date explicitly (rather than relying only on the page-level
 * "Today"/date header above both of them) is what keeps it unambiguous when the two sections cover
 * different days, which they can whenever [com.measured.app.domain.MealTimingMode] offsets the
 * meal section by a day. */
@Composable
private fun WeightSectionHeading(weightDate: LocalDate, today: LocalDate) {
    val weightDateLabel = dayLabel(weightDate, today)
    val heading = when (weightDateLabel) {
        "Today", "Yesterday" -> "$weightDateLabel's weight"
        else -> "Weight from $weightDateLabel"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(heading, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        InfoTooltipIcon(
            title = "Getting a reliable reading",
            text = "Weight can swing by a kilogram or two across a single day from water, food, and " +
                "salt - what matters is the trend over time, not any one number. For readings that are " +
                "actually comparable day to day, weigh in around the same time, ideally right after " +
                "waking and before eating or drinking.",
        )
    }
}

/** [mealDate] and [today] decide the wording - "Today's meals" or "Yesterday's meals" depending
 * on the Settings choice (see [com.measured.app.domain.MealTimingMode]) when checking in
 * normally, or the actual date when browsing further back in history, so it's never ambiguous
 * which day's eating is being asked about. */
@Composable
private fun MealSectionHeading(mealDate: LocalDate, today: LocalDate) {
    val mealDateLabel = dayLabel(mealDate, today)
    val heading = when (mealDateLabel) {
        "Today", "Yesterday" -> "$mealDateLabel's meals"
        else -> "Meals from $mealDateLabel"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(heading, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        if (mealDateLabel == "Yesterday") {
            InfoTooltipIcon(
                title = "Why yesterday?",
                text = "This asks about yesterday's eating rather than today's, since by the time you're " +
                    "checking in it's already finished and you know the real number - no guessing how the " +
                    "rest of today will go. Pick how it compared to your own usual day, or tap \"Enter exact " +
                    "calories\" if you know it exactly. You can switch this to ask about today instead under " +
                    "Settings, if you weigh in in the evening after you've already finished eating.",
            )
        } else {
            InfoTooltipIcon(
                title = "Logging meals",
                text = "Pick how much you ate compared to your own usual day, or tap \"Enter exact calories\" " +
                    "if you know the number.",
            )
        }
    }
}

@Composable
private fun BlankDayForm(viewModel: TodayViewModel, profile: UserProfile?) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WeightSectionHeading(viewModel.selectedDate, viewModel.actualToday)
            WeightInputField(viewModel, profile)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MealSectionHeading(viewModel.mealDate, viewModel.actualToday)
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
        WeightSectionHeading(viewModel.selectedDate, viewModel.actualToday)

        if (viewModel.isEditingWeight) {
            WeightInputField(viewModel, profile)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::saveWeight) { Text("Save") }
                if (viewModel.weightLog?.weightKg != null) {
                    TextButton(onClick = viewModel::onCancelEditingWeight) { Text("Cancel") }
                }
            }
        } else {
            val log = viewModel.weightLog
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
        MealSectionHeading(viewModel.mealDate, viewModel.actualToday)

        if (viewModel.isEditingMeal) {
            MealForm(viewModel)
        } else {
            val log = viewModel.mealLog
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
        if (viewModel.mealLog?.calories != null) {
            TextButton(onClick = viewModel::onCancelEditingMeal) { Text("Cancel") }
        }
    }
}

@Composable
private fun MealInputFields(viewModel: TodayViewModel) {
    val state = viewModel.mealState

    if (!state.useExact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "vs. your usual",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoTooltipIcon(
                title = "vs. your usual",
                text = "These compare that day to your own typical eating, not a fixed calorie amount - the " +
                    "app learns what's usual for you from what you've logged so far. Pick whichever best " +
                    "matches how much you ate compared to a normal day for you. If you know the exact " +
                    "number, \"Enter exact calories\" gives a more precise estimate than any of these.",
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IntakeLevel.entries.forEach { level ->
                FilterChip(
                    selected = state.selectedIntake == level,
                    onClick = { viewModel.onIntakeSelected(level) },
                    label = { Text(level.label) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = themedFilterChipColors(),
                )
            }
        }
        TextButton(onClick = viewModel::onToggleExact) {
            Text("Enter exact calories")
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
            Text("Use quick pick")
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
            text = {
                Text(
                    "This removes the weight logged for ${dayLabel(viewModel.selectedDate, viewModel.actualToday)} and the " +
                        "meal info logged for ${dayLabel(viewModel.mealDate, viewModel.actualToday)}. This can't be undone.",
                )
            },
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
        Icon(
            Icons.Outlined.Info,
            contentDescription = "What does this mean?",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
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
