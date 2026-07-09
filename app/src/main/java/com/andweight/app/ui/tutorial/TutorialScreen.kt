package com.andweight.app.ui.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class TutorialPage(val title: String, val body: String)

private val pages = listOf(
    TutorialPage(
        "Forget logging every meal",
        "Most calorie apps ask you to weigh every ingredient and log every bite. AndWeight just " +
            "needs your weight and a quick sense of how much you ate - a couple of taps, once a day.",
    ),
    TutorialPage(
        "This is about health, not just a number",
        "The scale doesn't tell the whole story - muscle mass and body composition matter just as " +
            "much as weight, and AndWeight won't push you to lose faster than is healthy. If you " +
            "have any history of disordered eating, please talk to a doctor before using a tool " +
            "like this one.",
    ),
    TutorialPage(
        "It learns your real metabolism",
        "Everyone's calorie needs are a little different, and no formula gets it exactly right. " +
            "AndWeight watches what actually happens to your weight and adjusts its estimate to match " +
            "your body, not just a textbook average.",
    ),
    TutorialPage(
        "It gets more accurate over time",
        "The first few days are just a starting guess. The longer you check in, the more the app " +
            "refines what it knows about you - so stick with it, and it'll pay off.",
    ),
    TutorialPage(
        "Consistency beats precision",
        "You don't need to be exact. Missed a day? No problem - AndWeight carries on smoothly. " +
            "Showing up regularly matters far more than getting every number perfect.",
    ),
)

@Composable
fun TutorialScreen(onDone: () -> Unit, contentPadding: PaddingValues = PaddingValues()) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = pages[pageIndex]
    val isLastPage = pageIndex == pages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(page.body, style = MaterialTheme.typography.bodyLarge)
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageIndicator(count = pages.size, selectedIndex = pageIndex)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isLastPage) Arrangement.End else Arrangement.SpaceBetween,
            ) {
                if (!isLastPage) {
                    TextButton(onClick = onDone) {
                        Text("Skip")
                    }
                }

                Button(onClick = {
                    if (isLastPage) onDone() else pageIndex++
                }) {
                    Text(if (isLastPage) "Let's get started" else "Next")
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(count: Int, selectedIndex: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val color = if (index == selectedIndex) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
