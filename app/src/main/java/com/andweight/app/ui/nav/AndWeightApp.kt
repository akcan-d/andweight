package com.andweight.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.andweight.app.AndWeightApplication
import com.andweight.app.ui.onboarding.OnboardingScreen

private const val ROUTER_ROUTE = "router"
private const val ONBOARDING_ROUTE = "onboarding"
private const val MAIN_ROUTE = "main"

@Composable
fun AndWeightApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTER_ROUTE) {
        composable(ROUTER_ROUTE) {
            RouterScreen(
                onNeedsOnboarding = {
                    navController.navigate(ONBOARDING_ROUTE) {
                        popUpTo(ROUTER_ROUTE) { inclusive = true }
                    }
                },
                onHasProfile = {
                    navController.navigate(MAIN_ROUTE) {
                        popUpTo(ROUTER_ROUTE) { inclusive = true }
                    }
                },
            )
        }
        composable(ONBOARDING_ROUTE) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(MAIN_ROUTE) {
                        popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                    }
                },
            )
        }
        composable(MAIN_ROUTE) { MainScreen() }
    }
}

@Composable
private fun RouterScreen(onNeedsOnboarding: () -> Unit, onHasProfile: () -> Unit) {
    val context = LocalContext.current
    var checked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val app = context.applicationContext as AndWeightApplication
        val profile = app.repository.getProfileOnce()
        checked = true
        if (profile == null) onNeedsOnboarding() else onHasProfile()
    }

    if (!checked) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
