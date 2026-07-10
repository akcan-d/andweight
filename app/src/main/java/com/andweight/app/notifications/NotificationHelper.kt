package com.andweight.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.andweight.app.MainActivity
import com.andweight.app.R

private const val CHANNEL_ID = "daily_reminder"
internal const val NOTIFICATION_ID = 1

/** Builds and shows the daily check-in reminder - a single, low-priority nudge, not something
 * that should feel urgent or naggy. Includes quick actions for whichever of weight/calories is
 * still missing, so the common case can be logged without opening the app at all. */
object NotificationHelper {

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily check-in reminder",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A once-a-day nudge to log your weight or meals, if you haven't already."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun showReminder(context: Context, needsWeight: Boolean, needsCalories: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time for your check-in")
            .setContentText("Log today's weight or meals to keep your estimate accurate.")
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (needsWeight) {
            builder.addAction(weightReplyAction(context))
        }
        if (needsCalories) {
            builder.addAction(usualCaloriesAction(context))
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun weightReplyAction(context: Context): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(EXTRA_WEIGHT_REPLY).setLabel("Today's weight").build()
        val intent = Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_LOG_WEIGHT)
        // RemoteInput delivery requires a mutable PendingIntent - the system fills in the reply text.
        val pendingIntent = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        return NotificationCompat.Action.Builder(R.drawable.ic_launcher_foreground, "Log weight", pendingIntent)
            .addRemoteInput(remoteInput)
            .build()
    }

    private fun usualCaloriesAction(context: Context): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_LOG_USUAL_CALORIES)
        val pendingIntent = PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Action.Builder(R.drawable.ic_launcher_foreground, "Ate usual amount", pendingIntent).build()
    }
}
