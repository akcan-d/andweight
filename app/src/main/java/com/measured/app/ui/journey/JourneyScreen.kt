package com.measured.app.ui.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.StarHalf
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AreaChart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.measured.app.domain.WeekGrouping
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
import kotlin.math.log10
import kotlin.math.pow
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
            val start = WeekGrouping.weekStart(anchor, firstDayOfWeek)
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

/** Every slot a period should show on the x-axis, including ones with no data - a Week always
 * shows its full 7 days (even ones that haven't happened yet or were never logged), a Month shows
 * every one of its calendar days, and a Year shows all 12 months, so the chart's shape reflects the
 * calendar rather than just wherever data happens to exist. */
private fun periodSlots(range: ChartRange, start: LocalDate, end: LocalDate): List<LocalDate> = when (range) {
    ChartRange.WEEK -> (0..6).map { start.plusDays(it.toLong()) }
    ChartRange.MONTH -> generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    ChartRange.YEAR -> (0..11).map { start.plusMonths(it.toLong()) }
    ChartRange.ALL -> emptyList()
}

/** Lines up [points] against every slot [range] should show within [bounds] - Week and Month stay
 * daily, Year averages each month's days into one point. A slot with nothing logged still gets an
 * entry (null value) rather than being skipped, so gaps show as actual gaps instead of silently
 * compressing the axis. All has no bounds to fill slots against, so it stays exactly what was
 * logged. */
private fun aggregateForRange(
    points: List<Pair<LocalDate, Double>>,
    range: ChartRange,
    bounds: Pair<LocalDate, LocalDate>?,
): List<Pair<LocalDate, Double?>> {
    if (range == ChartRange.ALL || bounds == null) return points.map { it.first to it.second }
    val (start, end) = bounds
    val slots = periodSlots(range, start, end)
    return when (range) {
        ChartRange.YEAR -> {
            val byMonth = points.filter { !it.first.isBefore(start) && !it.first.isAfter(end) }
                .groupBy { it.first.withDayOfMonth(1) }
                .mapValues { (_, group) -> group.map { it.second }.average() }
            slots.map { month -> month to byMonth[month] }
        }
        else -> {
            val byDate = points.associate { it.first to it.second }
            slots.map { date -> date to byDate[date] }
        }
    }
}

/** Per-point date labels for the chart's tap-to-reveal tooltip and axis ticks - a daily point
 * (Week, Month, or All) just needs its own date, a monthly one (Year) just needs the month name. */
private fun dateLabelFormatter(range: ChartRange): (LocalDate) -> String = when (range) {
    ChartRange.YEAR -> { date -> date.format(DateTimeFormatter.ofPattern("MMMM")) }
    else -> { date -> date.format(shortDateFormatter) }
}

/** Compact per-slot labels for the row of ticks under the chart - only shown when there are few
 * enough slots to not crowd (see [AxisTickRow]). */
private fun axisTickFormatter(range: ChartRange): (LocalDate) -> String = when (range) {
    ChartRange.YEAR -> { date -> date.format(DateTimeFormatter.ofPattern("MMM")) }
    else -> { date -> date.format(DateTimeFormatter.ofPattern("d")) }
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
    var chartSmoothed by remember { mutableStateOf(false) }
    var chartRange by remember { mutableStateOf(ChartRange.MONTH) }
    // Any date within the period currently shown - jumps back to today whenever the range type
    // itself changes, so switching from "last March" in Month view to Week view lands on the
    // current week rather than the week containing whatever March date was left over.
    var periodAnchor by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(chartRange) { periodAnchor = LocalDate.now() }
    var showSupporterLockedDialog by remember { mutableStateOf(false) }
    // Bumped by a tap anywhere outside a chart's own data points, so an open tap-to-reveal
    // tooltip clears instead of staying stuck open - taps that land on a point, a chip, or a
    // button are consumed there first and never reach this, so scrolling and other controls are
    // unaffected.
    var tapClearSignal by remember { mutableStateOf(0) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .pointerInput(Unit) { detectTapGestures(onTap = { tapClearSignal++ }) },
    ) {
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
            // there's no reason to view them on different ranges or in different styles. Style
            // (and the supporter-only smoothing option) live behind a gear menu rather than their
            // own row of icons, which otherwise crowded against the range chips on narrow phones.
            item {
                ChartControlBar(
                    range = chartRange,
                    availableRanges = availableRanges,
                    onRangeChange = { chartRange = it },
                    style = chartStyle,
                    onStyleChange = { chartStyle = it },
                    smoothed = chartSmoothed,
                    onSmoothedChange = { chartSmoothed = it },
                    isSupporter = isSupporter,
                    onLockedTap = { showSupporterLockedDialog = true },
                )
            }

            val firstDayOfWeek = WeekGrouping.systemFirstDayOfWeek()
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
            val effectiveSmoothed = isSupporter && chartSmoothed

            item {
                WeightTrendCard(
                    logs = chartLogs,
                    profile = profile,
                    chartStyle = effectiveChartStyle,
                    smoothed = effectiveSmoothed,
                    range = chartRange,
                    bounds = bounds,
                    tapClearSignal = tapClearSignal,
                )
            }

            // Everyone gets both trend charts now - a supporter perk is the chart style and
            // smoothing (see ChartOptionsMenu), not seeing the calorie chart at all.
            item {
                CalorieTrendCard(
                    logs = chartLogs,
                    chartStyle = effectiveChartStyle,
                    smoothed = effectiveSmoothed,
                    range = chartRange,
                    bounds = bounds,
                    tapClearSignal = tapClearSignal,
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

// Milestones are grouped by category, each showing every rung ever reached in that category (not
// just the latest) plus whatever's coming up next in it - rather than a separate "Up next" pile at
// the bottom, since what's coming up in, say, Consistency belongs with the rest of that category's
// story, not lumped in with every other category's next rung. The most recent reached rung shows
// as a full gold-accented card; earlier ones in that category are tucked behind it - tapping the
// gold card itself (not a separate text link) reveals them, styled like a medal case (silver, then
// bronze, then plain for anything further back), with a small chevron on the card as the only hint
// there's more, so the page doesn't turn into a long wall of cards that all say roughly the same
// thing.
@Composable
private fun MilestonesSection(milestones: List<Milestone>) {
    if (milestones.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (milestones.none { it.achieved }) {
            Text(
                "Keep logging to earn your first milestone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        MilestoneCategory.entries.forEach { category ->
            val inCategory = milestones.filter { it.category == category }
            if (inCategory.isNotEmpty()) {
                MilestoneCategorySection(category, inCategory)
            }
        }
    }
}

@Composable
private fun MilestoneCategorySection(category: MilestoneCategory, inCategory: List<Milestone>) {
    val achieved = inCategory.filter { it.achieved }
    val upNext = inCategory.firstOrNull { !it.achieved }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(category.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (achieved.isNotEmpty()) {
            AchievementTierGroup(achieved)
        }
        upNext?.let { AchievementCard(it, modifier = Modifier.fillMaxWidth()) }
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
        AchievementCard(
            latest,
            expandState = if (earlier.isNotEmpty()) expanded else null,
            onClick = if (earlier.isNotEmpty()) { { expanded = !expanded } } else null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (expanded && earlier.isNotEmpty()) {
            AchievementGrid(earlier)
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

// A fixed height, with the title and hint both clamped to a line count, so a short title like
// "Goal reached" and a long one like "A quarter of the way there" produce the exact same card
// size instead of the grid's rows subtly drifting out of alignment with each other.
private val ACHIEVEMENT_CARD_HEIGHT = 148.dp

@Composable
private fun AchievementCard(
    milestone: Milestone,
    /** null = no "more underneath" indicator; true/false = expanded/collapsed, with the card
     * itself as the tap target to toggle it (see [AchievementTierGroup]). */
    expandState: Boolean? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val badgeColor = if (milestone.achieved) categoryContainerColor(milestone.category) else MaterialTheme.colorScheme.surfaceVariant
    val badgeContentColor = if (milestone.achieved) categoryContentColor(milestone.category) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier.height(ACHIEVEMENT_CARD_HEIGHT).let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(
            containerColor = if (milestone.achieved) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            },
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        milestoneIcon(milestone),
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
            expandState?.let { expanded ->
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Hide earlier milestones in this category" else "Show earlier milestones in this category",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

/** A distinct icon per specific milestone rung (not just one icon per broad category) - threshold
 * values are parsed back out of [Milestone.id], which already embeds them (see
 * [com.measured.app.domain.JourneyMilestones]), rather than keeping a second parallel list that
 * could drift out of sync with the thresholds that actually define each milestone. */
private fun milestoneIcon(milestone: Milestone): ImageVector {
    val id = milestone.id
    return when (milestone.category) {
        MilestoneCategory.TIME -> when (id.removePrefix("time_").toLongOrNull()) {
            1L -> Icons.Outlined.RocketLaunch
            7L -> Icons.Outlined.CalendarToday
            14L -> Icons.Outlined.DateRange
            30L -> Icons.Outlined.CalendarMonth
            90L -> Icons.Outlined.Timeline
            180L -> Icons.Outlined.Insights
            365L -> Icons.Outlined.Celebration
            else -> Icons.Outlined.Schedule
        }
        MilestoneCategory.STREAK -> when (id.removePrefix("streak_").toIntOrNull()) {
            3 -> Icons.Outlined.Bolt
            7 -> Icons.Outlined.LocalFireDepartment
            14 -> Icons.Outlined.Whatshot
            30 -> Icons.Outlined.OfflineBolt
            60 -> Icons.Outlined.AutoAwesome
            100 -> Icons.Outlined.MilitaryTech
            else -> Icons.Outlined.LocalFireDepartment
        }
        MilestoneCategory.PROGRESS -> when (id.removePrefix("progress_").toDoubleOrNull()) {
            0.10 -> Icons.AutoMirrored.Outlined.TrendingUp
            0.25 -> Icons.Outlined.Flag
            0.50 -> Icons.AutoMirrored.Outlined.StarHalf
            0.75 -> Icons.Outlined.Star
            1.00 -> Icons.Outlined.WorkspacePremium
            else -> Icons.Outlined.Flag
        }
        MilestoneCategory.AMOUNT -> when (id.removePrefix("amount_").substringBefore('_').toDoubleOrNull()) {
            0.5 -> Icons.Outlined.Spa
            1.0 -> Icons.AutoMirrored.Outlined.DirectionsWalk
            2.0 -> Icons.AutoMirrored.Outlined.DirectionsRun
            5.0 -> Icons.Outlined.FitnessCenter
            10.0 -> Icons.Outlined.Insights
            20.0 -> Icons.Outlined.MilitaryTech
            50.0 -> Icons.Outlined.WorkspacePremium
            100.0 -> Icons.Outlined.EmojiEvents
            200.0 -> Icons.Outlined.Celebration
            500.0 -> Icons.Outlined.AutoAwesome
            else -> Icons.Outlined.MonitorWeight
        }
    }
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

/** Date range is the only thing left in the main control row now - chart style and line
 * smoothing moved into a small gear menu (see [ChartOptionsMenu]) once a growing list of
 * per-chart options started crowding this row badly enough to overlap on narrow phones. Both
 * still apply to the weight and calorie trends together, since there's no reason to view them on
 * different ranges or in different styles. */
@Composable
private fun ChartControlBar(
    range: ChartRange,
    availableRanges: List<ChartRange>,
    onRangeChange: (ChartRange) -> Unit,
    style: ChartStyle,
    onStyleChange: (ChartStyle) -> Unit,
    smoothed: Boolean,
    onSmoothedChange: (Boolean) -> Unit,
    isSupporter: Boolean,
    onLockedTap: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Scrollable and weighted rather than a plain Row, so a long run of range chips on a
        // narrow phone scrolls instead of overflowing past the gear icon.
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
        ChartOptionsMenu(style, onStyleChange, smoothed, onSmoothedChange, isSupporter, onLockedTap)
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
    smoothed: Boolean,
    range: ChartRange,
    bounds: Pair<LocalDate, LocalDate>?,
    tapClearSignal: Int,
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
                aggregateForRange(rawPoints, range, bounds).map { (date, kg) -> date to kg?.let(it::fromKg) }
            } ?: emptyList()

            if (unit == null || aggregated.count { it.second != null } < 2) {
                Text(
                    "A few more days of data needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val dates = aggregated.map { it.first }
            val weights = aggregated.map { it.second }
            val nonNullWeights = weights.filterNotNull()
            val goalWeight = unit.fromKg(profile.goalWeightKg)
            val unitLabel = unit.name.lowercase()

            TrendChartCanvas(
                dates = dates,
                values = weights,
                style = chartStyle,
                smoothed = smoothed,
                color = lineColor,
                axisStep = 1.0,
                goalValue = goalWeight,
                goalColor = goalColor,
                unitLabel = unitLabel,
                valueFormatter = { "${oneDecimal(it)} $unitLabel" },
                tickFormatter = { oneDecimal(it) },
                dateLabelFormatter = dateLabelFormatter(range),
                tapClearSignal = tapClearSignal,
                contentDescription = "Weight trend chart, from ${oneDecimal(nonNullWeights.first())} to " +
                    "${oneDecimal(nonNullWeights.last())} $unitLabel, goal ${oneDecimal(goalWeight)} $unitLabel",
            )

            AxisTickRow(dates, range)
            Text(
                "- - - goal (${oneDecimal(goalWeight)} $unitLabel)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun CalorieTrendCard(
    logs: List<DailyLog>,
    chartStyle: ChartStyle,
    smoothed: Boolean,
    range: ChartRange,
    bounds: Pair<LocalDate, LocalDate>?,
    tapClearSignal: Int,
) {
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

            val aggregated = aggregateForRange(rawPoints, range, bounds)

            if (aggregated.count { it.second != null } < 2) {
                Text(
                    "A few more days of data needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val dates = aggregated.map { it.first }
            val calories = aggregated.map { it.second }
            val nonNullCalories = calories.filterNotNull()

            TrendChartCanvas(
                dates = dates,
                values = calories,
                style = chartStyle,
                smoothed = smoothed,
                color = lineColor,
                axisStep = 200.0,
                forceMinZero = true,
                unitLabel = "kcal",
                valueFormatter = { "${it.toInt()} kcal" },
                tickFormatter = { it.toInt().toString() },
                dateLabelFormatter = dateLabelFormatter(range),
                tapClearSignal = tapClearSignal,
                contentDescription =
                    "Calorie trend chart, from ${nonNullCalories.first().toInt()} to ${nonNullCalories.last().toInt()} calories",
            )

            AxisTickRow(dates, range)
        }
    }
}

/** Compact per-slot labels under the chart - one per day/month when there are few enough to not
 * crowd (a Week's 7 days, a Year's 12 months), falling back to just the period's first and last
 * date otherwise (a Month's 28-31 days, or All's unbounded daily history). */
@Composable
private fun AxisTickRow(dates: List<LocalDate>, range: ChartRange) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (dates.size <= 12) {
            val tickFormatter = axisTickFormatter(range)
            dates.forEach { d ->
                Text(tickFormatter(d), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val labelFormatter = dateLabelFormatter(range)
            Text(labelFormatter(dates.first()), style = MaterialTheme.typography.bodySmall)
            Text(labelFormatter(dates.last()), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Shared drawing + interaction for both trend charts - line/bar/area rendering (with optional
 * smoothing on line/area), an optional dashed goal line, and tap-to-reveal-value (tapping a
 * bar/point shows its date and exact value; tapping it again, or anywhere else on screen, hides
 * it - see [tapClearSignal]). Pulled out of the two trend cards since they differed only in their
 * data, color, and value formatting, not in how the chart itself behaves.
 *
 * [values] can hold nulls for a slot with nothing logged (an empty day in Week/Month, or an empty
 * month in Year) - the line/area breaks across a gap instead of connecting through it, and a bar
 * chart simply has no bar there. A lone logged value surrounded by gaps gets a small dot instead
 * of vanishing silently, since there's no line or bar to draw it through. */
private val CHART_HEIGHT = 160.dp
private val AXIS_GUTTER_WIDTH = 34.dp

@Composable
private fun TrendChartCanvas(
    dates: List<LocalDate>,
    values: List<Double?>,
    style: ChartStyle,
    smoothed: Boolean,
    color: Color,
    axisStep: Double,
    unitLabel: String,
    valueFormatter: (Double) -> String,
    tickFormatter: (Double) -> String,
    contentDescription: String,
    tapClearSignal: Int,
    forceMinZero: Boolean = false,
    goalValue: Double? = null,
    goalColor: Color = Color.Unspecified,
    dateLabelFormatter: (LocalDate) -> String = { it.format(shortDateFormatter) },
) {
    var selectedIndex by remember(dates) { mutableStateOf<Int?>(null) }
    LaunchedEffect(tapClearSignal) { selectedIndex = null }

    val nonNullValues = values.filterNotNull()
    // Rounded outward to the nearest step rather than the exact data extremes, so a point
    // sitting right at the top or bottom of the chart has a little breathing room instead of
    // touching the edge - e.g. an actual range of 1929-4501 becomes an axis of 1900-4550. The
    // calorie chart instead pins its floor to zero (forceMinZero) - a calorie count has a real
    // zero, unlike weight, so there's no reason the axis should ever imply otherwise.
    val (minValue, maxValue) = if (forceMinZero) {
        0.0 to niceMaxOnly(maxOf(nonNullValues.max(), goalValue ?: nonNullValues.max()), axisStep)
    } else {
        val dataMin = minOf(nonNullValues.min(), goalValue ?: nonNullValues.min())
        val dataMax = maxOf(nonNullValues.max(), goalValue ?: nonNullValues.max())
        niceAxisBounds(dataMin, dataMax, axisStep)
    }
    val valueRange = (maxValue - minValue).let { if (it < 0.01) 1.0 else it }
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val runs = remember(values) { nonNullRuns(values) }
    // Round, evenly-spaced numbers (not raw fractions of the range, which routinely land on
    // something like 123.33) - these double as both the gridlines drawn in the canvas and the
    // labels in the gutter beside it, so the two always line up.
    val ticks = remember(minValue, maxValue) { niceTickValues(minValue, maxValue) }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // A dedicated gutter to the left of the plotted area, rather than labels floating over
        // the data - and the unit is named once here instead of repeated on every tick.
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(AXIS_GUTTER_WIDTH)) {
            Text(unitLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
                ticks.forEach { tickValue ->
                    val f = ((tickValue - minValue) / valueRange).toFloat().coerceIn(0f, 1f)
                    Text(
                        tickFormatter(tickValue),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.TopEnd).offset(y = CHART_HEIGHT * (1f - f) - 7.dp),
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT)
                    .semantics { this.contentDescription = contentDescription }
                    .pointerInput(dates) {
                        detectTapGestures { offset ->
                            val tapped = if (dates.size == 1) {
                                0
                            } else {
                                (offset.x / size.width * (dates.size - 1)).roundToInt().coerceIn(0, dates.size - 1)
                            }
                            if (values[tapped] != null) {
                                selectedIndex = if (selectedIndex == tapped) null else tapped
                            }
                        }
                    },
            ) {
                val chartHeight = size.height
                val chartWidth = size.width
                fun xFor(index: Int): Float =
                    if (dates.size == 1) chartWidth / 2f else chartWidth * index / (dates.size - 1).toFloat()
                fun yFor(value: Double): Float =
                    (chartHeight - ((value - minValue) / valueRange * chartHeight)).toFloat()

                ticks.forEach { tickValue ->
                    val y = yFor(tickValue)
                    drawLine(color = gridColor, start = Offset(0f, y), end = Offset(chartWidth, y), strokeWidth = 1.5f)
                }

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
                            if (value == null) return@forEachIndexed
                            val x = xFor(i)
                            drawRect(
                                color = color,
                                topLeft = Offset(x - barWidth / 2f, yFor(value)),
                                size = Size(barWidth, chartHeight - yFor(value)),
                            )
                        }
                    }
                    ChartStyle.AREA -> {
                        runs.forEach { run ->
                            val points = run.map { i -> Offset(xFor(i), yFor(values[i]!!)) }
                            if (points.size == 1) {
                                drawCircle(color = color, radius = 4f, center = points[0])
                                return@forEach
                            }
                            val linePath = if (smoothed) smoothPathThrough(points) else straightPathThrough(points)
                            val fillPath = Path().apply {
                                addPath(linePath)
                                lineTo(points.last().x, chartHeight)
                                lineTo(points.first().x, chartHeight)
                                close()
                            }
                            drawPath(fillPath, color = color.copy(alpha = 0.25f))
                            drawPath(linePath, color = color, style = Stroke(width = 4f, cap = StrokeCap.Round))
                        }
                    }
                    ChartStyle.LINE -> {
                        runs.forEach { run ->
                            val points = run.map { i -> Offset(xFor(i), yFor(values[i]!!)) }
                            if (points.size == 1) {
                                drawCircle(color = color, radius = 4f, center = points[0])
                            } else {
                                val path = if (smoothed) smoothPathThrough(points) else straightPathThrough(points)
                                drawPath(path, color = color, style = Stroke(width = 4f, cap = StrokeCap.Round))
                            }
                        }
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
                    drawCircle(color = color, radius = 7f, center = Offset(x, yFor(values[i]!!)))
                }
            }

            selectedIndex?.let { i ->
                Text(
                    "${dateLabelFormatter(dates[i])}: ${valueFormatter(values[i]!!)}",
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
}

/** Chart style and line smoothing both apply to the weight and calorie trends together, tucked
 * behind one gear icon rather than a row of their own icons - open to everyone, so the perks stay
 * discoverable, with supporter-only options shown locked (a small lock icon, still tappable to
 * explain itself) rather than hidden outright. */
@Composable
private fun ChartOptionsMenu(
    style: ChartStyle,
    onStyleChange: (ChartStyle) -> Unit,
    smoothed: Boolean,
    onSmoothedChange: (Boolean) -> Unit,
    isSupporter: Boolean,
    onLockedTap: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "Chart options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ChartOptionsHeader("Style")
            ChartStyle.entries.forEach { option ->
                val locked = option != ChartStyle.LINE && !isSupporter
                ChartOptionMenuItem(
                    label = option.label,
                    icon = styleIcon(option),
                    selected = style == option,
                    locked = locked,
                    onClick = {
                        expanded = false
                        if (locked) onLockedTap() else onStyleChange(option)
                    },
                )
            }
            HorizontalDivider()
            ChartOptionsHeader("Line")
            ChartOptionMenuItem(
                label = "Smoothed",
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                selected = smoothed,
                locked = !isSupporter,
                onClick = {
                    expanded = false
                    if (!isSupporter) onLockedTap() else onSmoothedChange(!smoothed)
                },
            )
        }
    }
}

@Composable
private fun ChartOptionsHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun ChartOptionMenuItem(label: String, icon: ImageVector, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = {
            if (locked) {
                Icon(Icons.Outlined.Lock, contentDescription = "Supporters only", modifier = Modifier.size(16.dp))
            } else if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        },
        onClick = onClick,
    )
}

private fun styleIcon(style: ChartStyle): ImageVector = when (style) {
    ChartStyle.LINE -> Icons.AutoMirrored.Outlined.ShowChart
    ChartStyle.BAR -> Icons.Outlined.BarChart
    ChartStyle.AREA -> Icons.Outlined.AreaChart
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

/** Same outward-rounding as [niceAxisBounds], but for an axis whose floor is pinned elsewhere
 * (the calorie chart's zero) rather than derived from the data - only the top needs rounding. */
private fun niceMaxOnly(dataMax: Double, step: Double): Double {
    var hi = ceil(dataMax / step) * step
    if (hi <= dataMax) hi += step
    return hi
}

/** A small, evenly-spaced set of round numbers (1, 2, or 5 times a power of ten) spanning
 * [minValue]..[maxValue], for axis tick labels - rather than literal fractions of the range,
 * which routinely land on an ugly number like 123.33 even when the axis bounds themselves are
 * round. [targetCount] is a rough guide, not a guarantee - the actual count depends on how evenly
 * the chosen step divides the range. */
private fun niceTickValues(minValue: Double, maxValue: Double, targetCount: Int = 4): List<Double> {
    val range = maxValue - minValue
    if (range <= 0.0) return listOf(minValue)
    val rawStep = range / (targetCount - 1)
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val residual = rawStep / magnitude
    val niceResidual = when {
        residual <= 1.5 -> 1.0
        residual <= 3.0 -> 2.0
        residual <= 7.0 -> 5.0
        else -> 10.0
    }
    val step = niceResidual * magnitude
    val ticks = mutableListOf<Double>()
    var v = ceil(minValue / step) * step
    while (v <= maxValue + step * 0.001) {
        ticks += v
        v += step
    }
    return ticks.ifEmpty { listOf(minValue, maxValue) }
}

/** The index ranges of consecutive non-null values in [values] - a run is where the chart can
 * actually draw a connected line/area; a null breaks it into a new run. */
private fun nonNullRuns(values: List<Double?>): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = -1
    values.forEachIndexed { i, v ->
        if (v != null) {
            if (start == -1) start = i
        } else if (start != -1) {
            runs += start..(i - 1)
            start = -1
        }
    }
    if (start != -1) runs += start..values.lastIndex
    return runs
}

private fun straightPathThrough(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
}

/** A lightweight smoothed curve through [points] - each segment is a quadratic Bezier from one
 * point to the midpoint of the next, using the point itself as the control, rather than a full
 * Catmull-Rom spline. Simpler to compute and, for a day-to-day trend line, visually close enough
 * to not be worth the extra math. */
private fun smoothPathThrough(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until points.size - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        path.quadraticTo(p0.x, p0.y, (p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}
