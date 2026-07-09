package com.andweight.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andweight.app.domain.DailyLog
import com.andweight.app.domain.Phrasing
import com.andweight.app.domain.UserProfile
import com.andweight.app.domain.WeekSummary
import com.andweight.app.ui.AppViewModelProvider
import com.andweight.app.ui.common.IntakeChip
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d")

private enum class HistoryViewMode(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly trend"),
}

@Composable
fun HistoryScreen(
    contentPadding: PaddingValues,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val profile by viewModel.profile.collectAsState()
    val tdeeState by viewModel.tdeeState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    var viewMode by remember { mutableStateOf(HistoryViewMode.DAILY) }

    val hasAnyData = logs.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        if (!hasAnyData) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No entries yet - check in from the Today tab to start building your stats.",
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
            item { WeightTrendCard(logs, profile) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryViewMode.entries.forEach { mode ->
                        FilterChip(
                            selected = viewMode == mode,
                            onClick = { viewMode = mode },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }

            when (viewMode) {
                HistoryViewMode.DAILY -> {
                    val sortedLogs = logs.sortedByDescending { it.date }
                    items(sortedLogs) { log -> DailyCard(log, profile, onDelete = { viewModel.deleteLog(log.date) }) }
                }
                HistoryViewMode.WEEKLY -> {
                    val weeks = tdeeState?.weeklyHistory.orEmpty().reversed()
                    items(weeks) { week -> WeekCard(week, profile) }
                }
            }
        }
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
private fun DailyCard(log: DailyLog, profile: UserProfile?, onDelete: () -> Unit) {
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
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete this day", tint = MaterialTheme.colorScheme.error)
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
