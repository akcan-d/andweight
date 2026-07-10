package com.andweight.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.andweight.app.AndWeightApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** AlarmManager alarms don't survive a reboot, so the daily reminder chain needs to be
 * re-established here if it was enabled. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val app = context.applicationContext as AndWeightApplication

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (app.settingsRepository.isReminderEnabledOnce()) {
                    val (hour, minute) = app.settingsRepository.getReminderTimeOnce()
                    ReminderScheduler.schedule(context, hour, minute)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
