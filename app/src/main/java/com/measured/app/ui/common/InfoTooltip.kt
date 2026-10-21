package com.measured.app.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A small "i" icon that opens a plain-language explanation on tap - lets a label stay short on
 * screen while still having somewhere to put the longer explanation, instead of the label itself
 * growing a paragraph or the explanation living only in a tip the person may never read.
 */
@Composable
fun InfoTooltipIcon(title: String, text: String, modifier: Modifier = Modifier) {
    var showInfo by remember { mutableStateOf(false) }

    IconButton(onClick = { showInfo = true }, modifier = modifier) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = "What does this mean?",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("Got it") }
            },
        )
    }
}
