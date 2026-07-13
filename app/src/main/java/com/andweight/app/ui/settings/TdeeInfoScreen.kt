package com.andweight.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class TdeeInfoSection(val title: String, val body: String)

private val SECTIONS = listOf(
    TdeeInfoSection(
        "What \"adaptive TDEE\" means",
        "TDEE (total daily energy expenditure) is how many calories your body actually burns in a " +
            "day - the number where eating exactly that much would keep your weight steady. \"Adaptive\" " +
            "means this app doesn't just calculate that number once and leave it fixed - it keeps " +
            "correcting it over time using your own real results, so it gets more accurate the longer " +
            "you use it.",
    ),
    TdeeInfoSection(
        "Where the starting number comes from",
        "Before you've logged anything, the app needs a first guess. If you've told it your sex, " +
            "height, and age, it uses the Mifflin-St Jeor equation - a widely used formula for " +
            "estimating resting metabolic rate - multiplied by an activity-level factor. Any of those " +
            "three details you leave out is filled in with a reasonable population-average value " +
            "instead, so partial information still helps. If none of them are provided at all, it " +
            "falls back to a much simpler rule of thumb based on body weight alone, matching the " +
            "original spreadsheet this app was built from.",
    ),
    TdeeInfoSection(
        "How it adapts",
        "Every week, the app compares what you actually ate to what actually happened to your " +
            "weight. If you ate less than your estimated maintenance calories and lost weight, or ate " +
            "more and gained, that tells the app something concrete: what your true maintenance " +
            "calories must have been to produce that real result. That weekly \"implied TDEE\" is " +
            "blended with up to the 5 previous weeks in a rolling average, so a single unusual week " +
            "(a big weekend, a bout of water retention) can't swing your target on its own - but a " +
            "sustained trend shows up clearly.",
    ),
    TdeeInfoSection(
        "Why this beats a fixed calculator",
        "Formulas like Mifflin-St Jeor are good starting points, but everyone's real metabolism " +
            "varies from the formula by a meaningful margin - two people with the same height, weight, " +
            "age, and sex can genuinely burn quite different amounts of energy. No formula can know " +
            "that in advance. Only your own logged results can reveal it, which is the whole idea " +
            "behind adapting the estimate over time instead of trusting a one-time calculation.",
    ),
    TdeeInfoSection(
        "What it needs from you",
        "The adaptive part only works with real data - the more consistently you log your weight and " +
            "what you ate, the faster and more accurately it converges on your true number. Gaps are " +
            "handled gracefully (a week with no entries just carries the previous estimate forward " +
            "unchanged), but frequent logging is what makes this genuinely better than a static " +
            "calculator, rather than just a fancier-looking one.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TdeeInfoScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How your estimate works") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            items(SECTIONS) { section ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(section.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
