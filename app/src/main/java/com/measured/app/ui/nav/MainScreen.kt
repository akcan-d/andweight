package com.measured.app.ui.nav

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.measured.app.ui.AppViewModelProvider
import com.measured.app.ui.journey.JourneyHistoryScreen
import com.measured.app.ui.journey.JourneyScreen
import com.measured.app.ui.journey.MilestoneUnlockViewModel
import com.measured.app.ui.journey.MilestonesScreen
import com.measured.app.ui.journey.ResearchScreen
import com.measured.app.ui.settings.EditProfileScreen
import com.measured.app.ui.settings.ReminderOptInViewModel
import com.measured.app.ui.settings.SettingsScreen
import com.measured.app.ui.settings.SupporterPromptViewModel
import com.measured.app.ui.settings.SupporterScreen
import com.measured.app.ui.settings.TdeeInfoScreen
import com.measured.app.ui.settings.TipOfTheDayViewModel
import com.measured.app.ui.tips.TipsScreen
import com.measured.app.ui.today.CheckInReminderViewModel
import com.measured.app.ui.today.TodayScreen
import com.measured.app.ui.tutorial.TutorialScreen
import java.time.LocalDate
import kotlinx.coroutines.launch

private const val MAIN_TABS_ROUTE = "main_tabs"
private const val SETTINGS_TIPS_ROUTE = "settings/tips"
private const val SETTINGS_TUTORIAL_ROUTE = "settings/tutorial"
private const val SETTINGS_EDIT_PROFILE_ROUTE = "settings/profile"
private const val SETTINGS_TDEE_INFO_ROUTE = "settings/tdee-info"
private const val SETTINGS_SUPPORTER_ROUTE = "settings/supporter"
private const val JOURNEY_MILESTONES_ROUTE = "journey/milestones"
private const val JOURNEY_RESEARCH_ROUTE = "journey/research"
private const val JOURNEY_HISTORY_ROUTE = "journey/history"

private const val TAB_STATUS = 0
private const val TAB_JOURNEY = 1
private const val TAB_SETTINGS = 2

@Composable
fun MainScreen(onDataErased: () -> Unit) {
    val navController = rememberNavController()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    // Set by Journey's "edit this day" action and consumed by the Status tab the moment it's
    // composed after the switch - a plain nav argument would fight with the pager's own state
    // retention, so the date travels alongside navigation rather than inside it.
    var pendingEditDate by remember { mutableStateOf<LocalDate?>(null) }

    fun goToTab(tab: Int) {
        val backStackEntry = navController.currentBackStackEntry
        if (backStackEntry?.destination?.route != MAIN_TABS_ROUTE) {
            navController.popBackStack(MAIN_TABS_ROUTE, inclusive = false)
        }
        scope.launch { pagerState.animateScrollToPage(tab) }
    }

    PopupHost()

    // A brief, dismissible notification rather than a dialog - reaching a milestone is good news
    // to glance at, not something that needs an explicit "OK".
    val milestoneViewModel: MilestoneUnlockViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val checkInReminderViewModel: CheckInReminderViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val showCheckInBadge by checkInReminderViewModel.showBadge.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val milestoneMessage = milestoneViewModel.newMilestoneMessage
    LaunchedEffect(milestoneMessage) {
        if (milestoneMessage != null) {
            val result = snackbarHostState.showSnackbar(
                message = milestoneMessage,
                actionLabel = "View",
                duration = SnackbarDuration.Long,
            )
            milestoneViewModel.onMessageShown()
            if (result == SnackbarResult.ActionPerformed) {
                navController.navigate(JOURNEY_MILESTONES_ROUTE)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val onTabs = backStackEntry?.destination?.route == MAIN_TABS_ROUTE

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavPillItem(
                        selected = onTabs && pagerState.currentPage == TAB_STATUS,
                        icon = Icons.Outlined.Today,
                        label = "Status",
                        onClick = { goToTab(TAB_STATUS) },
                        showBadge = showCheckInBadge,
                    )
                    NavPillItem(
                        selected = onTabs && pagerState.currentPage == TAB_JOURNEY,
                        icon = Icons.Outlined.EmojiEvents,
                        label = "Journey",
                        onClick = { goToTab(TAB_JOURNEY) },
                    )
                    NavPillItem(
                        selected = onTabs && pagerState.currentPage == TAB_SETTINGS,
                        icon = Icons.Outlined.Settings,
                        label = "Settings",
                        onClick = { goToTab(TAB_SETTINGS) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = MAIN_TABS_ROUTE) {
            composable(MAIN_TABS_ROUTE) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        TAB_STATUS -> TodayScreen(
                            contentPadding = padding,
                            onEditProfile = { navController.navigate(SETTINGS_EDIT_PROFILE_ROUTE) },
                            initialDate = pendingEditDate,
                            onInitialDateConsumed = { pendingEditDate = null },
                        )
                        TAB_JOURNEY -> JourneyScreen(
                            contentPadding = padding,
                            onShowMilestones = { navController.navigate(JOURNEY_MILESTONES_ROUTE) },
                            onShowResearch = { navController.navigate(JOURNEY_RESEARCH_ROUTE) },
                            onShowHistory = { navController.navigate(JOURNEY_HISTORY_ROUTE) },
                        )
                        TAB_SETTINGS -> SettingsScreen(
                            contentPadding = padding,
                            onViewAllTips = { navController.navigate(SETTINGS_TIPS_ROUTE) },
                            onShowTutorial = { navController.navigate(SETTINGS_TUTORIAL_ROUTE) },
                            onEditProfile = { navController.navigate(SETTINGS_EDIT_PROFILE_ROUTE) },
                            onShowTdeeInfo = { navController.navigate(SETTINGS_TDEE_INFO_ROUTE) },
                            onShowSupporter = { navController.navigate(SETTINGS_SUPPORTER_ROUTE) },
                            onDataErased = onDataErased,
                        )
                    }
                }
            }
            composable(SETTINGS_TIPS_ROUTE) {
                TipsScreen(contentPadding = padding, onBack = { navController.popBackStack() })
            }
            composable(SETTINGS_TUTORIAL_ROUTE) {
                TutorialScreen(contentPadding = padding, onDone = { navController.popBackStack() })
            }
            composable(SETTINGS_EDIT_PROFILE_ROUTE) {
                EditProfileScreen(contentPadding = padding, onBack = { navController.popBackStack() })
            }
            composable(SETTINGS_TDEE_INFO_ROUTE) {
                TdeeInfoScreen(contentPadding = padding, onBack = { navController.popBackStack() })
            }
            composable(SETTINGS_SUPPORTER_ROUTE) {
                SupporterScreen(contentPadding = padding, onBack = { navController.popBackStack() })
            }
            composable(JOURNEY_MILESTONES_ROUTE) {
                MilestonesScreen(contentPadding = padding, onBack = { navController.popBackStack() })
            }
            composable(JOURNEY_RESEARCH_ROUTE) {
                ResearchScreen(contentPadding = padding, onBack = { navController.popBackStack() })
            }
            composable(JOURNEY_HISTORY_ROUTE) {
                JourneyHistoryScreen(
                    contentPadding = padding,
                    onEditDay = { date ->
                        pendingEditDate = date
                        goToTab(TAB_STATUS)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/** A floating rounded pill rather than an edge-to-edge bar - the active tab gets a soft tinted
 * background instead of just a color change, matching the chip language used everywhere else. */
@Composable
private fun NavPillItem(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit, showBadge: Boolean = false) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // A plain theme-colored dot, not the error/red Badge default - this app avoids
        // red "something's wrong" framing for an ordinary daily reminder.
        BadgedBox(badge = { if (showBadge) Badge(containerColor = MaterialTheme.colorScheme.primary) }) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(21.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor, fontWeight = FontWeight.SemiBold)
    }
}

/** At most one of these shows at a time - if both would fire in the same session, the tip takes
 * priority and the reminder prompt simply appears on the next recomposition once the tip is
 * dismissed, rather than stacking two dialogs on top of each other. */
@Composable
private fun PopupHost(
    tipViewModel: TipOfTheDayViewModel = viewModel(factory = AppViewModelProvider.Factory),
    reminderViewModel: ReminderOptInViewModel = viewModel(factory = AppViewModelProvider.Factory),
    supporterPromptViewModel: SupporterPromptViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    when {
        tipViewModel.tipToShow != null -> TipOfTheDayPopup(tipViewModel)
        reminderViewModel.showPrompt -> ReminderOptInPopup(reminderViewModel)
        supporterPromptViewModel.showPrompt -> SupporterPromptPopup(supporterPromptViewModel)
    }
}

@Composable
private fun SupporterPromptPopup(viewModel: SupporterPromptViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::onDismiss,
        title = { Text("Enjoying Measured?") },
        text = {
            Text(
                "If it's been useful, a cheap one-time supporter unlock adds a couple of extra " +
                    "color themes and an extra chart - entirely optional, and you can always find " +
                    "it later under Settings.",
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel::onBecomeSupporter) {
                Text("Become a supporter")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onDismiss) {
                Text("Maybe later")
            }
        },
    )
}

@Composable
private fun TipOfTheDayPopup(viewModel: TipOfTheDayViewModel) {
    val tip = viewModel.tipToShow ?: return

    AlertDialog(
        onDismissRequest = viewModel::onDismiss,
        title = { Text(tip.title) },
        text = {
            Column {
                Text(tip.body)
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .toggleable(
                            value = viewModel.dontShowAgain,
                            onValueChange = { viewModel.onToggleDontShowAgain() },
                            role = Role.Checkbox,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = viewModel.dontShowAgain, onCheckedChange = null)
                    Text("Don't show these again")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::onDismiss) {
                Text("Got it")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderOptInPopup(viewModel: ReminderOptInViewModel) {
    val context = LocalContext.current
    val isMetric by viewModel.isMetric.collectAsState()
    val timePickerState = rememberTimePickerState(initialHour = 21, initialMinute = 0, is24Hour = isMetric)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Enable either way - if denied, the reminder is scheduled but silently won't show a
        // notification until permission is granted later from Settings, same as elsewhere in the app.
        viewModel.enableReminder(timePickerState.hour, timePickerState.minute)
    }

    AlertDialog(
        onDismissRequest = viewModel::declineReminder,
        title = { Text("Daily check-in reminder?") },
        text = {
            Column {
                Text("We can remind you once a day if you haven't logged your weight or meals yet - never more than once a day.")
                Row(modifier = Modifier.padding(top = 12.dp)) {
                    TimePicker(state = timePickerState)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.enableReminder(timePickerState.hour, timePickerState.minute)
                }
            }) {
                Text("Turn on reminders")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::declineReminder) {
                Text("No thanks")
            }
        },
    )
}
