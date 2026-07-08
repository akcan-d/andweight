package com.andweight.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.andweight.app.ui.history.HistoryScreen
import com.andweight.app.ui.today.TodayScreen

private const val TODAY_ROUTE = "today"
private const val HISTORY_ROUTE = "history"

@Composable
fun MainScreen() {
    val navController = rememberNavController()

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
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    label = { Text("History") },
                )
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = TODAY_ROUTE) {
            composable(TODAY_ROUTE) { TodayScreen(contentPadding = padding) }
            composable(HISTORY_ROUTE) { HistoryScreen(contentPadding = padding) }
        }
    }
}

private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
