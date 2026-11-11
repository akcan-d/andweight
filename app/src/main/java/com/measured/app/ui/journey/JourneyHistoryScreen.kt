package com.measured.app.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.measured.app.domain.DailyLog
import com.measured.app.domain.Phrasing
import com.measured.app.domain.UserProfile
import com.measured.app.domain.WeekGrouping
import com.measured.app.ui.AppViewModelProvider
import com.measured.app.ui.common.IntakeChip
import com.measured.app.ui.theme.themedFilterChipColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

private enum class JourneyViewMode(val label: String) {
    CALENDAR("Calendar"),
    WEEKLY("Weekly trend"),
}

/** History used to live inline on the main Journey page; it moved here for the same reason
 * Milestones and the research section did - it's substantial enough content (a full switchable
 * daily/weekly view, potentially a year or more of entries) to compete for attention with the
 * trend charts rather than sit underneath them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyHistoryScreen(
    contentPadding: PaddingValues,
    onEditDay: (LocalDate) -> Unit,
    onBack: () -> Unit,
    viewModel: JourneyViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val profile by viewModel.profile.collectAsState()
    val logs by viewModel.logs.collectAsState()
    var viewMode by remember { mutableStateOf(JourneyViewMode.CALENDAR) }

    val today = LocalDate.now()
    val logsByDate = remember(logs) { logs.associateBy { it.date } }
    var viewedMonth by remember { mutableStateOf(YearMonth.from(today)) }
    // Always starts on today rather than null/unselected - a calendar that opens with nothing
    // picked would need its own "what do I show below the grid" fallback anyway, and today is
    // exactly what someone opening History most likely wants to check first.
    var selectedDate by remember { mutableStateOf(today) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { topBarPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topBarPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                JourneyViewMode.CALENDAR -> {
                    item {
                        CalendarMonthNav(
                            month = viewedMonth,
                            onPrevious = { viewedMonth = viewedMonth.minusMonths(1) },
                            onNext = { viewedMonth = viewedMonth.plusMonths(1) },
                            canGoNext = viewedMonth < YearMonth.from(today),
                        )
                    }
                    item {
                        val loggedThisMonth = logsByDate.keys.count { YearMonth.from(it) == viewedMonth }
                        val daysSoFar = if (viewedMonth == YearMonth.from(today)) today.dayOfMonth else viewedMonth.lengthOfMonth()
                        Text(
                            "$loggedThisMonth of $daysSoFar days logged",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        CalendarGrid(
                            month = viewedMonth,
                            firstDayOfWeek = WeekGrouping.systemFirstDayOfWeek(),
                            logsByDate = logsByDate,
                            selectedDate = selectedDate,
                            today = today,
                            onDayClick = { selectedDate = it },
                        )
                    }
                    item {
                        CalendarLegend()
                    }
                    item {
                        SelectedDayDetail(
                            date = selectedDate,
                            log = logsByDate[selectedDate],
                            profile = profile,
                            onEdit = { onEditDay(selectedDate) },
                            onDelete = { viewModel.deleteLog(selectedDate) },
                        )
                    }
                }
                JourneyViewMode.WEEKLY -> {
                    val firstDayOfWeek = WeekGrouping.systemFirstDayOfWeek()
                    val weeks = calendarWeekSummaries(logs, firstDayOfWeek).reversed()
                    items(weeks) { week -> WeekCard(week, profile) }
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthNav(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, canGoNext: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous month")
        }
        Text(month.format(monthFormatter), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "Next month",
                tint = if (canGoNext) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        }
    }
}

/** How fully a day was logged - drives both the cell's fill color (the same "progress as fill"
 * language used elsewhere in the app, like the hero ring) and whether it can be tapped at all. */
private enum class DayFillState { COMPLETE, PARTIAL, EMPTY }

private fun DailyLog?.fillState(): DayFillState = when {
    this == null -> DayFillState.EMPTY
    weightKg != null && calories != null -> DayFillState.COMPLETE
    weightKg != null || calories != null -> DayFillState.PARTIAL
    else -> DayFillState.EMPTY
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    logsByDate: Map<LocalDate, DailyLog>,
    selectedDate: LocalDate,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val dow = DayOfWeek.of((firstDayOfWeek.value - 1 + i) % 7 + 1)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val firstOfMonth = month.atDay(1)
        val leadingBlanks = (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val totalDays = month.lengthOfMonth()
        val rows = (leadingBlanks + totalDays + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNum = row * 7 + col - leadingBlanks + 1
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..totalDays) {
                            val date = month.atDay(dayNum)
                            DayCell(
                                date = date,
                                fillState = logsByDate[date].fillState(),
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                isFuture = date.isAfter(today),
                                onClick = { onDayClick(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    fillState: DayFillState,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        fillState == DayFillState.COMPLETE -> DAY_COMPLETE_COLOR.copy(alpha = 0.22f)
        fillState == DayFillState.PARTIAL -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(CircleShape)
            .background(containerColor)
            .let {
                if (isToday && !isSelected) it.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else it
            }
            .clickable(enabled = !isFuture, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CalendarLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(DAY_COMPLETE_COLOR.copy(alpha = 0.6f), "Logged")
        LegendDot(MaterialTheme.colorScheme.secondaryContainer, "Partial")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SelectedDayDetail(date: LocalDate, log: DailyLog?, profile: UserProfile?, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember(date) { mutableStateOf(false) }
    val unit = profile?.weightUnit

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(date.format(dateFormatter), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit this day")
                    }
                    if (log != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete this day", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (log == null) {
                Text(
                    "No entry logged for this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (log.weightKg != null && unit != null) {
                        Text(Phrasing.formatWeight(log.weightKg, unit), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("No weight logged", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IntakeChip(log)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this day?") },
            text = { Text("This removes the weight and meal info logged for ${date.format(dateFormatter)}. This can't be undone.") },
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

/** A real Mon-Sun (or whatever the phone's locale uses) calendar week, purely for this display -
 * distinct from [com.measured.app.domain.TdeeEngine]'s own weekly windows, which are intentionally
 * anchored to whenever the first logged day happened to fall rather than the calendar, since that
 * anchoring is what keeps its smoothing stable. Averages here are plain means of whatever got
 * logged that week (no carry-forward from previous weeks), so they're a simpler number than the
 * TDEE engine's own weekly figures and don't try to reproduce its estimate - "Estimated TDEE" is
 * on the Status screen for that. */
private data class CalendarWeekSummary(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val avgWeightKg: Double?,
    val avgCalories: Double?,
)

private fun calendarWeekSummaries(logs: List<DailyLog>, firstDayOfWeek: DayOfWeek): List<CalendarWeekSummary> {
    if (logs.isEmpty()) return emptyList()
    val firstWeek = WeekGrouping.weekStart(logs.minOf { it.date }, firstDayOfWeek)
    val lastWeek = WeekGrouping.weekStart(logs.maxOf { it.date }, firstDayOfWeek)
    val byWeek = logs.groupBy { WeekGrouping.weekStart(it.date, firstDayOfWeek) }

    val weeks = mutableListOf<CalendarWeekSummary>()
    var weekStart = firstWeek
    while (!weekStart.isAfter(lastWeek)) {
        val weekLogs = byWeek[weekStart].orEmpty()
        val weights = weekLogs.mapNotNull { it.weightKg }
        val calories = weekLogs.mapNotNull { it.calories }
        weeks += CalendarWeekSummary(
            startDate = weekStart,
            endDate = weekStart.plusDays(6),
            avgWeightKg = weights.takeIf { it.isNotEmpty() }?.average(),
            avgCalories = calories.takeIf { it.isNotEmpty() }?.average(),
        )
        weekStart = weekStart.plusWeeks(1)
    }
    return weeks
}

@Composable
private fun WeekCard(week: CalendarWeekSummary, profile: UserProfile?) {
    val unit = profile?.weightUnit
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${week.startDate.format(dateFormatter)} - ${week.endDate.format(dateFormatter)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (week.avgWeightKg == null && week.avgCalories == null) {
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
            }
        }
    }
}
