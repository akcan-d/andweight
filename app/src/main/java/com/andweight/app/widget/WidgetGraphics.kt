package com.andweight.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Hand-drawn with android.graphics directly (same reasoning as ShareImageRenderer: a fixed,
 * simple shape is more predictable than trying to snapshot a composable), rendered onto a
 * transparent bitmap so it sits naturally on whatever background the home screen gives it.
 */
object WidgetGraphics {

    /** A ring that mirrors the Today screen's goal-progress ring - track plus a swept arc,
     * starting at 12 o'clock. */
    fun progressRing(sizePx: Int, progress: Float, trackColor: Int, ringColor: Int): Bitmap {
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
        return bitmap
    }

    /** A simple connected-line trend, mirroring Journey's WeightTrendCard chart but scaled down
     * for the widget. Expects at least two values. */
    fun sparkline(widthPx: Int, heightPx: Int, values: List<Double>, lineColor: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (values.size < 2) return bitmap

        val minValue = values.min()
        val maxValue = values.max()
        val range = (maxValue - minValue).let { if (it < 0.01) 1.0 else it }
        val dotRadius = heightPx * 0.07f
        val verticalInset = dotRadius + heightPx * 0.08f

        fun xFor(index: Int) = widthPx * index / (values.size - 1).toFloat()
        fun yFor(value: Double): Float {
            val t = ((value - minValue) / range).toFloat()
            return heightPx - verticalInset - t * (heightPx - 2 * verticalInset)
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            style = Paint.Style.STROKE
            strokeWidth = heightPx * 0.06f
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until values.size - 1) {
            canvas.drawLine(xFor(i), yFor(values[i]), xFor(i + 1), yFor(values[i + 1]), linePaint)
        }

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor }
        canvas.drawCircle(xFor(values.lastIndex), yFor(values.last()), dotRadius, dotPaint)

        return bitmap
    }
}
