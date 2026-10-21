package com.measured.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Shown when someone taps a locked, supporter-only control (a color theme, a chart style, a
 * share-card design) instead of leaving the tap a silent no-op - the point of showing locked
 * things at all is so they're discoverable, which only works if tapping one actually explains
 * why nothing happened.
 */
@Composable
fun SupporterOnlyDialog(onDismiss: () -> Unit, onBecomeSupporter: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supporters only") },
        text = {
            Text(
                "This one's part of the supporter unlock - a cheap one-time purchase that adds a " +
                    "few extra perks. You can find it anytime under Settings.",
            )
        },
        confirmButton = {
            TextButton(onClick = onBecomeSupporter) { Text("Become a supporter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Maybe later") }
        },
    )
}
