package com.andweight.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andweight.app.domain.ThemeMode
import com.andweight.app.ui.AppViewModelProvider
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onViewAllTips: () -> Unit,
    onShowTutorial: () -> Unit,
    onEditProfile: () -> Unit,
    onDataErased: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val tipsEnabled by viewModel.tipsEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val reminderEnabled by viewModel.reminderEnabled.collectAsState()
    val reminderTime by viewModel.reminderTime.collectAsState()

    LaunchedEffect(viewModel.didEraseData) {
        if (viewModel.didEraseData) onDataErased()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Update your goal, pace, height, age, activity level, units, or start date any time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
            Text("Edit profile")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    label = { Text(mode.label) },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ReminderSection(
            reminderEnabled = reminderEnabled,
            reminderTime = reminderTime,
            onEnabledChange = viewModel::setReminderEnabled,
            onTimeChange = viewModel::setReminderTime,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Tips", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = tipsEnabled, onCheckedChange = viewModel::setTipsEnabled)
            Text("Show a daily tip when I open the app")
        }
        TextButton(onClick = onViewAllTips) {
            Text("View all tips")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Help", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = onShowTutorial, modifier = Modifier.fillMaxWidth()) {
            Text("Show the tutorial again")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        EraseDataSection(onErase = viewModel::eraseAllData)
    }
}

@Composable
private fun ReminderSection(
    reminderEnabled: Boolean,
    reminderTime: Pair<Int, Int>,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onEnabledChange(true)
    }
    var showTimePicker by remember { mutableStateOf(false) }

    Text("Daily reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Remind me to check in if I haven't yet")
        Switch(
            checked = reminderEnabled,
            onCheckedChange = { checked ->
                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onEnabledChange(checked)
                }
            },
        )
    }

    if (reminderEnabled) {
        val (hour, minute) = reminderTime
        TextButton(onClick = { showTimePicker = true }) {
            Text("Reminder time: ${LocalTime.of(hour, minute).format(timeFormatter)}")
        }
    }

    if (showTimePicker) {
        ReminderTimePickerDialog(
            initialHour = reminderTime.first,
            initialMinute = reminderTime.second,
            onConfirm = { hour, minute ->
                onTimeChange(hour, minute)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder time") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EraseDataSection(onErase: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    Text("Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    TextButton(
        onClick = { showConfirm = true },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text("Erase all data and start over")
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Erase everything?") },
            text = {
                Text(
                    "This permanently deletes your profile and every day you've logged, and takes you back " +
                        "through setup from scratch. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onErase()
                }) {
                    Text("Erase everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
