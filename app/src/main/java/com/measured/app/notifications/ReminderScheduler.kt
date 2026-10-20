package com.measured.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Schedules (and re-schedules on each firing) the next one-shot daily reminder alarm, rather than
 * a repeating alarm - simpler to keep in sync whenever the user changes the reminder time. */
object ReminderScheduler {

    fun schedule(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAtMillis = nextTriggerMillis(hour, minute)
        val pendingIntent = reminderPendingIntent(context)

        // Deliberately inexact (no SCHEDULE_EXACT_ALARM) - a once-a-day nudge doesn't need
        // to-the-minute precision, and the exact-alarm permission draws extra Play Store review
        // scrutiny for apps that don't have a genuine alarm-clock/calendar use case.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(reminderPendingIntent(context))
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var trigger = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!trigger.isAfter(now)) {
            trigger = trigger.plusDays(1)
        }
        return trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
