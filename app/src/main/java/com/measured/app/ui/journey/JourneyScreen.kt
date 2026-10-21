package com.measured.app.ui.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
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
import com.measured.app.ui.common.SupporterOnlyDialog
import com.measured.app.ui.theme.themedFilterChipColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d")

// Same green used for the Today screen's "high confidence" accuracy indicator - a neutral,
// theme-independent "done" signal, not a judgment on any of the day's actual numbers. Shared
// with JourneyHistoryScreen's DayRow.
val DAY_COMPLETE_COLOR = Color(0xFF43A047)

private enum class ChartStyle { LINE, BAR }

// Once there's a lot of history, defaulting to the full span means early, often noisier data
// stretches the chart's auto min/max and flattens how recent changes look - a plain "zoom in"
// toggle beats trying to guess the right window automatically.
private enum class ChartWindow(val label: String) {
    RECENT("Last 60 days"),
    ALL("All time"),
}
private const val CHART_WINDOW_THRESHOLD_DAYS = 60L

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
    var chartWindow by remember { mutableStateOf(ChartWindow.RECENT) }
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
            val spansMoreThanWindow = earliestLog != null &&
                ChronoUnit.DAYS.between(earliestLog, LocalDate.now()) > CHART_WINDOW_THRESHOLD_DAYS
            if (spansMoreThanWindow) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChartWindow.entries.forEach { window ->
                            FilterChip(
                                selected = chartWindow == window,
                                onClick = { chartWindow = window },
                                label = { Text(window.label) },
                                colors = themedFilterChipColors(),
                            )
                        }
                    }
                }
            }
            val chartLogs = if (spansMoreThanWindow && chartWindow == ChartWindow.RECENT) {
                val cutoff = LocalDate.now().minusDays(CHART_WINDOW_THRESHOLD_DAYS)
                logs.filter { it.date >= cutoff }
            } else {
                logs
            }

            item {
                WeightTrendCard(
                    logs = chartLogs,
                    profile = profile,
                    chartStyle = chartStyle,
                    onChartStyleChange = { chartStyle = it },
                    isSupporter = isSupporter,
                    onLockedTap = { showSupporterLockedDialog = true },
                )
            }

            // Everyone gets both trend charts now - a supporter perk is switching between line
            // and bar for them (see ChartStyleToggle), not seeing the calorie chart at all.
            item {
                CalorieTrendCard(
                    logs = chartLogs,
                    chartStyle = chartStyle,
                    onChartStyleChange = { chartStyle = it },
                    isSupporter = isSupporter,
                    onLockedTap = { showSupporterLockedDialog = true },
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

@Composable
private fun WeightTrendCard(
    logs: List<DailyLog>,
    profile: UserProfile?,
    chartStyle: ChartStyle,
    onChartStyleChange: (ChartStyle) -> Unit,
    isSupporter: Boolean,
    onLockedTap: () -> Unit,
) {
    val points = logs.filter { it.weightKg != null }.sortedBy { it.date }
    val lineColor = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.tertiary
    // Switching to a bar chart is a supporter perk - if someone switched while a supporter and
    // then stopped being one, this still falls back to the line chart rather than honoring a
    // style choice they no longer have access to.
    val effectiveChartStyle = if (isSupporter) chartStyle else ChartStyle.LINE

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Weight trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (points.size >= 2) ChartStyleToggle(effectiveChartStyle, onChartStyleChange, isSupporter, onLockedTap)
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

                    if (effectiveChartStyle == ChartStyle.BAR) {
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

@Composable
private fun CalorieTrendCard(
    logs: List<DailyLog>,
    chartStyle: ChartStyle,
    onChartStyleChange: (ChartStyle) -> Unit,
    isSupporter: Boolean,
    onLockedTap: () -> Unit,
) {
    val points = logs.filter { it.calories != null }.sortedBy { it.date }
    val lineColor = MaterialTheme.colorScheme.secondary
    val effectiveChartStyle = if (isSupporter) chartStyle else ChartStyle.LINE

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Calorie trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (points.size >= 2) ChartStyleToggle(effectiveChartStyle, onChartStyleChange, isSupporter, onLockedTap)
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

                    if (effectiveChartStyle == ChartStyle.BAR) {
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
 * charts at once, since there's no reason to view them differently. Switching to a bar chart is
 * a supporter perk, shown the same way as a locked color theme in Settings: still visible and
 * tappable, but with a small lock badge instead of actually switching, rather than disappearing
 * outright (so the perk is discoverable, not just absent). */
@Composable
private fun ChartStyleToggle(
    selected: ChartStyle,
    onChange: (ChartStyle) -> Unit,
    isSupporter: Boolean,
    onLockedTap: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(ChartStyle.LINE) }) {
            Icon(
                Icons.AutoMirrored.Outlined.ShowChart,
                contentDescription = "Line chart",
                tint = if (selected == ChartStyle.LINE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { if (isSupporter) onChange(ChartStyle.BAR) else onLockedTap() }) {
                Icon(
                    Icons.Outlined.BarChart,
                    contentDescription = if (isSupporter) "Bar chart" else "Bar chart - supporters only",
                    tint = if (selected == ChartStyle.BAR) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isSupporter) {
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
