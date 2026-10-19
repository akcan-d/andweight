package com.andweight.app

import android.app.Application
import com.andweight.app.data.AndWeightRepository
import com.andweight.app.data.SettingsRepository
import com.andweight.app.notifications.NotificationHelper
import com.andweight.app.notifications.ReminderScheduler
import com.andweight.app.ui.settings.AppIconSwitcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AndWeightApplication : Application() {
    lateinit var repository: AndWeightRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AndWeightRepository(this)
        settingsRepository = SettingsRepository(this)

        NotificationHelper.createChannel(this)
        AppIconSwitcher.applyPendingDisables(this)

        // Defensive re-scheduling on every process start - covers the default-enabled first run
        // (nobody has touched the Settings toggle yet) and self-heals if an alarm was ever lost.
        CoroutineScope(Dispatchers.IO).launch {
            if (settingsRepository.isReminderEnabledOnce()) {
                val (hour, minute) = settingsRepository.getReminderTimeOnce()
                ReminderScheduler.schedule(this@AndWeightApplication, hour, minute)
            }
        }
    }
}
