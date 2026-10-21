package com.measured.app.ui.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.measured.app.domain.DailyLog
import com.measured.app.domain.DataFillRateResult
import com.measured.app.domain.HealthBenefit
import com.measured.app.domain.Milestone
import com.measured.app.domain.MilestoneCategory
import com.measured.app.domain.Phrasing
import com.measured.app.domain.UserProfile
import com.measured.app.domain.WeekSummary
import com.measured.app.ui.AppViewModelProvider
import com.measured.app.ui.common.IntakeChip
import com.measured.app.ui.theme.themedFilterChipColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d")
private val dayOfMonthFormatter = DateTimeFormatter.ofPattern("EEE, d")
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

// Same green used for the Today screen's "high confidence" accuracy indicator - a neutral,
// theme-independent "done" signal, not a judgment on any of the day's actual numbers.
private val DAY_COMPLETE_COLOR = Color(0xFF43A047)

private enum class JourneyViewMode(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly trend"),
}

private enum class ChartStyle { LINE, BAR }

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
    val isSupporter by viewModel.isSupporter.collectAsState()
    var viewMode by remember { mutableStateOf(JourneyViewMode.DAILY) }
    var chartStyle by remember { mutableStateOf(ChartStyle.LINE) }

    val hasAnyData = logs.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        if (!hasAnyData) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No entries yet - check in from the Status tab to start your journey.",
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

            item {
                WeightTrendCard(
                    logs = logs,
                    profile = profile,
                    chartStyle = chartStyle,
                    onChartStyleChange = { chartStyle = it },
                )
            }

            item {
                if (isSupporter) {
                    CalorieTrendCard(
                        logs = logs,
                        chartStyle = chartStyle,
                        onChartStyleChange = { chartStyle = it },
                    )
                } else {
                    LockedChartTeaser()
                }
            }

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
                            colors = themedFilterChipColors(),
                        )
                    }
                }
            }

            when (viewMode) {
                JourneyViewMode.DAILY -> {
                    // Grouped by month with one compact row per day, rather than a full card per
                    // day loaded one week at a time - that old pattern meant someone with a year
                    // of history had to tap "Previous week" dozens of times just to scroll back,
                    // and still ended up with hundreds of bulky cards once they did. The LazyColumn
                    // this all lives in already only composes what's on screen, so there's no need
                    // to hide older entries behind a button at all - just group them for scanning.
                    val monthGroups = logs.sortedByDescending { it.date }.groupBy { YearMonth.from(it.date) }

                    monthGroups.forEach { (month, daysInMonth) ->
                        item(key = "month-$month") {
                            Text(
                                month.format(monthFormatter),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        item(key = "month-card-$month") {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    daysInMonth.forEachIndexed { index, log ->
                                        DayRow(
                                            log = log,
                                            profile = profile,
                                            onEdit = { onEditDay(log.date) },
                                            onDelete = { viewModel.deleteLog(log.date) },
                                        )
                                        if (index != daysInMonth.lastIndex) {
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                        }
                                    }
                                }
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
                "Keep logging to unlock more.",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Each milestone gets its own achievement card (icon badge + title + status) rather than a small
// tappable chip - milestones are meant to feel like a reward for real effort, so they get room to
// breathe instead of being squeezed into a single-line label. Still just one flat "unlocked" set
// plus one flat "up next" set, not four separate per-category sections - milestone titles already
// say what they're about ("7-day streak", "Halfway there"), so a category label per card was mostly
// repeating information the title already carried.
@Composable
private fun MilestonesSection(milestones: List<Milestone>) {
    if (milestones.isEmpty()) return
    val achieved = milestones.filter { it.achieved }
    val upNext = milestones.filter { !it.achieved }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (achieved.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Unlocked", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                AchievementGrid(achieved)
            }
        } else {
            Text(
                "Keep logging to earn your first milestone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (upNext.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Up next",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AchievementGrid(upNext)
            }
        }
    }
}

@Composable
private fun AchievementGrid(milestones: List<Milestone>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        milestones.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { milestone -> AchievementCard(milestone, modifier = Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AchievementCard(milestone: Milestone, modifier: Modifier = Modifier) {
    val badgeColor = if (milestone.achieved) categoryContainerColor(milestone.category) else MaterialTheme.colorScheme.surfaceVariant
    val badgeContentColor = if (milestone.achieved) categoryContentColor(milestone.category) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (milestone.achieved) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    categoryIcon(milestone.category),
                    contentDescription = null,
                    tint = badgeContentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                milestone.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                if (milestone.achieved) "Unlocked" else (milestone.hint ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = if (milestone.achieved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun categoryIcon(category: MilestoneCategory): ImageVector = when (category) {
    MilestoneCategory.TIME -> Icons.Outlined.Schedule
    MilestoneCategory.STREAK -> Icons.Outlined.LocalFireDepartment
    MilestoneCategory.PROGRESS -> Icons.Outlined.Flag
    MilestoneCategory.AMOUNT -> Icons.Outlined.MonitorWeight
}

@Composable
private fun categoryContainerColor(category: MilestoneCategory): Color = when (category) {
    MilestoneCategory.TIME -> MaterialTheme.colorScheme.primaryContainer
    MilestoneCategory.STREAK -> MaterialTheme.colorScheme.tertiaryContainer
    MilestoneCategory.PROGRESS -> MaterialTheme.colorScheme.secondaryContainer
    MilestoneCategory.AMOUNT -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun categoryContentColor(category: MilestoneCategory): Color = when (category) {
    MilestoneCategory.TIME -> MaterialTheme.colorScheme.onPrimaryContainer
    MilestoneCategory.STREAK -> MaterialTheme.colorScheme.onTertiaryContainer
    MilestoneCategory.PROGRESS -> MaterialTheme.colorScheme.onSecondaryContainer
    MilestoneCategory.AMOUNT -> MaterialTheme.colorScheme.onPrimaryContainer
}

@Composable
private fun DataFillRateCard(fillRate: DataFillRateResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Check-in rate", style = MaterialTheme.typography.bodyMedium)
            Text(
                "${fillRate.percent}% (${fillRate.loggedDays}/${fillRate.totalDays})",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// Deliberately calmer than the Milestones page's achievement cards: this is citing real research
// findings, not celebrating a personal win, so it reads like a short reference list rather than a
// row of unlockable rewards - a plain row per finding that expands in place to show the full
// explanation and citation, instead of badges, colors, or a popup dialog.
@Composable
private fun HealthBenefitsSection(benefits: List<HealthBenefit>) {
    if (benefits.isEmpty()) return
    val achieved = benefits.filter { it.achieved }
    val next = benefits.firstOrNull { !it.achieved }
    var expandedTitle by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Population averages from published research, not a guarantee for you personally - " +
                "always follow your own doctor's guidance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (achieved.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                achieved.forEach { fact ->
                    ResearchFactRow(
                        fact = fact,
                        expanded = expandedTitle == fact.title,
                        onToggle = { expandedTitle = if (expandedTitle == fact.title) null else fact.title },
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
}

@Composable
private fun ResearchFactRow(fact: HealthBenefit, expanded: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    fact.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Text(fact.body, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Source: ${fact.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeightTrendCard(
    logs: List<DailyLog>,
    profile: UserProfile?,
    chartStyle: ChartStyle,
    onChartStyleChange: (ChartStyle) -> Unit,
) {
    val points = logs.filter { it.weightKg != null }.sortedBy { it.date }
    val lineColor = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.tertiary

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Weight trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (points.size >= 2) ChartStyleToggle(chartStyle, onChartStyleChange)
            }

            if (points.size < 2 || profile == null) {
                Text(
                    "A few more days of data needed.",
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

            val weightChartDescription = "Weight trend chart, from ${oneDecimal(weights.first())} to " +
                "${oneDecimal(weights.last())} ${unit.name.lowercase()}, goal ${oneDecimal(goalWeight)} ${unit.name.lowercase()}"
            Box(modifier = Modifier.fillMaxWidth()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(vertical = 4.dp)
                        .semantics { contentDescription = weightChartDescription },
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

                    if (chartStyle == ChartStyle.BAR) {
                        val barWidth = (chartWidth / weights.size) * 0.6f
                        weights.forEachIndexed { i, weight ->
                            val x = xFor(i)
                            drawRect(
                                color = lineColor,
                                topLeft = Offset(x - barWidth / 2f, yFor(weight)),
                                size = Size(barWidth, chartHeight - yFor(weight)),
                            )
                        }
                    } else {
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
                }
                AxisLabel("${oneDecimal(maxWeight)} ${unit.name.lowercase()}", Alignment.TopStart)
                AxisLabel("${oneDecimal(minWeight)} ${unit.name.lowercase()}", Alignment.BottomStart)
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

// A supporter-only perk - the weight trend above is the one chart everyone needs, this is a nice
// extra for anyone curious about their calorie history specifically.
@Composable
private fun CalorieTrendCard(
    logs: List<DailyLog>,
    chartStyle: ChartStyle,
    onChartStyleChange: (ChartStyle) -> Unit,
) {
    val points = logs.filter { it.calories != null }.sortedBy { it.date }
    val lineColor = MaterialTheme.colorScheme.secondary

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Calorie trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (points.size >= 2) ChartStyleToggle(chartStyle, onChartStyleChange)
            }

            if (points.size < 2) {
                Text(
                    "A few more days of data needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val calories = points.map { it.calories!! }
            val minCalories = calories.min()
            val maxCalories = calories.max()
            val range = (maxCalories - minCalories).let { if (it < 1.0) 1.0 else it }

            val calorieChartDescription =
                "Calorie trend chart, from ${calories.first().toInt()} to ${calories.last().toInt()} calories"
            Box(modifier = Modifier.fillMaxWidth()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(vertical = 4.dp)
                        .semantics { contentDescription = calorieChartDescription },
                ) {
                    val chartHeight = size.height
                    val chartWidth = size.width
                    fun xFor(index: Int): Float =
                        if (points.size == 1) chartWidth / 2f else chartWidth * index / (points.size - 1).toFloat()
                    fun yFor(value: Double): Float =
                        (chartHeight - ((value - minCalories) / range * chartHeight)).toFloat()

                    if (chartStyle == ChartStyle.BAR) {
                        val barWidth = (chartWidth / calories.size) * 0.6f
                        calories.forEachIndexed { i, value ->
                            val x = xFor(i)
                            drawRect(
                                color = lineColor,
                                topLeft = Offset(x - barWidth / 2f, yFor(value)),
                                size = Size(barWidth, chartHeight - yFor(value)),
                            )
                        }
                    } else {
                        for (i in 0 until calories.size - 1) {
                            drawLine(
                                color = lineColor,
                                start = Offset(xFor(i), yFor(calories[i])),
                                end = Offset(xFor(i + 1), yFor(calories[i + 1])),
                                strokeWidth = 4f,
                            )
                        }
                        calories.forEachIndexed { i, value ->
                            drawCircle(color = lineColor, radius = 5f, center = Offset(xFor(i), yFor(value)))
                        }
                    }
                }
                AxisLabel("${maxCalories.toInt()} kcal", Alignment.TopStart)
                AxisLabel("${minCalories.toInt()} kcal", Alignment.BottomStart)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(points.first().date.format(shortDateFormatter), style = MaterialTheme.typography.bodySmall)
                Text(points.last().date.format(shortDateFormatter), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A compact line/bar switch shared by both trend cards - picking one style applies to both
 * charts at once, since there's no reason to view them differently. */
@Composable
private fun ChartStyleToggle(selected: ChartStyle, onChange: (ChartStyle) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(ChartStyle.LINE) }) {
            Icon(
                Icons.AutoMirrored.Outlined.ShowChart,
                contentDescription = "Line chart",
                tint = if (selected == ChartStyle.LINE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onChange(ChartStyle.BAR) }) {
            Icon(
                Icons.Outlined.BarChart,
                contentDescription = "Bar chart",
                tint = if (selected == ChartStyle.BAR) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A small value label pinned to a corner of a chart's Box, with a translucent backing so it
 * stays readable regardless of what the chart itself drew underneath it - the minimal way to
 * show what range a chart spans without adding gridlines or a full axis. */
@Composable
private fun BoxScope.AxisLabel(text: String, alignment: Alignment) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .align(alignment)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun LockedChartTeaser() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column {
                Text("Calorie trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "An extra chart for supporters - see Settings to learn more.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// One compact line per day instead of a full card - these are meant to sit stacked by the dozen
// inside a single month card, so a date, the day's numbers, and a tap target for edit/delete is
// all there's room (and need) for. The date already omits the month/year, since those are said
// once in the month header above this row rather than repeated on every single line.
@Composable
private fun DayRow(log: DailyLog, profile: UserProfile?, onEdit: () -> Unit, onDelete: () -> Unit) {
    val unit = profile?.weightUnit
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // A fully logged day (both weight and calories) earns a small green dot, the same
                // "done" signal used for the accuracy indicator elsewhere - a quiet confirmation
                // while scanning a month at a glance, not a judgment on any of the numbers in it.
                if (log.weightKg != null && log.calories != null) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(DAY_COMPLETE_COLOR),
                    )
                }
                Text(log.date.format(dayOfMonthFormatter), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (log.weightKg != null && unit != null) {
                    Text(Phrasing.formatWeight(log.weightKg, unit), style = MaterialTheme.typography.bodySmall)
                }
                IntakeChip(log, style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = { showDeleteConfirm = true }) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete this day",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
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
