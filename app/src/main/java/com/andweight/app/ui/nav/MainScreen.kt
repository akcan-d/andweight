package com.andweight.app.ui.nav

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.andweight.app.ui.AppViewModelProvider
import com.andweight.app.ui.journey.JourneyScreen
import com.andweight.app.ui.settings.EditProfileScreen
import com.andweight.app.ui.settings.ReminderOptInViewModel
import com.andweight.app.ui.settings.SettingsScreen
import com.andweight.app.ui.settings.TdeeInfoScreen
import com.andweight.app.ui.settings.TipOfTheDayViewModel
import com.andweight.app.ui.tips.TipsScreen
import com.andweight.app.ui.today.TodayScreen
import com.andweight.app.ui.tutorial.TutorialScreen
import java.time.LocalDate

private const val TODAY_ROUTE_BASE = "today"
private const val TODAY_DATE_ARG = "date"
private const val TODAY_ROUTE_PATTERN = "today?date={date}"
private const val JOURNEY_ROUTE = "journey"
private const val SETTINGS_ROUTE = "settings"
private const val SETTINGS_TIPS_ROUTE = "settings/tips"
private const val SETTINGS_TUTORIAL_ROUTE = "settings/tutorial"
private const val SETTINGS_EDIT_PROFILE_ROUTE = "settings/profile"
private const val SETTINGS_TDEE_INFO_ROUTE = "settings/tdee-info"

@Composable
fun MainScreen(onDataErased: () -> Unit) {
    val navController = rememberNavController()

    PopupHost()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination

            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute?.hierarchy?.any { it.route == TODAY_ROUTE_PATTERN } == true,
                    onClick = { navController.navigateToToday(null) },
                    icon = { Icon(Icons.Filled.Today, contentDescription = null) },
                    label = { Text("Status") },
                )
                NavigationBarItem(
                    selected = currentRoute?.hierarchy?.any { it.route == JOURNEY_ROUTE } == true,
                    onClick = { navController.navigateToTab(JOURNEY_ROUTE) },
                    icon = { Icon(Icons.Filled.EmojiEvents, contentDescription = null) },
                    label = { Text("Journey") },
                )
                NavigationBarItem(
                    selected = currentRoute?.hierarchy?.any { it.route == SETTINGS_ROUTE } == true,
                    onClick = { navController.navigateToTab(SETTINGS_ROUTE) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = TODAY_ROUTE_PATTERN) {
            composable(
                route = TODAY_ROUTE_PATTERN,
                arguments = listOf(navArgument(TODAY_DATE_ARG) { type = NavType.LongType; defaultValue = -1L }),
            ) { backStackEntry ->
                val epochDay = backStackEntry.arguments?.getLong(TODAY_DATE_ARG) ?: -1L
                TodayScreen(
                    contentPadding = padding,
                    onEditProfile = { navController.navigate(SETTINGS_EDIT_PROFILE_ROUTE) },
                    initialDate = if (epochDay >= 0) LocalDate.ofEpochDay(epochDay) else null,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(JOURNEY_ROUTE) {
                JourneyScreen(
                    contentPadding = padding,
                    onEditDay = { date -> navController.navigateToToday(date) },
                )
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    contentPadding = padding,
                    onViewAllTips = { navController.navigate(SETTINGS_TIPS_ROUTE) },
                    onShowTutorial = { navController.navigate(SETTINGS_TUTORIAL_ROUTE) },
                    onEditProfile = { navController.navigate(SETTINGS_EDIT_PROFILE_ROUTE) },
                    onShowTdeeInfo = { navController.navigate(SETTINGS_TDEE_INFO_ROUTE) },
                    onDataErased = onDataErased,
                )
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
        }
    }
}

/** At most one of these shows at a time - if both would fire in the same session, the tip takes
 * priority and the reminder prompt simply appears on the next recomposition once the tip is
 * dismissed, rather than stacking two dialogs on top of each other. */
@Composable
private fun PopupHost(
    tipViewModel: TipOfTheDayViewModel = viewModel(factory = AppViewModelProvider.Factory),
    reminderViewModel: ReminderOptInViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    when {
        tipViewModel.tipToShow != null -> TipOfTheDayPopup(tipViewModel)
        reminderViewModel.showPrompt -> ReminderOptInPopup(reminderViewModel)
    }
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
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = viewModel.dontShowAgain, onCheckedChange = { viewModel.onToggleDontShowAgain() })
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

private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Two different things share the Today destination, so they're deliberately handled with
 * different navigation semantics:
 * - null (the bottom-nav tab) uses the same save/restore-state tab-switch behavior as the other
 *   tabs, so returning to Today resumes wherever you left off.
 * - a specific date (the "edit this day" action from Journey) is a plain push instead - using
 *   restoreState here would risk restoring a *previously saved* Today instance and silently
 *   ignoring the newly requested date. A plain push always lands on exactly the requested day,
 *   and the system back button (or the header's back arrow) returns to Journey.
 */
private fun NavController.navigateToToday(date: LocalDate?) {
    if (date == null) {
        navigateToTab(TODAY_ROUTE_BASE)
    } else {
        navigate("$TODAY_ROUTE_BASE?$TODAY_DATE_ARG=${date.toEpochDay()}")
    }
}
