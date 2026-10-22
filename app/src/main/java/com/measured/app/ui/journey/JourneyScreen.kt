package com.measured.app.ui.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AreaChart
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.measured.app.domain.DailyLog
import com.measured.app.domain.DataFillRateResult
import com.measured.app.domain.HealthBenefit
import com.measured.app.domain.Milestone
import com.measured.app.domain.MilestoneCategory
import com.measured.app.domain.UserProfile
import com.measured.app.ui.AppViewModelProvider
import com.measured.app.ui.common.InfoTooltipIcon
import com.measured.app.ui.common.SupporterOnlyDialog
import com.measured.app.ui.theme.themedFilterChipColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d")

// Same green used for the Today screen's "high confidence" accuracy indicator - a neutral,
// theme-independent "done" signal, not a judgment on any of the day's actual numbers. Shared
// with JourneyHistoryScreen's DayRow.
val DAY_COMPLETE_COLOR = Color(0xFF43A047)

private enum class ChartStyle(val label: String) { LINE("Line"), BAR("Bar"), AREA("Area") }

// Once there's a lot of history, defaulting to the full span means early, often noisier data
// stretches the chart's auto min/max and flattens how recent changes look - a plain "zoom in"
// toggle beats trying to guess the right window automatically. Week/Month/Year mirror the
// ranges most other health-tracking apps offer - actual calendar periods (this October, this
// week), not a rolling N-day window, with arrows to page to an earlier or later one.
private enum class ChartRange(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year"),
    ALL("All"),
}

/** The [start, end] (inclusive) of the calendar period [anchor] falls in, or null for ALL, which
 * has no period to navigate - always the complete history. */
private fun periodBounds(range: ChartRange, anchor: LocalDate, firstDayOfWeek: DayOfWeek): Pair<LocalDate, LocalDate>? =
    when (range) {
        ChartRange.WEEK -> {
            val start = weekStart(anchor, firstDayOfWeek)
            start to start.plusDays(6)
        }
        ChartRange.MONTH -> {
            val start = anchor.withDayOfMonth(1)
            start to start.plusMonths(1).minusDays(1)
        }
        ChartRange.YEAR -> {
            val start = anchor.withDayOfYear(1)
            start to start.plusYears(1).minusDays(1)
        }
        ChartRange.ALL -> null
    }

private fun shiftAnchor(range: ChartRange, anchor: LocalDate, periods: Long): LocalDate = when (range) {
    ChartRange.WEEK -> anchor.plusWeeks(periods)
    ChartRange.MONTH -> anchor.plusMonths(periods)
    ChartRange.YEAR -> anchor.plusYears(periods)
    ChartRange.ALL -> anchor
}

private fun periodTitle(range: ChartRange, start: LocalDate, end: LocalDate): String = when (range) {
    ChartRange.WEEK -> "${start.format(shortDateFormatter)} - ${end.format(shortDateFormatter)}"
    ChartRange.MONTH -> start.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    ChartRange.YEAR -> start.format(DateTimeFormatter.ofPattern("yyyy"))
    ChartRange.ALL -> "All time"
}

private fun weekStart(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate {
    val diff = (date.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    return date.minusDays(diff.toLong())
}

/** Month view averages its days into one point per calendar week, and Year view into one point
 * per calendar month - Week and All stay daily. A point's date is its sub-period's start, so
 * aggregated points still sort and plot left-to-right the same way daily ones do. Points with no
 * logged value in a sub-period simply don't produce a point, same as a day with nothing logged
 * today - this is for display, so it only ever sees sub-periods that have at least one entry. */
private fun aggregateForRange(
    points: List<Pair<LocalDate, Double>>,
    range: ChartRange,
    firstDayOfWeek: DayOfWeek,
): List<Pair<LocalDate, Double>> = when (range) {
    ChartRange.MONTH -> points.groupBy { (date, _) -> weekStart(date, firstDayOfWeek) }
        .toSortedMap()
        .map { (start, group) -> start to group.map { it.second }.average() }
    ChartRange.YEAR -> points.groupBy { (date, _) -> date.withDayOfMonth(1) }
        .toSortedMap()
        .map { (start, group) -> start to group.map { it.second }.average() }
    ChartRange.WEEK, ChartRange.ALL -> points
}

/** Per-point date labels for the chart's tap-to-reveal tooltip - "Oct 13" reads fine for a daily
 * point, but an aggregated weekly point needs "Week of Oct 13" to say what it actually represents,
 * and a monthly one just needs the month's name. */
private fun dateLabelFormatter(range: ChartRange): (LocalDate) -> String = when (range) {
    ChartRange.MONTH -> { date -> "Week of ${date.format(shortDateFormatter)}" }
    ChartRange.YEAR -> { date -> date.format(DateTimeFormatter.ofPattern("MMMM")) }
    ChartRange.WEEK, ChartRange.ALL -> { date -> date.format(shortDateFormatter) }
}

// Below this, a Week/Month/Year breakdown would mostly just show "All" three different ways -
// the range picker only earns its place once there's enough history to actually zoom into.
private const val CHART_RANGE_PICKER_THRESHOLD_DAYS = 14L

@Composable
fun JourneyScreen(
    contentPadding: PaddingValues,
    onShowMilestones: () -> Unit,
    onShowResearch: () -> Unit,
    onShowHistory: () -> Unit,
    viewModel: JourneyViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val profile by viewModel.profile.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val milestones by viewModel.milestones.collectAsState()
    val healthBenefits: List<HealthBenefit> by viewModel.healthBenefits.collectAsState()
    val dataFillRate by viewModel.dataFillRate.collectAsState()
    val isSupporter by viewModel.isSupporter.collectAsState()
    var chartStyle by remember { mutableStateOf(ChartStyle.LINE) }
    var chartRange by remember { mutableStateOf(ChartRange.MONTH) }
    // Any date within the period currently shown - jumps back to today whenever the range type
    // itself changes, so switching from "last March" in Month view to Week view lands on the
    // current week rather than the week containing whatever March date was left over.
    var periodAnchor by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(chartRange) { periodAnchor = LocalDate.now() }
    var showSupporterLockedDialog by remember { mutableStateOf(false) }

    if (showSupporterLockedDialog) {
        SupporterOnlyDialog(
            onDismiss = { showSupporterLockedDialog = false },
            onBecomeSupporter = {
                showSupporterLockedDialog = false
                viewModel.setSupporter(true)
            },
        )
    }

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

            val earliestLog = logs.minOfOrNull { it.date }
            val showRangePicker = earliestLog != null &&
                ChronoUnit.DAYS.between(earliestLog, LocalDate.now()) > CHART_RANGE_PICKER_THRESHOLD_DAYS
            val availableRanges = if (showRangePicker) {
                ChartRange.entries
            } else {
                listOf(ChartRange.ALL)
            }

            // One control bar above both charts rather than each card growing its own - date
            // range and chart style both apply to the weight and calorie trends together, since
            // there's no reason to view them on different ranges or in different styles.
            item {
                ChartControlBar(
                    range = chartRange,
                    availableRanges = availableRanges,
                    onRangeChange = { chartRange = it },
                    style = chartStyle,
                    onStyleChange = { chartStyle = it },
                    isSupporter = isSupporter,
                    onLockedTap = { showSupporterLockedDialog = true },
                )
            }

            val firstDayOfWeek = profile?.weightUnit?.firstDayOfWeek ?: DayOfWeek.MONDAY
            val bounds = periodBounds(chartRange, periodAnchor, firstDayOfWeek)
            if (bounds != null) {
                item {
                    PeriodNavigationBar(
                        title = periodTitle(chartRange, bounds.first, bounds.second),
                        onPrevious = { periodAnchor = shiftAnchor(chartRange, periodAnchor, -1) },
                        onNext = { periodAnchor = shiftAnchor(chartRange, periodAnchor, 1) },
                        canGoNext = bounds.second < LocalDate.now(),
                    )
                }
            }

            val chartLogs = bounds?.let { (start, end) -> logs.filter { it.date >= start && it.date <= end } } ?: logs
            val effectiveChartStyle = if (isSupporter) chartStyle else ChartStyle.LINE

            item {
                WeightTrendCard(
                    logs = chartLogs,
                    profile = profile,
                    chartStyle = effectiveChartStyle,
                    range = chartRange,
                    firstDayOfWeek = firstDayOfWeek,
                )
            }

            // Everyone gets both trend charts now - a supporter perk is the chart style (see
            // ChartStyleToggle), not seeing the calorie chart at all.
            item {
                CalorieTrendCard(
                    logs = chartLogs,
                    chartStyle = effectiveChartStyle,
                    range = chartRange,
                    firstDayOfWeek = firstDayOfWeek,
                )
            }

            item { HistoryEntryCard(dayCount = logs.size, onClick = onShowHistory) }
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
private fun HistoryEntryCard(dayCount: Int, onClick: () -> Unit) {
    JourneyEntryCard(
        icon = Icons.Outlined.Schedule,
        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
        title = "History",
        subtitle = if (dayCount == 1) "1 day logged" else "$dayCount days logged",
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

// Milestones are grouped by category rather than shown as one flat list, since each category can
// now hold every rung ever reached (not just the latest) - the most recent rung in a category
// shows as a full gold-accented card, with any earlier ones in that category tucked behind an
// expandable row styled like a medal case (silver, then bronze, then plain for anything further
// back) so the achievement history stays visible without the page turning into a long wall of
// cards that all say roughly the same thing.
@Composable
private fun MilestonesSection(milestones: List<Milestone>) {
    if (milestones.isEmpty()) return
    val achieved = milestones.filter { it.achieved }
    val upNext = milestones.filter { !it.achieved }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (achieved.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Unlocked", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                MilestoneCategory.entries.forEach { category ->
                    val inCategory = achieved.filter { it.category == category }
                    if (inCategory.isNotEmpty()) {
                        AchievementTierGroup(inCategory)
                    }
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

/** [achievedInCategory] is in the same oldest-to-newest order JourneyMilestones returns it in, so
 * the last entry is always the most recently reached rung in this category. */
@Composable
private fun AchievementTierGroup(achievedInCategory: List<Milestone>) {
    var expanded by remember { mutableStateOf(false) }
    val latest = achievedInCategory.last()
    val earlier = achievedInCategory.dropLast(1).asReversed()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AchievementCard(latest, tier = AchievementTier.GOLD, modifier = Modifier.fillMaxWidth())
        if (earlier.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (expanded) "Hide earlier milestones" else "+${earlier.size} earlier in this category",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                AchievementGrid(
                    earlier,
                    tierFor = { index -> if (index == 0) AchievementTier.SILVER else if (index == 1) AchievementTier.BRONZE else AchievementTier.NONE },
                )
            }
        }
    }
}

@Composable
private fun AchievementGrid(milestones: List<Milestone>, tierFor: (Int) -> AchievementTier = { AchievementTier.NONE }) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        milestones.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { columnIndex, milestone ->
                    AchievementCard(milestone, tier = tierFor(rowIndex * 2 + columnIndex), modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private enum class AchievementTier(val ringColor: Color?) {
    GOLD(Color(0xFFD4AF37)),
    SILVER(Color(0xFFA8A8A8)),
    BRONZE(Color(0xFFB08D57)),
    NONE(null),
}

// A fixed height, with the title and hint both clamped to a line count, so a short title like
// "Goal reached" and a long one like "A quarter of the way there" produce the exact same card
// size instead of the grid's rows subtly drifting out of alignment with each other.
private val ACHIEVEMENT_CARD_HEIGHT = 148.dp

@Composable
private fun AchievementCard(milestone: Milestone, tier: AchievementTier = AchievementTier.NONE, modifier: Modifier = Modifier) {
    val badgeColor = if (milestone.achieved) categoryContainerColor(milestone.category) else MaterialTheme.colorScheme.surfaceVariant
    val badgeContentColor = if (milestone.achieved) categoryContentColor(milestone.category) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier.height(ACHIEVEMENT_CARD_HEIGHT),
        colors = CardDefaults.cardColors(
            containerColor = if (milestone.achieved) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
                    .let { if (tier.ringColor != null) it.border(2.dp, tier.ringColor, CircleShape) else it },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    categoryIcon(milestone.category),
                    contentDescription = null,
                    tint = badgeContentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                milestone.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (milestone.achieved) "Unlocked" else (milestone.hint ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = if (milestone.achieved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

/** One control bar above both trend cards - date range and chart style apply to the weight and
 * calorie trends together, since there's no reason to view them on different ranges or in
 * different styles. */
@Composable
private fun ChartControlBar(
    range: ChartRange,
    availableRanges: List<ChartRange>,
    onRangeChange: (ChartRange) -> Unit,
    style: ChartStyle,
    onStyleChange: (ChartStyle) -> Unit,
    isSupporter: Boolean,
    onLockedTap: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Scrollable and weighted rather than a plain Row - 4 range chips plus 3 style icons
        // don't reliably fit a phone's width in one unconstrained row, which pushed (and on
        // narrow screens, clipped) the style toggle off the visible edge entirely. Weighting
        // this side measures the toggle at its own fixed size first and gives the chips whatever
        // space is left, scrolling instead of overflowing.
        Row(
            modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            availableRanges.forEach { r ->
                FilterChip(
                    selected = range == r,
                    onClick = { onRangeChange(r) },
                    label = { Text(r.label) },
                    colors = themedFilterChipColors(),
                )
            }
        }
        ChartStyleToggle(style, onStyleChange, isSupporter, onLockedTap)
    }
}

/** Pages between calendar periods (an earlier/later week, month, or year) - shown under the
 * control bar whenever the selected range is an actual navigable period (not All, which has
 * nothing to page between). [canGoNext] stops navigation into a period that hasn't started yet. */
@Composable
private fun PeriodNavigationBar(title: String, onPrevious: () -> Unit, onNext: () -> Unit, canGoNext: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Earlier $title")
        }
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "Later",
                tint = if (canGoNext) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        }
    }
}

@Composable
private fun WeightTrendCard(
    logs: List<DailyLog>,
    profile: UserProfile?,
    chartStyle: ChartStyle,
    range: ChartRange,
    firstDayOfWeek: DayOfWeek,
) {
    val rawPoints = logs.filter { it.weightKg != null }.sortedBy { it.date }
        .map { it.date to it.weightKg!! }
    val lineColor = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.tertiary

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weight trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            val unit = profile?.weightUnit
            val aggregated = unit?.let {
                aggregateForRange(rawPoints, range, firstDayOfWeek).map { (date, kg) -> date to it.fromKg(kg) }
            } ?: emptyList()

            if (aggregated.size < 2 || unit == null) {
                Text(
                    "A few more days of data needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val dates = aggregated.map { it.first }
            val weights = aggregated.map { it.second }
            val goalWeight = unit.fromKg(profile.goalWeightKg)
            val unitLabel = unit.name.lowercase()
            val labelFormatter = dateLabelFormatter(range)

            TrendChartCanvas(
                dates = dates,
                values = weights,
                style = chartStyle,
                color = lineColor,
                axisStep = 1.0,
                goalValue = goalWeight,
                goalColor = goalColor,
                valueFormatter = { "${oneDecimal(it)} $unitLabel" },
                dateLabelFormatter = labelFormatter,
                contentDescription = "Weight trend chart, from ${oneDecimal(weights.first())} to " +
                    "${oneDecimal(weights.last())} $unitLabel, goal ${oneDecimal(goalWeight)} $unitLabel",
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(labelFormatter(dates.first()), style = MaterialTheme.typography.bodySmall)
                Text(labelFormatter(dates.last()), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "- - - goal (${oneDecimal(goalWeight)} $unitLabel)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun CalorieTrendCard(logs: List<DailyLog>, chartStyle: ChartStyle, range: ChartRange, firstDayOfWeek: DayOfWeek) {
    val rawPoints = logs.filter { it.calories != null }.sortedBy { it.date }
        .map { it.date to it.calories!! }
    val lineColor = MaterialTheme.colorScheme.secondary

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Calorie trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                InfoTooltipIcon(
                    title = "About these numbers",
                    text = "Calorie values are estimates based on what you logged each day - either a " +
                        "quick-pick \"how much did you eat\" answer converted to a number, or an exact " +
                        "amount you typed in yourself. They aren't measured, so treat the trend as a " +
                        "general direction rather than a precise count.",
                )
            }

            val aggregated = aggregateForRange(rawPoints, range, firstDayOfWeek)

            if (aggregated.size < 2) {
                Text(
                    "A few more days of data needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val dates = aggregated.map { it.first }
            val calories = aggregated.map { it.second }
            val labelFormatter = dateLabelFormatter(range)

            TrendChartCanvas(
                dates = dates,
                values = calories,
                style = chartStyle,
                color = lineColor,
                axisStep = 50.0,
                valueFormatter = { "${it.toInt()} kcal" },
                dateLabelFormatter = labelFormatter,
                contentDescription =
                    "Calorie trend chart, from ${calories.first().toInt()} to ${calories.last().toInt()} calories",
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(labelFormatter(dates.first()), style = MaterialTheme.typography.bodySmall)
                Text(labelFormatter(dates.last()), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Shared drawing + interaction for both trend charts - line/bar/area rendering, an optional
 * dashed goal line, and tap-to-reveal-value (tapping a bar/point shows its date and exact value,
 * tapping it again hides it). Pulled out of the two trend cards since they differed only in
 * their data, color, and value formatting, not in how the chart itself behaves. */
@Composable
private fun TrendChartCanvas(
    dates: List<LocalDate>,
    values: List<Double>,
    style: ChartStyle,
    color: Color,
    axisStep: Double,
    valueFormatter: (Double) -> String,
    contentDescription: String,
    goalValue: Double? = null,
    goalColor: Color = Color.Unspecified,
    dateLabelFormatter: (LocalDate) -> String = { it.format(shortDateFormatter) },
) {
    var selectedIndex by remember(dates) { mutableStateOf<Int?>(null) }
    val dataMin = minOf(values.min(), goalValue ?: values.min())
    val dataMax = maxOf(values.max(), goalValue ?: values.max())
    // Rounded outward to the nearest step rather than the exact data extremes, so a point
    // sitting right at the top or bottom of the chart has a little breathing room instead of
    // touching the edge - e.g. an actual range of 1929-4501 becomes an axis of 1900-4550.
    val (minValue, maxValue) = niceAxisBounds(dataMin, dataMax, axisStep)
    val range = (maxValue - minValue).let { if (it < 0.01) 1.0 else it }

    Box(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(vertical = 4.dp)
                .semantics { this.contentDescription = contentDescription }
                .pointerInput(dates) {
                    detectTapGestures { offset ->
                        val tapped = if (dates.size == 1) {
                            0
                        } else {
                            (offset.x / size.width * (dates.size - 1)).roundToInt().coerceIn(0, dates.size - 1)
                        }
                        selectedIndex = if (selectedIndex == tapped) null else tapped
                    }
                },
        ) {
            val chartHeight = size.height
            val chartWidth = size.width
            fun xFor(index: Int): Float =
                if (dates.size == 1) chartWidth / 2f else chartWidth * index / (dates.size - 1).toFloat()
            fun yFor(value: Double): Float =
                (chartHeight - ((value - minValue) / range * chartHeight)).toFloat()

            goalValue?.let { goal ->
                val goalY = yFor(goal)
                drawLine(
                    color = goalColor,
                    start = Offset(0f, goalY),
                    end = Offset(chartWidth, goalY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                )
            }

            when (style) {
                ChartStyle.BAR -> {
                    val barWidth = (chartWidth / values.size) * 0.6f
                    values.forEachIndexed { i, value ->
                        val x = xFor(i)
                        drawRect(
                            color = color,
                            topLeft = Offset(x - barWidth / 2f, yFor(value)),
                            size = Size(barWidth, chartHeight - yFor(value)),
                        )
                    }
                }
                ChartStyle.AREA -> {
                    val fillPath = Path().apply {
                        moveTo(xFor(0), chartHeight)
                        values.forEachIndexed { i, value -> lineTo(xFor(i), yFor(value)) }
                        lineTo(xFor(values.size - 1), chartHeight)
                        close()
                    }
                    drawPath(fillPath, color = color.copy(alpha = 0.25f))
                    for (i in 0 until values.size - 1) {
                        drawLine(
                            color = color,
                            start = Offset(xFor(i), yFor(values[i])),
                            end = Offset(xFor(i + 1), yFor(values[i + 1])),
                            strokeWidth = 4f,
                        )
                    }
                }
                ChartStyle.LINE -> {
                    for (i in 0 until values.size - 1) {
                        drawLine(
                            color = color,
                            start = Offset(xFor(i), yFor(values[i])),
                            end = Offset(xFor(i + 1), yFor(values[i + 1])),
                            strokeWidth = 4f,
                        )
                    }
                    values.forEachIndexed { i, value -> drawCircle(color = color, radius = 5f, center = Offset(xFor(i), yFor(value))) }
                }
            }

            selectedIndex?.let { i ->
                val x = xFor(i)
                drawLine(
                    color = color,
                    start = Offset(x, 0f),
                    end = Offset(x, chartHeight),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                )
                drawCircle(color = color, radius = 7f, center = Offset(x, yFor(values[i])))
            }
        }
        AxisLabel(valueFormatter(maxValue), Alignment.TopStart)
        AxisLabel(valueFormatter(minValue), Alignment.BottomStart)
        selectedIndex?.let { i ->
            Text(
                "${dateLabelFormatter(dates[i])}: ${valueFormatter(values[i])}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** A compact 3-way style switch shared by both trend cards - picking one applies to both charts
 * at once, since there's no reason to view them differently. Bar and area are a supporter perk,
 * shown the same way as a locked color theme in Settings: still visible and tappable, but with a
 * small lock badge instead of actually switching, rather than disappearing outright (so the perk
 * is discoverable, not just absent). */
@Composable
private fun ChartStyleToggle(
    selected: ChartStyle,
    onChange: (ChartStyle) -> Unit,
    isSupporter: Boolean,
    onLockedTap: () -> Unit,
) {
    @Composable
    fun StyleIcon(style: ChartStyle, icon: ImageVector, locked: Boolean) {
        Box {
            IconButton(onClick = { if (!locked) onChange(style) else onLockedTap() }) {
                Icon(
                    icon,
                    contentDescription = if (locked) "${style.label} chart - supporters only" else "${style.label} chart",
                    tint = if (selected == style) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (locked) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 6.dp, end = 6.dp)
                        .size(12.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(1.dp),
                )
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        StyleIcon(ChartStyle.LINE, Icons.AutoMirrored.Outlined.ShowChart, locked = false)
        StyleIcon(ChartStyle.BAR, Icons.Outlined.BarChart, locked = !isSupporter)
        StyleIcon(ChartStyle.AREA, Icons.Outlined.AreaChart, locked = !isSupporter)
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


// Shared with JourneyHistoryScreen's WeekCard.
fun oneDecimal(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

/** Rounds a data range outward to the nearest multiple of [step], so chart axis bounds sit a
 * little past the actual min/max instead of right on top of them - and if a value already lands
 * exactly on a multiple (no rounding would occur), pushes out one more step so there's always at
 * least a sliver of visible headroom. */
private fun niceAxisBounds(dataMin: Double, dataMax: Double, step: Double): Pair<Double, Double> {
    var lo = floor(dataMin / step) * step
    var hi = ceil(dataMax / step) * step
    if (lo >= dataMin) lo -= step
    if (hi <= dataMax) hi += step
    return lo to hi
}
