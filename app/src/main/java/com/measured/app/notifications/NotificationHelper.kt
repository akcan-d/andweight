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

        // An old-style balance scale, matching ic_launcher_foreground.xml's proportions - beam,
        // post, foot, and two pointed hanging dishes.
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = size * 0.0593f
            strokeCap = Paint.Cap.ROUND
        }
        val dishPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = size * 0.0504f
            strokeCap = Paint.Cap.ROUND
        }

        val postX = size * 0.5f
        val beamY = size * 0.3667f
        canvas.drawLine(postX, size * 0.2778f, postX, size * 0.7222f, framePaint)
        canvas.drawLine(size * 0.3519f, size * 0.7222f, size * 0.6481f, size * 0.7222f, framePaint)
        canvas.drawLine(size * 0.2778f, beamY, size * 0.7222f, beamY, framePaint)

        val dishRadius = size * 0.0652f
        val dishY = size * 0.5444f
        for ((beamEndX, dishCenterX) in listOf(size * 0.2778f to size * 0.2837f, size * 0.7222f to size * 0.7163f)) {
            canvas.drawLine(beamEndX, beamY, dishCenterX - dishRadius, dishY, dishPaint)
            canvas.drawLine(beamEndX, beamY, dishCenterX + dishRadius, dishY, dishPaint)
            val dishRect = RectF(dishCenterX - dishRadius, dishY - dishRadius, dishCenterX + dishRadius, dishY + dishRadius)
            canvas.drawArc(dishRect, 180f, -180f, false, dishPaint)
        }

        val fulcrumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(postX, size * 0.337f, size * 0.0385f, fulcrumPaint)

        return bitmap
    }
}
