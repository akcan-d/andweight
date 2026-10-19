package com.andweight.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andweight.app.AndWeightApplication
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.data.SettingsRepository
import com.andweight.app.ui.journey.JourneyViewModel
import com.andweight.app.ui.journey.MilestoneUnlockViewModel
import com.andweight.app.ui.onboarding.OnboardingViewModel
import com.andweight.app.ui.settings.EditProfileViewModel
import com.andweight.app.ui.settings.ReminderOptInViewModel
import com.andweight.app.ui.settings.SettingsViewModel
import com.andweight.app.ui.settings.SupporterPromptViewModel
import com.andweight.app.ui.settings.TipOfTheDayViewModel
import com.andweight.app.ui.tips.TipsViewModel
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
        initializer { JourneyViewModel(repository(), settingsRepository()) }
        initializer { SettingsViewModel(settingsRepository(), repository(), application()) }
        initializer { TipOfTheDayViewModel(settingsRepository()) }
        initializer { TipsViewModel(settingsRepository()) }
        initializer { EditProfileViewModel(repository()) }
        initializer { ReminderOptInViewModel(settingsRepository(), repository(), application()) }
        initializer { SupporterPromptViewModel(repository(), settingsRepository()) }
        initializer { MilestoneUnlockViewModel(repository(), settingsRepository()) }
    }
}

private fun CreationExtras.repository(): AndWeightRepository {
    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AndWeightApplication
    return app.repository
}

private fun CreationExtras.settingsRepository(): SettingsRepository {
    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AndWeightApplication
    return app.settingsRepository
}

private fun CreationExtras.application(): AndWeightApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AndWeightApplication
