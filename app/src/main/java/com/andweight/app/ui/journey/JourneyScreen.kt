package com.andweight.app.ui.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andweight.app.domain.DailyLog
import com.andweight.app.domain.DataFillRateResult
import com.andweight.app.domain.HealthBenefit
import com.andweight.app.domain.Milestone
import com.andweight.app.domain.Phrasing
import com.andweight.app.domain.UserProfile
import com.andweight.app.domain.WeekGrouping
import com.andweight.app.domain.WeekSummary
import com.andweight.app.ui.AppViewModelProvider
import com.andweight.app.ui.common.IntakeChip
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d")

private enum class JourneyViewMode(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly trend"),
}

@Composable
fun JourneyScreen(
    contentPadding: PaddingValues,
    onEditDay: (LocalDate) -> Unit,
    onShowMilestones: () -> Unit,
    onShowResearch: () -> Unit,
    viewModel: JourneyViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val profile by viewModel.profile.collectAsState()
    val tdeeState by viewModel.tdeeState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val milestones by viewModel.milestones.collectAsState()
    val healthBenefits: List<HealthBenefit> by viewModel.healthBenefits.collectAsState()
    val dataFillRate by viewModel.dataFillRate.collectAsState()
    var viewMode by remember { mutableStateOf(JourneyViewMode.DAILY) }
    var visibleWeeks by remember { mutableStateOf(1) }

    val hasAnyData = logs.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        if (!hasAnyData) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No entries yet - check in from the Today tab to start your journey.",
                    modifier = Modifier.padding(32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (milestones.isNotEmpty()) {
                item { MilestonesEntryCard(milestones, onClick = onShowMilestones) }
            }

            if (healthBenefits.isNotEmpty()) {
                item { ResearchEntryCard(healthBenefits, onClick = onShowResearch) }
            }

            dataFillRate?.let { fillRate -> item { DataFillRateCard(fillRate) } }

            item { WeightTrendCard(logs, profile) }

            item { Text("History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    JourneyViewMode.entries.forEach { mode ->
                        FilterChip(
                            selected = viewMode == mode,
                            onClick = { viewMode = mode },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }

            when (viewMode) {
                JourneyViewMode.DAILY -> {
                    val firstDayOfWeek = profile?.weightUnit?.firstDayOfWeek ?: DayOfWeek.MONDAY
                    val currentWeekStart = WeekGrouping.weekStart(viewModel.actualToday, firstDayOfWeek)
                    val oldestVisibleWeekStart = currentWeekStart.minusWeeks((visibleWeeks - 1).toLong())
                    val visibleLogs = logs
                        .filter { !it.date.isBefore(oldestVisibleWeekStart) }
                        .sortedByDescending { it.date }

                    items(visibleLogs) { log ->
                        DailyCard(log, profile, onEdit = { onEditDay(log.date) }, onDelete = { viewModel.deleteLog(log.date) })
                    }

                    val earliestLogDate = logs.minOfOrNull { it.date }
                    val earliestWeekStart = earliestLogDate?.let { WeekGrouping.weekStart(it, firstDayOfWeek) }
                    if (visibleLogs.isEmpty()) {
                        item {
                            Text(
                                "No entries this week.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (earliestWeekStart != null && oldestVisibleWeekStart.isAfter(earliestWeekStart)) {
                        item {
                            TextButton(
                                onClick = { visibleWeeks += 1 },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Previous week")
                            }
                        }
                    }
                }
                JourneyViewMode.WEEKLY -> {
                    val weeks = tdeeState?.weeklyHistory.orEmpty().reversed()
                    items(weeks) { week -> WeekCard(week, profile) }
                }
            }
        }
    }
}

// Compact teasers on the main Journey page - Milestones and the research section both moved to
// their own full screens (see MilestonesScreen/ResearchScreen below) rather than living inline,
// since each has enough content (achieved items, an "up next" line, tappable detail) to compete
// for attention with the rest of the page.
@Composable
private fun MilestonesEntryCard(milestones: List<Milestone>, onClick: () -> Unit) {
    val achievedCount = milestones.count { it.achieved }
    JourneyEntryCard(
        icon = Icons.Outlined.EmojiEvents,
        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
        title = "Milestones",
        subtitle = if (achievedCount == 0) "None unlocked yet" else "$achievedCount unlocked",
        onClick = onClick,
    )
}

@Composable
private fun ResearchEntryCard(benefits: List<HealthBenefit>, onClick: () -> Unit) {
    val achievedCount = benefits.count { it.achieved }
    JourneyEntryCard(
        icon = Icons.Outlined.FavoriteBorder,
        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
        title = "What the research says",
        subtitle = if (achievedCount == 0) "Nothing unlocked yet" else "$achievedCount unlocked",
        onClick = onClick,
    )
}

@Composable
private fun JourneyEntryCard(
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestonesScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: JourneyViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val milestones by viewModel.milestones.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Milestones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { topBarPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = topBarPadding.calculateTopPadding() + 12.dp,
                    bottom = contentPadding.calculateBottomPadding() + 20.dp,
                ),
        ) {
            MilestonesSection(milestones)
            Spacer(Modifier.height(16.dp))
            MilestonesMascot()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: JourneyViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val benefits by viewModel.healthBenefits.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What the research says") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { topBarPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = topBarPadding.calculateTopPadding() + 12.dp,
                    bottom = contentPadding.calculateBottomPadding() + 20.dp,
                ),
        ) {
            HealthBenefitsSection(benefits)
        }
    }
}

@Composable
private fun MilestonesMascot() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val faceColor = MaterialTheme.colorScheme.primaryContainer
            val featureColor = MaterialTheme.colorScheme.onPrimaryContainer
            val badgeColor = MaterialTheme.colorScheme.tertiary
            val badgeMarkColor = MaterialTheme.colorScheme.onTertiary
            val shadowColor = MaterialTheme.colorScheme.surfaceVariant

            Canvas(modifier = Modifier.size(104.dp, 88.dp)) {
                val w = size.width
                val h = size.height

                drawOval(
                    color = shadowColor,
                    topLeft = Offset(w * 0.18f, h * 0.86f),
                    size = Size(w * 0.64f, h * 0.08f),
                )

                val faceRadius = w * 0.36f
                val faceCenter = Offset(w * 0.47f, h * 0.48f)
                drawCircle(color = faceColor, radius = faceRadius, center = faceCenter)

                val eyeRadius = w * 0.032f
                drawCircle(
                    color = featureColor,
                    radius = eyeRadius,
                    center = Offset(faceCenter.x - faceRadius * 0.34f, faceCenter.y - faceRadius * 0.08f),
                )
                drawCircle(
                    color = featureColor,
                    radius = eyeRadius,
                    center = Offset(faceCenter.x + faceRadius * 0.34f, faceCenter.y - faceRadius * 0.08f),
                )

                drawArc(
                    color = featureColor,
                    startAngle = 25f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(faceCenter.x - faceRadius * 0.4f, faceCenter.y - faceRadius * 0.05f),
                    size = Size(faceRadius * 0.8f, faceRadius * 0.7f),
                    style = Stroke(width = w * 0.022f, cap = StrokeCap.Round),
                )

                val badgeRadius = w * 0.15f
                val badgeCenter = Offset(faceCenter.x + faceRadius * 0.82f, faceCenter.y - faceRadius * 0.78f)
                drawCircle(color = badgeColor, radius = badgeRadius, center = badgeCenter)
                val markHalf = badgeRadius * 0.4f
                drawLine(
                    color = badgeMarkColor,
                    start = Offset(badgeCenter.x - markHalf, badgeCenter.y),
                    end = Offset(badgeCenter.x - markHalf * 0.2f, badgeCenter.y + markHalf * 0.7f),
                    strokeWidth = w * 0.016f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = badgeMarkColor,
                    start = Offset(badgeCenter.x - markHalf * 0.2f, badgeCenter.y + markHalf * 0.7f),
                    end = Offset(badgeCenter.x + markHalf, badgeCenter.y - markHalf * 0.6f),
                    strokeWidth = w * 0.016f,
                    cap = StrokeCap.Round,
                )
            }

            Text("More milestones on the way", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Keep logging and we'll keep finding new things worth celebrating.",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// One flat set of achieved milestones plus one flat set of what's coming up next, rather than four
// separate per-category cards each with their own achieved/next split - milestone titles already
// say what they're about ("7-day streak", "Halfway there"), so a category label per card was mostly
// repeating information the title already carried.
@Composable
private fun MilestonesSection(milestones: List<Milestone>) {
    if (milestones.isEmpty()) return
    val achieved = milestones.filter { it.achieved }
    val upNext = milestones.filter { !it.achieved }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (achieved.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(achieved) { milestone ->
                    AssistChip(
                        onClick = {},
                        label = { Text(milestone.title) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                        border = null,
                    )
                }
            }
        } else {
            Text(
                "Keep logging to earn your first milestone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (upNext.isNotEmpty()) {
            Text("Up next", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(upNext) { milestone ->
                    AssistChip(
                        onClick = {},
                        label = { Text(milestone.hint?.let { hint -> "${milestone.title} - $hint" } ?: milestone.title) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        border = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun DataFillRateCard(fillRate: DataFillRateResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Check-in rate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "You logged something on ${fillRate.percent}% of the last ${fillRate.totalDays} " +
                    "day${if (fillRate.totalDays == 1) "" else "s"} (${fillRate.loggedDays}/${fillRate.totalDays}).",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// Matches the Milestones section's chip layout, for the same reason: unlocked findings as short
// tappable chips (title only, full text on tap) instead of a full card per finding stacked
// vertically, so unlocking several at once doesn't turn this into a wall of paragraphs.
@Composable
private fun HealthBenefitsSection(benefits: List<HealthBenefit>) {
    if (benefits.isEmpty()) return
    val achieved = benefits.filter { it.achieved }
    val next = benefits.firstOrNull { !it.achieved }
    var expandedFact by remember { mutableStateOf<HealthBenefit?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Population averages from published research, not a guarantee for you personally - " +
                "always follow your own doctor's guidance. Tap a finding to read more.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (achieved.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(achieved) { fact ->
                    AssistChip(
                        onClick = { expandedFact = fact },
                        label = { Text(fact.title) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        border = null,
                    )
                }
            }
        } else {
            Text(
                "Nothing unlocked yet - keep going!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        next?.let { fact ->
            Text(
                "Next: ${fact.title}${fact.hint?.let { " - $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    expandedFact?.let { fact ->
        AlertDialog(
            onDismissRequest = { expandedFact = null },
            title = { Text(fact.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(fact.body, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Source: ${fact.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { expandedFact = null }) { Text("Got it") }
            },
        )
    }
}

@Composable
private fun WeightTrendCard(logs: List<DailyLog>, profile: UserProfile?) {
    val points = logs.filter { it.weightKg != null }.sortedBy { it.date }
    val lineColor = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.tertiary

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weight trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            if (points.size < 2 || profile == null) {
                Text(
                    "Log your weight on a few more days to see your trend here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val unit = profile.weightUnit
            val weights = points.map { unit.fromKg(it.weightKg!!) }
            val goalWeight = unit.fromKg(profile.goalWeightKg)
            val minWeight = minOf(weights.min(), goalWeight)
            val maxWeight = maxOf(weights.max(), goalWeight)
            val range = (maxWeight - minWeight).let { if (it < 0.01) 1.0 else it }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(vertical = 4.dp),
            ) {
                val chartHeight = size.height
                val chartWidth = size.width
                fun xFor(index: Int): Float =
                    if (points.size == 1) chartWidth / 2f else chartWidth * index / (points.size - 1).toFloat()
                fun yFor(weight: Double): Float =
                    (chartHeight - ((weight - minWeight) / range * chartHeight)).toFloat()

                val goalY = yFor(goalWeight)
                drawLine(
                    color = goalColor,
                    start = Offset(0f, goalY),
                    end = Offset(chartWidth, goalY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                )

                for (i in 0 until weights.size - 1) {
                    drawLine(
                        color = lineColor,
                        start = Offset(xFor(i), yFor(weights[i])),
                        end = Offset(xFor(i + 1), yFor(weights[i + 1])),
                        strokeWidth = 4f,
                    )
                }
                weights.forEachIndexed { i, weight ->
                    drawCircle(color = lineColor, radius = 5f, center = Offset(xFor(i), yFor(weight)))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(points.first().date.format(shortDateFormatter), style = MaterialTheme.typography.bodySmall)
                Text(points.last().date.format(shortDateFormatter), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "- - - goal (${oneDecimal(goalWeight)} ${unit.name.lowercase()})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun DailyCard(log: DailyLog, profile: UserProfile?, onEdit: () -> Unit, onDelete: () -> Unit) {
    val unit = profile?.weightUnit
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(log.date.format(dateFormatter), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (log.weightKg != null && unit != null) {
                    Text("Weight: ${Phrasing.formatWeight(log.weightKg, unit)}", style = MaterialTheme.typography.bodySmall)
                }
                IntakeChip(log, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit this day")
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete this day", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this day?") },
            text = { Text("This removes the weight and meal info logged for ${log.date.format(dateFormatter)}. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
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
private fun WeekCard(week: WeekSummary, profile: UserProfile?) {
    val unit = profile?.weightUnit
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${week.startDate.format(dateFormatter)} - ${week.endDate.format(dateFormatter)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (!week.hasWeightData && !week.hasCalorieData) {
                Text("No entries this week", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Avg weight", style = MaterialTheme.typography.bodySmall)
                    Text(
                        week.avgWeightKg?.let { kg ->
                            val value = unit?.fromKg(kg) ?: kg
                            "${oneDecimal(value)} ${unit?.name?.lowercase() ?: "kg"}"
                        } ?: "-",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Avg calories", style = MaterialTheme.typography.bodySmall)
                    Text(week.avgCalories?.roundToInt()?.toString() ?: "-", style = MaterialTheme.typography.bodySmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Estimated TDEE", style = MaterialTheme.typography.bodySmall)
                    Text(week.tdee?.roundToInt()?.toString() ?: "not enough data yet", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun oneDecimal(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
