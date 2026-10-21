package com.measured.app.ui.journey

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.measured.app.domain.DailyLog
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
private val dayOfMonthFormatter = DateTimeFormatter.ofPattern("EEE, d")
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

private enum class JourneyViewMode(val label: String) {
    DAILY("Daily"),
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
    val tdeeState by viewModel.tdeeState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    var viewMode by remember { mutableStateOf(JourneyViewMode.DAILY) }

    // Grouped by month with one compact row per day. Only the most recent month starts expanded -
    // someone with a year of history opening this screen sees one open month plus a scannable
    // stack of collapsed headers, not every day they've ever logged at once.
    val monthGroups = logs.sortedByDescending { it.date }.groupBy { YearMonth.from(it.date) }
    val mostRecentMonth = monthGroups.keys.firstOrNull()
    var expandedMonth by remember(mostRecentMonth) { mutableStateOf(mostRecentMonth) }

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
                start = 20.dp,
                end = 20.dp,
                top = topBarPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                JourneyViewMode.DAILY -> {
                    monthGroups.forEach { (month, daysInMonth) ->
                        item(key = "month-$month") {
                            MonthHeader(
                                month = month,
                                dayCount = daysInMonth.size,
                                expanded = expandedMonth == month,
                                onToggle = { expandedMonth = if (expandedMonth == month) null else month },
                            )
                        }
                        if (expandedMonth == month) {
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
                }
                JourneyViewMode.WEEKLY -> {
                    val weeks = tdeeState?.weeklyHistory.orEmpty().reversed()
                    items(weeks) { week -> WeekCard(week, profile) }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, dayCount: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${month.format(monthFormatter)} ($dayCount)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
