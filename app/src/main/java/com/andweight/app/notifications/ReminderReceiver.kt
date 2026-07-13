package com.andweight.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.andweight.app.AndWeightApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Fires once at the scheduled time, shows the reminder if today isn't already fully logged, then
 * immediately schedules tomorrow's alarm - a self-rescheduling chain rather than a repeating
 * alarm, so it stays in sync if the reminder time changes in Settings. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as AndWeightApplication

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (app.settingsRepository.isReminderEnabledOnce()) {
                    val today = LocalDate.now()
                    val log = app.repository.getLogForDate(today)
                    val dayComplete = log?.weightKg != null && log.calories != null
                    if (!dayComplete) {
                        NotificationHelper.showReminder(context)
                    }
                    val (hour, minute) = app.settingsRepository.getReminderTimeOnce()
                    ReminderScheduler.schedule(context, hour, minute)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
