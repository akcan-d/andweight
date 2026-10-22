package com.measured.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.measured.app.AndWeightApplication
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
                    // Weight is logged for today, but meal info is always reported about the day
                    // before (see TodayViewModel.mealDate) - so "complete" means today's weight
                    // and yesterday's calories, two different rows, not one row with both fields.
                    val weightLogged = app.repository.getLogForDate(today)?.weightKg != null
                    val mealLogged = app.repository.getLogForDate(today.minusDays(1))?.calories != null
                    if (!weightLogged || !mealLogged) {
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
