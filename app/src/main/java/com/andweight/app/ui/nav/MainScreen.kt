package com.andweight.app.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.andweight.app.ui.AppViewModelProvider
import com.andweight.app.ui.history.HistoryScreen
import com.andweight.app.ui.settings.EditProfileScreen
import com.andweight.app.ui.settings.SettingsScreen
import com.andweight.app.ui.settings.TipOfTheDayViewModel
import com.andweight.app.ui.tips.TipsScreen
import com.andweight.app.ui.today.TodayScreen
import com.andweight.app.ui.tutorial.TutorialScreen

private const val TODAY_ROUTE = "today"
private const val HISTORY_ROUTE = "history"
private const val SETTINGS_ROUTE = "settings"
private const val SETTINGS_TIPS_ROUTE = "settings/tips"
private const val SETTINGS_TUTORIAL_ROUTE = "settings/tutorial"
private const val SETTINGS_EDIT_PROFILE_ROUTE = "settings/profile"

@Composable
fun MainScreen(onDataErased: () -> Unit) {
    val navController = rememberNavController()

    TipOfTheDayPopup()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination

            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute?.hierarchy?.any { it.route == TODAY_ROUTE } == true,
                    onClick = { navController.navigateToTab(TODAY_ROUTE) },
                    icon = { Icon(Icons.Filled.Today, contentDescription = null) },
                    label = { Text("Today") },
                )
                NavigationBarItem(
                    selected = currentRoute?.hierarchy?.any { it.route == HISTORY_ROUTE } == true,
                    onClick = { navController.navigateToTab(HISTORY_ROUTE) },
                    icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null) },
                    label = { Text("Stats") },
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
        NavHost(navController = navController, startDestination = TODAY_ROUTE) {
            composable(TODAY_ROUTE) {
                TodayScreen(
                    contentPadding = padding,
                    onEditProfile = { navController.navigate(SETTINGS_EDIT_PROFILE_ROUTE) },
                )
            }
            composable(HISTORY_ROUTE) { HistoryScreen(contentPadding = padding) }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    contentPadding = padding,
                    onViewAllTips = { navController.navigate(SETTINGS_TIPS_ROUTE) },
                    onShowTutorial = { navController.navigate(SETTINGS_TUTORIAL_ROUTE) },
                    onEditProfile = { navController.navigate(SETTINGS_EDIT_PROFILE_ROUTE) },
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
        }
    }
}

@Composable
private fun TipOfTheDayPopup(viewModel: TipOfTheDayViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
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

private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
