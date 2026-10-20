package com.measured.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.measured.app.MainActivity
import com.measured.app.R

private const val CHANNEL_ID = "daily_reminder"
private const val NOTIFICATION_ID = 1

/** Builds and shows the daily check-in reminder - a single, low-priority nudge, not something
 * that should feel urgent or naggy. */
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

    fun showReminder(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(brandedLargeIcon())
            .setContentTitle("Time for your check-in")
            .setContentText("Log today's weight or meals to keep your estimate accurate.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    // Some OEM skins (Samsung's One UI among them) auto-generate a round notification avatar by
    // cropping the app's adaptive launcher icon in a way that loses its background and crops the
    // ring glyph oddly, reading as a stray smudge rather than the logo. Setting an explicit large
    // icon - the same ring-and-dot mark drawn cleanly onto a solid circle - replaces that guess
    // with something that actually looks right.
    private fun brandedLargeIcon(): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2D7D55.toInt() }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, backgroundPaint)

        val inset = size * 0.27f
        val rect = RectF(inset, inset, size - inset, size - inset)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = size * 0.09f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(rect, -90f, 270f, false, ringPaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(rect.right, rect.top, size * 0.06f, dotPaint)

        return bitmap
    }
}
