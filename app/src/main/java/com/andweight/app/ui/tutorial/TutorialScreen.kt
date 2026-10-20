package com.andweight.app.ui.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class TutorialPage(val icon: ImageVector, val title: String, val body: String)

private val pages = listOf(
    TutorialPage(
        Icons.Outlined.TaskAlt,
        "Forget logging every meal",
        "Most calorie apps ask you to weigh every ingredient and log every bite. AndWeight just " +
            "needs your weight and a quick sense of how much you ate - a couple of taps, once a day.",
    ),
    TutorialPage(
        Icons.Outlined.FavoriteBorder,
        "This is about health, not just a number",
        "The scale doesn't tell the whole story - muscle mass and body composition matter just as " +
            "much as weight, and AndWeight won't push you to lose faster than is healthy. If you " +
            "have any history of disordered eating, please talk to a doctor before using a tool " +
            "like this one.",
    ),
    TutorialPage(
        Icons.Outlined.Insights,
        "It learns your real metabolism",
        "Everyone's calorie needs are a little different, and no formula gets it exactly right. " +
            "AndWeight watches what actually happens to your weight and adjusts its estimate to match " +
            "your body, not just a textbook average.",
    ),
    TutorialPage(
        Icons.AutoMirrored.Outlined.TrendingUp,
        "It gets more accurate over time",
        "The first few days are just a starting guess. The longer you check in, the more the app " +
            "refines what it knows about you - so stick with it, and it'll pay off.",
    ),
    TutorialPage(
        Icons.Outlined.CalendarMonth,
        "Consistency beats precision",
        "You don't need to be exact. Missed a day? No problem - AndWeight carries on smoothly. " +
            "Showing up regularly matters far more than getting every number perfect.",
    ),
)

@Composable
fun TutorialScreen(onDone: () -> Unit, contentPadding: PaddingValues = PaddingValues()) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            TutorialPageContent(pages[index])
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageIndicator(count = pages.size, selectedIndex = pagerState.currentPage)

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
                    if (isLastPage) {
                        onDone()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                }) {
                    Text(if (isLastPage) "Let's get started" else "Next")
                }
            }
        }
    }
}

@Composable
private fun TutorialPageContent(page: TutorialPage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(page.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
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
