package com.measured.app

import android.app.Application
import com.measured.app.data.AndWeightRepository
import com.measured.app.data.SettingsRepository
import com.measured.app.notifications.NotificationHelper
import com.measured.app.notifications.ReminderScheduler
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

        // Once per process start, not per screen visit - see SupporterPrompt, which needs this
        // install's actual age/use independent of how much logged history a backup restore
        // might bring with it.
        CoroutineScope(Dispatchers.IO).launch {
            settingsRepository.recordAppLaunchOnce()
        }

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
