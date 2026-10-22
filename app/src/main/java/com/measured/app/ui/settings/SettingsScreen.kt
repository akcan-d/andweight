package com.measured.app.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.outlined.Tonality
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.measured.app.BuildConfig
import com.measured.app.domain.AccentTheme
import com.measured.app.domain.BackupSerializer
import com.measured.app.domain.MealTimingMode
import com.measured.app.domain.ThemeMode
import com.measured.app.domain.WeightUnit
import com.measured.app.ui.AppViewModelProvider
import com.measured.app.ui.common.InfoTooltipIcon
import com.measured.app.ui.common.SupporterOnlyDialog
import com.measured.app.ui.theme.accentPaletteFor
import com.measured.app.ui.theme.themedFilterChipColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val timeFormatter12h = DateTimeFormatter.ofPattern("h:mm a")
private val timeFormatter24h = DateTimeFormatter.ofPattern("HH:mm")
private const val PRIVACY_POLICY_URL = "https://akcan-d.github.io/andweight/privacy-policy.html"
private const val FEEDBACK_EMAIL = "andweight.app@gmail.com"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onViewAllTips: () -> Unit,
    onShowTutorial: () -> Unit,
    onEditProfile: () -> Unit,
    onShowTdeeInfo: () -> Unit,
    onShowSupporter: () -> Unit,
    onDataErased: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val tipsEnabled by viewModel.tipsEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val reminderEnabled by viewModel.reminderEnabled.collectAsState()
    val reminderTime by viewModel.reminderTime.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val isSupporter by viewModel.isSupporter.collectAsState()
    val accentTheme by viewModel.accentTheme.collectAsState()
    val highContrast by viewModel.highContrast.collectAsState()
    val mealTimingMode by viewModel.mealTimingMode.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel.didEraseData) {
        if (viewModel.didEraseData) onDataErased()
    }

    // Holds the fetched summary while a supporter is choosing a design - non-supporters never
    // see this, since the share button sends them straight to ShareImageRenderer.share().
    var pendingShareSummary by remember { mutableStateOf<SettingsViewModel.ShareSummary?>(null) }
    var showSupporterLockedDialog by remember { mutableStateOf(false) }

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
        // Every other section here has its own heading - these two buttons were the one thing
        // that didn't, which read as adrift rather than intentional.
        Text("Your profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onEditProfile, modifier = Modifier.weight(1f)) {
                Text("Edit profile")
            }
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        pendingShareSummary = viewModel.buildShareSummary() ?: return@launch
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Share progress")
            }
        }

        pendingShareSummary?.let { summary ->
            ShareDesignPickerDialog(
                summary = summary,
                backgroundColor = accentPaletteFor(accentTheme, darkTheme = false).primary.toArgb(),
                isSupporter = isSupporter,
                onDesignSelected = { design ->
                    pendingShareSummary = null
                    val bitmap = ShareImageRenderer.render(
                        context,
                        summary,
                        accentPaletteFor(accentTheme, darkTheme = false).primary.toArgb(),
                        design,
                    )
                    ShareImageRenderer.share(context, bitmap)
                },
                onLockedDesignTapped = { showSupporterLockedDialog = true },
                onDismiss = { pendingShareSummary = null },
            )
        }

        if (showSupporterLockedDialog) {
            SupporterOnlyDialog(
                onDismiss = { showSupporterLockedDialog = false },
                onBecomeSupporter = {
                    showSupporterLockedDialog = false
                    viewModel.setSupporter(true)
                },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Just an entry point here - the full description, the toggle, and the perk list all
        // live on their own screen now, so a free user scrolling past this sees three lines
        // instead of a whole section's worth of chips and copy.
        Text("Support the app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (isSupporter) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Outlined.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
                Text("You're a supporter - thank you!", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(
                "A cheap one-time unlock adds a few extra perks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onShowSupporter, modifier = Modifier.fillMaxWidth()) {
            Text(if (isSupporter) "Manage" else "Learn more")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Units", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeightUnit.entries.forEach { unit ->
                FilterChip(
                    selected = profile?.weightUnit == unit,
                    onClick = { viewModel.setWeightUnit(unit) },
                    label = { Text(if (unit == WeightUnit.KG) "Metric (kg, cm)" else "Imperial (lb, ft/in)") },
                    colors = themedFilterChipColors(),
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Check-in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            InfoTooltipIcon(
                title = "Yesterday's meals vs. today's",
                text = "If you weigh in in the morning, asking about yesterday's meals makes more sense - " +
                    "by then yesterday's eating is finished and known for certain, where today's isn't yet. " +
                    "If you weigh in in the evening or before bed, after you've already finished eating for " +
                    "the day, asking about today's meals instead skips the extra day of lag.",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealTimingMode.entries.forEach { mode ->
                FilterChip(
                    selected = mealTimingMode == mode,
                    onClick = { viewModel.setMealTimingMode(mode) },
                    label = { Text(mode.label) },
                    colors = themedFilterChipColors(),
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    label = { Text(mode.label) },
                    colors = themedFilterChipColors(),
                )
            }
        }

        Text("Color theme", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentTheme.entries.forEach { theme ->
                val locked = theme.isSupporterOnly && !isSupporter
                FilterChip(
                    selected = accentTheme == theme,
                    onClick = { if (locked) showSupporterLockedDialog = true else viewModel.setAccentTheme(theme) },
                    label = { Text(theme.label) },
                    leadingIcon = if (locked) {
                        { Icon(Icons.Outlined.Lock, contentDescription = "Supporter only", modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    },
                    colors = themedFilterChipColors(),
                )
            }
        }
        // Only shown when there's actually something to sync - the icon's real, persisted
        // component state is the source of truth for "current", not a separately-tracked value
        // that could drift out of sync with it.
        if (!AppIconSwitcher.isAlreadyApplied(context, accentTheme)) {
            OutlinedButton(onClick = viewModel::syncIconToTheme) {
                Text("Sync icon")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = highContrast, onValueChange = viewModel::setHighContrast, role = Role.Switch),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Tonality, contentDescription = null)
                Text("High contrast")
            }
            Switch(checked = highContrast, onCheckedChange = null)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ReminderSection(
            reminderEnabled = reminderEnabled,
            reminderTime = reminderTime,
            isMetric = profile?.weightUnit?.isMetric ?: true,
            onEnabledChange = viewModel::setReminderEnabled,
            onTimeChange = viewModel::setReminderTime,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Tips", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = tipsEnabled,
                    onValueChange = viewModel::setTipsEnabled,
                    role = Role.Switch,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.TipsAndUpdates, contentDescription = null)
                Text("Daily tip")
            }
            Switch(checked = tipsEnabled, onCheckedChange = null)
        }
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onViewAllTips) {
                Text("View all tips")
            }
            TextButton(onClick = viewModel::resetTipHistory) {
                Text("Reset tip history")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Help", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = onShowTutorial, modifier = Modifier.fillMaxWidth()) {
            Text("Show the tutorial again")
        }
        OutlinedButton(onClick = onShowTdeeInfo, modifier = Modifier.fillMaxWidth()) {
            Text("How your estimate works")
        }
        OutlinedButton(onClick = { sendFeedback(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Report a bug or send feedback")
        }
        TextButton(onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }) {
            Text("Privacy policy")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        BackupRestoreSection(onBuildBackup = viewModel::buildFullBackup, onRestoreBackup = viewModel::restoreFullBackup)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        EraseDataSection(onErase = viewModel::eraseAllData)
    }
}

/** Only ever shown to supporters - everyone else's share button skips straight to sharing the
 * default design, so this is the one and only place the choice itself exists. */
@Composable
private fun ShareDesignPickerDialog(
    summary: SettingsViewModel.ShareSummary,
    backgroundColor: Int,
    isSupporter: Boolean,
    onDesignSelected: (ShareCardDesign) -> Unit,
    onLockedDesignTapped: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a design") },
        text = {
            // All designs stay visible even for a free user - a locked one shown as locked is
            // discoverable; one left out of the picker entirely isn't.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShareCardDesign.entries.forEach { design ->
                    val locked = design.isSupporterOnly && !isSupporter
                    val bitmap = ShareImageRenderer.render(context, summary, backgroundColor, design)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { if (locked) onLockedDesignTapped() else onDesignSelected(design) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = if (locked) "${design.label} - supporters only" else design.label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.small)
                                    .alpha(if (locked) 0.4f else 1f),
                            )
                            if (locked) {
                                Icon(
                                    Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Center).size(20.dp),
                                )
                            }
                        }
                        Text(design.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun BackupRestoreSection(
    onBuildBackup: suspend () -> String,
    onRestoreBackup: suspend (String) -> BackupSerializer.BackupData?,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var restoreResult by remember { mutableStateOf<BackupSerializer.BackupData?>(null) }
    var restoreFailed by remember { mutableStateOf(false) }
    var pendingRestoreText by remember { mutableStateOf<String?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val backup = onBuildBackup()
            context.contentResolver.openOutputStream(uri)?.use { it.write(backup.toByteArray()) }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            // Confirmed before actually restoring, rather than applying it immediately - unlike
            // onboarding's restore, there's real data on this device that the file could affect.
            if (text != null) pendingRestoreText = text
        }
    }

    Text("Backup & restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        "Save or restore everything in one file.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = { createDocumentLauncher.launch("measured_backup_${LocalDate.now()}.txt") },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Create a full backup")
    }
    OutlinedButton(
        onClick = { openDocumentLauncher.launch("*/*") },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Restore from a backup")
    }

    pendingRestoreText?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingRestoreText = null },
            title = { Text("Restore from this backup?") },
            text = {
                Text(
                    "Your profile and preferences will be replaced with what's in the file. Any day " +
                        "the file also has an entry for will be overwritten too - but only for the " +
                        "fields the file actually has for that day, so a day logged more fully on " +
                        "this device than in the file keeps what the file doesn't cover. Days this " +
                        "device has that the file doesn't are left alone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreText = null
                    coroutineScope.launch {
                        val result = onRestoreBackup(text)
                        if (result == null) restoreFailed = true else restoreResult = result
                    }
                }) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreText = null }) { Text("Cancel") }
            },
        )
    }

    restoreResult?.let { result ->
        AlertDialog(
            onDismissRequest = { restoreResult = null },
            title = { Text("Restore complete") },
            text = {
                val dayWord = if (result.logs.size == 1) "day" else "days"
                val parts = mutableListOf<String>()
                if (result.profile != null) parts += "your profile"
                parts += "${result.logs.size} $dayWord"
                if (result.settings != null) parts += "your preferences"
                Text("Restored ${parts.joinToString(", ")}.")
            },
            confirmButton = {
                TextButton(onClick = { restoreResult = null }) { Text("OK") }
            },
        )
    }

    if (restoreFailed) {
        AlertDialog(
            onDismissRequest = { restoreFailed = false },
            title = { Text("Couldn't read that file") },
            text = { Text("That doesn't look like a Measured backup file.") },
            confirmButton = {
                TextButton(onClick = { restoreFailed = false }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun ReminderSection(
    reminderEnabled: Boolean,
    reminderTime: Pair<Int, Int>,
    isMetric: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onEnabledChange(true)
    }
    var showTimePicker by remember { mutableStateOf(false) }

    val onReminderToggle: (Boolean) -> Unit = { checked ->
        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onEnabledChange(checked)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = reminderEnabled, onValueChange = onReminderToggle, role = Role.Switch),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Notifications, contentDescription = null)
            Text("Daily reminder")
        }
        Switch(checked = reminderEnabled, onCheckedChange = null)
    }

    if (reminderEnabled) {
        val (hour, minute) = reminderTime
        val timeFormatter = if (isMetric) timeFormatter24h else timeFormatter12h
        TextButton(onClick = { showTimePicker = true }) {
            Text("Reminder time: ${LocalTime.of(hour, minute).format(timeFormatter)}")
        }

        // Some phones (Samsung, Xiaomi, OnePlus and others layer their own battery management on
        // top of stock Android's) will quietly kill the reminder unless the app is excluded from
        // battery optimization - AlarmManager alone can't get around that, so the only real fix
        // is asking the person to allow it. Granting that happens in a separate system Settings
        // screen, so this needs to actively recheck on resume rather than once - otherwise the
        // warning never clears even after the person turns the setting on.
        var ignoringBatteryOptimizations by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        if (!ignoringBatteryOptimizations) {
            Text(
                "Some phones delay or block reminders unless the app is excluded from battery optimization.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                val intent = Intent(
                    AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                )
                context.startActivity(intent)
            }) {
                Text("Allow reminders to run in the background")
            }
        }
    }

    if (showTimePicker) {
        ReminderTimePickerDialog(
            initialHour = reminderTime.first,
            initialMinute = reminderTime.second,
            is24Hour = isMetric,
            onConfirm = { hour, minute ->
                onTimeChange(hour, minute)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
}

/** mailto: via ACTION_SENDTO rather than ACTION_SEND, so only email apps can handle it, pre-filled
 * with the device/app details that make a bug report actually useful without the person having to
 * think to include them. */
private fun sendFeedback(context: Context) {
    val body = "\n\n---\n" +
        "Measured ${BuildConfig.VERSION_NAME}\n" +
        "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
        "${Build.MANUFACTURER} ${Build.MODEL}"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "Measured feedback")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No email app found - you can reach me at $FEEDBACK_EMAIL", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = is24Hour)

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
