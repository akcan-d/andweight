package com.measured.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Hand-drawn with android.graphics directly (same reasoning as ShareImageRenderer: a fixed,
 * simple shape is more predictable than trying to snapshot a composable), rendered onto a
 * transparent bitmap so it sits naturally on whatever home-screen background the widget has -
 * the widgets built on this are deliberately background-free, floating directly on the wallpaper.
 */
object WidgetGraphics {

    /** A ring that mirrors the Today screen's goal-progress ring - track plus a swept arc,
     * starting at 12 o'clock - with its number drawn directly into the same bitmap rather than as
     * separate overlaid text, so it can carry a soft shadow for legibility against whatever
     * wallpaper is behind it (Glance's own Text styling has no shadow support). */
    fun progressRingWithNumber(
        sizePx: Int,
        progress: Float,
        trackColor: Int,
        ringColor: Int,
        textColor: Int,
        numberText: String,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val ringStrokeWidth = sizePx * 0.14f
        val inset = ringStrokeWidth / 2f
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = trackColor
            style = Paint.Style.STROKE
            strokeWidth = ringStrokeWidth
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(rect, -90f, 360f, false, trackPaint)

        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0f) {
            val ringPaint = Paint(trackPaint).apply { color = ringColor }
            canvas.drawArc(rect, -90f, sweep, false, ringPaint)
        }

        if (numberText.isNotEmpty()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = sizePx * 0.26f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                setShadowLayer(sizePx * 0.05f, 0f, 0f, 0x55000000)
            }
            val textY = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(numberText, sizePx / 2f, textY, textPaint)
        }

        return bitmap
    }
}
