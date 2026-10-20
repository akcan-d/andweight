package com.measured.app.ui.nav

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.measured.app.AndWeightApplication
import com.measured.app.domain.AccentTheme
import com.measured.app.domain.ThemeMode
import com.measured.app.ui.onboarding.OnboardingScreen
import com.measured.app.ui.theme.AndWeightTheme
import com.measured.app.ui.tutorial.TutorialScreen

private const val ROUTER_ROUTE = "router"
private const val TUTORIAL_ROUTE = "tutorial"
private const val ONBOARDING_ROUTE = "onboarding"
private const val MAIN_ROUTE = "main"

@Composable
fun AndWeightApp() {
    val context = LocalContext.current
    val app = context.applicationContext as AndWeightApplication
    val themeMode by app.settingsRepository.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val accentTheme by app.settingsRepository.accentThemeFlow.collectAsState(initial = AccentTheme.SAGE)
    val highContrast by app.settingsRepository.highContrastFlow.collectAsState(initial = false)
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    AndWeightTheme(darkTheme = useDarkTheme, accentTheme = accentTheme, highContrast = highContrast) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = ROUTER_ROUTE) {
                composable(ROUTER_ROUTE) {
                    RouterScreen(
                        onNeedsOnboarding = {
                            navController.navigate(TUTORIAL_ROUTE) {
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
                composable(TUTORIAL_ROUTE) {
                    Scaffold { padding ->
                        TutorialScreen(
                            contentPadding = padding,
                            onDone = {
                                navController.navigate(ONBOARDING_ROUTE) {
                                    popUpTo(TUTORIAL_ROUTE) { inclusive = true }
                                }
                            },
                        )
                    }
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
                composable(MAIN_ROUTE) {
                    MainScreen(
                        onDataErased = {
                            navController.navigate(ONBOARDING_ROUTE) {
                                popUpTo(MAIN_ROUTE) { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Whether a profile exists, or null while still checking. Kept as Compose state (rather than
 * navigating directly from inside the loading coroutine) so the actual `navigate()` call happens
 * from its own effect, restarted cleanly on recomposition - not chained directly onto the
 * continuation of a suspend database call, which is not guaranteed to resume on the main thread
 * and previously crashed Navigation's main-thread-only lifecycle check.
 */
@Composable
private fun RouterScreen(onNeedsOnboarding: () -> Unit, onHasProfile: () -> Unit) {
    val context = LocalContext.current
    var profileExists by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        val app = context.applicationContext as AndWeightApplication
        val profile = app.repository.getProfileOnce()
        profileExists = profile != null
    }

    LaunchedEffect(profileExists) {
        when (profileExists) {
            true -> onHasProfile()
            false -> onNeedsOnboarding()
            null -> Unit
        }
    }

    if (profileExists == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
