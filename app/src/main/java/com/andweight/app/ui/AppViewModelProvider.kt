package com.andweight.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andweight.app.AndWeightApplication
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.ui.history.HistoryViewModel
import com.andweight.app.ui.onboarding.OnboardingViewModel
import com.andweight.app.ui.today.TodayViewModel

/**
 * Simple manual dependency injection: every ViewModel takes the single shared
 * repository from the Application instance. No DI framework needed for an app
 * this size.
 */
object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { OnboardingViewModel(repository()) }
        initializer { TodayViewModel(repository()) }
        initializer { HistoryViewModel(repository()) }
    }
}

private fun CreationExtras.repository(): AndWeightRepository {
    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AndWeightApplication
    return app.repository
}
