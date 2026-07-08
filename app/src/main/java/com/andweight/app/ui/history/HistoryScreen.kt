package com.andweight.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andweight.app.domain.UserProfile
import com.andweight.app.domain.WeekSummary
import com.andweight.app.ui.AppViewModelProvider
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun HistoryScreen(
    contentPadding: PaddingValues,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val profile by viewModel.profile.collectAsState()
    val tdeeState by viewModel.tdeeState.collectAsState()
    val weeks = tdeeState?.weeklyHistory.orEmpty().reversed()

    if (weeks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No entries yet - check in from the Today tab to start building your history.",
                modifier = Modifier.padding(32.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(weeks) { week -> WeekCard(week, profile) }
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
