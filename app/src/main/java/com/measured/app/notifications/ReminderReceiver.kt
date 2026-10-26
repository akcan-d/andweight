package com.measured.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.measured.app.AndWeightApplication
import com.measured.app.domain.CheckInStatus
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
                    // Which date meal info is reported about depends on the user's
                    // MealTimingMode setting (see TodayViewModel.mealDate) - same-day mode means
                    // today's weight and today's calories can be the same row.
                    val mode = app.settingsRepository.mealTimingModeOnce()
                    val mealDate = CheckInStatus.mealDateFor(today, mode)
                    val todayLog = app.repository.getLogForDate(today)
                    val mealLog = if (mealDate == today) todayLog else app.repository.getLogForDate(mealDate)
                    if (!CheckInStatus.isComplete(todayLog, mealLog)) {
                        NotificationHelper.showReminder(context, mode)
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
