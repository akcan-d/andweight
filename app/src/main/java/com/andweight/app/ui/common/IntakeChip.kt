package com.andweight.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.andweight.app.domain.DailyLog
import kotlin.math.roundToInt

/**
 * Compact, non-judgmental rendering of a day's meal entry - a direction icon plus a short label
 * instead of a full sentence, and a single alpha-graded tint rather than red/green good/bad
 * coloring (this app deliberately avoids framing "ate more" as bad).
 */
@Composable
fun IntakeChip(log: DailyLog, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    when {
        log.wasExactEntry -> Text("Exact: ${log.calories?.roundToInt()} kcal", style = style)
        log.intakeLevel != null -> {
            val level = log.intakeLevel
            val icon = when {
                level.direction < 0 && level.intensity >= 2 -> Icons.Filled.KeyboardDoubleArrowDown
                level.direction < 0 -> Icons.AutoMirrored.Filled.TrendingDown
                level.direction > 0 && level.intensity >= 2 -> Icons.Filled.KeyboardDoubleArrowUp
                level.direction > 0 -> Icons.AutoMirrored.Filled.TrendingUp
                else -> Icons.AutoMirrored.Filled.TrendingFlat
            }
            val alpha = 0.55f + (level.intensity * 0.225f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha), modifier = androidx.compose.ui.Modifier.size(18.dp))
                Text(level.shortLabel, style = style)
            }
        }
        else -> Text("Not logged yet", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
