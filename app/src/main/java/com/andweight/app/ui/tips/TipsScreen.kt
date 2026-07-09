package com.andweight.app.ui.tips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class Tip(val title: String, val body: String)

private val tips = listOf(
    Tip(
        "Log every day you can",
        "The more consistently you log your weight and how much you ate, the faster the app " +
            "locks onto your real metabolic rate - and the more you can trust what it tells you.",
    ),
    Tip(
        "Weigh in at the same time each day",
        "Try to step on the scale around the same time every day, ideally right after waking up " +
            "and before eating or drinking anything. This keeps your readings comparable day to day.",
    ),
    Tip(
        "It gets smarter the longer you use it",
        "This isn't a one-time calculation. Every entry refines the app's estimate of your " +
            "metabolism, so your numbers become more accurate the longer you stick with it.",
    ),
    Tip(
        "You don't need to be precise",
        "There's no need to count calories exactly. A quick, honest sense of how much you ate - " +
            "starving, average, stuffed - is all the app needs to work with.",
    ),
    Tip(
        "Be honest, not aspirational",
        "Log what you actually ate, not what you meant to eat. The app can only help you if what " +
            "you enter reflects reality, good day or bad.",
    ),
    Tip(
        "Missing a day here and there is fine",
        "Life happens. If you skip a day, the app carries your last known numbers forward and " +
            "keeps working - no need to panic or go back and fix anything.",
    ),
    Tip(
        "Forgot to log yesterday? Go back",
        "Use the arrows on the check-in card to step back to an earlier day and fill it in " +
            "whenever you remember. It's never too late to add a missing entry.",
    ),
    Tip(
        "Don't read too much into one day's weight",
        "Your weight can jump around from water retention, sodium, or hormones. Focus on the " +
            "weekly trend on the History tab rather than any single day's number.",
    ),
)

@Composable
fun TipsScreen(contentPadding: PaddingValues) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tips) { tip ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tip.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(tip.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
