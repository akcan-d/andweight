package com.measured.app.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.measured.app.R
import com.measured.app.domain.GoalDirection
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Draws a simple branded square card with android.graphics directly, rather than capturing a
 * Compose screen - this only ever needs to produce one fixed layout, so plain Canvas/Paint calls
 * are more predictable than trying to snapshot a composable to a bitmap.
 */
object ShareImageRenderer {

    private const val SIZE = 1080

    // Mirrors Color.kt's LightPrimary/LightSurface - duplicated here since this file works in
    // plain android.graphics ints rather than Compose Color, and only needs these two.
    private const val BACKGROUND_COLOR = 0xFF2D7D55.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val WHITE_SOFT = 0xCCFFFFFF.toInt()
    private const val DIVIDER = 0x40FFFFFF

    fun render(context: Context, summary: SettingsViewModel.ShareSummary): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND_COLOR)

        // ResourcesCompat gives back the variable font at its default instance - synthetic-bolding
        // it is good enough for a share image, rather than threading a font-variation-settings
        // path through a resource id (which Typeface.Builder doesn't accept directly).
        val quicksand = ResourcesCompat.getFont(context, R.font.quicksand_variable)
        val boldTypeface = quicksand?.let { Typeface.create(it, Typeface.BOLD) } ?: Typeface.DEFAULT_BOLD
        val bodyTypeface = ResourcesCompat.getFont(context, R.font.figtree_variable) ?: Typeface.DEFAULT

        val center = SIZE / 2f

        val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE_SOFT
            typeface = boldTypeface
            textSize = 38f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("M E A S U R E D", center, 170f, eyebrowPaint)

        val direction = summary.direction
        val unit = summary.unit
        val amount = formatOneDecimal(unit.fromKg(abs(summary.changeKg)))
        val directionWord = when (direction) {
            GoalDirection.LOSE -> "down"
            GoalDirection.GAIN -> "up"
            GoalDirection.MAINTAIN -> "maintained"
        }

        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE
            typeface = boldTypeface
            textSize = 230f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(amount, center, 590f, numberPaint)

        val unitLabel = unit.name.lowercase()
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE_SOFT
            typeface = bodyTypeface
            textSize = 50f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("$unitLabel $directionWord", center, 660f, unitPaint)

        val dividerPaint = Paint().apply { color = DIVIDER; strokeWidth = 2f }
        canvas.drawLine(center - 160f, 760f, center + 160f, 760f, dividerPaint)

        val dayWord = if (summary.daysActive == 1) "day" else "days"
        val daysPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE
            typeface = bodyTypeface
            textSize = 46f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${summary.daysActive} $dayWord tracked", center, 870f, daysPaint)

        return bitmap
    }

    fun share(context: Context, bitmap: Bitmap) {
        val directory = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(directory, "andweight_progress.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share your progress"))
    }

    private fun formatOneDecimal(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
