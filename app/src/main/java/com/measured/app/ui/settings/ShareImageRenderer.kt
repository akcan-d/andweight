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
 * Compose screen - a handful of fixed layouts are more predictable than trying to snapshot a
 * composable to a bitmap. Picking which of [ShareCardDesign]'s layouts to use is a supporter
 * perk (see SettingsScreen's share button); everyone else always gets [ShareCardDesign.CLASSIC].
 */
object ShareImageRenderer {

    private const val SIZE = 1080

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val WHITE_SOFT = 0xCCFFFFFF.toInt()
    private const val DIVIDER = 0x40FFFFFF

    /** [backgroundColor] should be the light-mode primary for the person's selected accent theme
     * (not whatever [androidx.compose.material3.ColorScheme.primary] currently resolves to) -
     * dark mode's "primary" is a light tone meant to pair with dark text, which would make the
     * white text this card draws unreadable. Callers should resolve it with
     * `accentPaletteFor(theme, darkTheme = false).primary`, regardless of the system's actual
     * dark-mode state, so the shared card always looks like a deep, rich brand card. */
    fun render(
        context: Context,
        summary: SettingsViewModel.ShareSummary,
        backgroundColor: Int,
        design: ShareCardDesign = ShareCardDesign.CLASSIC,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        // ResourcesCompat gives back the variable font at its default instance - synthetic-bolding
        // it is good enough for a share image, rather than threading a font-variation-settings
        // path through a resource id (which Typeface.Builder doesn't accept directly).
        val quicksand = ResourcesCompat.getFont(context, R.font.quicksand_variable)
        val boldTypeface = quicksand?.let { Typeface.create(it, Typeface.BOLD) } ?: Typeface.DEFAULT_BOLD
        val bodyTypeface = ResourcesCompat.getFont(context, R.font.figtree_variable) ?: Typeface.DEFAULT

        when (design) {
            ShareCardDesign.CLASSIC -> drawClassic(canvas, summary, boldTypeface, bodyTypeface)
            ShareCardDesign.MINIMAL -> drawMinimal(canvas, summary, boldTypeface, bodyTypeface)
            ShareCardDesign.STATS -> drawStats(canvas, summary, boldTypeface, bodyTypeface)
        }

        return bitmap
    }

    private fun drawClassic(canvas: Canvas, summary: SettingsViewModel.ShareSummary, boldTypeface: Typeface, bodyTypeface: Typeface) {
        val center = SIZE / 2f

        val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE_SOFT
            typeface = boldTypeface
            textSize = 38f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("M E A S U R E D", center, 170f, eyebrowPaint)

        val unit = summary.unit
        val amount = formatOneDecimal(unit.fromKg(abs(summary.changeKg)))

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
        canvas.drawText("$unitLabel ${directionWord(summary.direction)}", center, 660f, unitPaint)

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
    }

    /** As bare as this card gets: just the number, nothing else competing for attention. */
    private fun drawMinimal(canvas: Canvas, summary: SettingsViewModel.ShareSummary, boldTypeface: Typeface, bodyTypeface: Typeface) {
        val center = SIZE / 2f
        val unit = summary.unit
        val amount = formatOneDecimal(unit.fromKg(abs(summary.changeKg)))

        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE
            typeface = boldTypeface
            textSize = 280f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(amount, center, center + 40f, numberPaint)

        val unitLabel = unit.name.lowercase()
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE_SOFT
            typeface = bodyTypeface
            textSize = 56f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("$unitLabel ${directionWord(summary.direction)}", center, center + 150f, labelPaint)
    }

    /** Two figures side by side - the weight moved and the days tracked - for anyone who wants
     * the consistency story told alongside the number itself. */
    private fun drawStats(canvas: Canvas, summary: SettingsViewModel.ShareSummary, boldTypeface: Typeface, bodyTypeface: Typeface) {
        val center = SIZE / 2f

        val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE_SOFT
            typeface = boldTypeface
            textSize = 34f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("M E A S U R E D", center, 150f, eyebrowPaint)

        val unit = summary.unit
        val amount = formatOneDecimal(unit.fromKg(abs(summary.changeKg)))
        val leftX = center - 220f
        val rightX = center + 220f
        val statY = 560f
        val labelY = 630f

        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE
            typeface = boldTypeface
            textSize = 150f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(amount, leftX, statY, numberPaint)
        canvas.drawText("${summary.daysActive}", rightX, statY, numberPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE_SOFT
            typeface = bodyTypeface
            textSize = 38f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${unit.name.lowercase()} ${directionWord(summary.direction)}", leftX, labelY, labelPaint)
        canvas.drawText(if (summary.daysActive == 1) "day tracked" else "days tracked", rightX, labelY, labelPaint)

        val dividerPaint = Paint().apply { color = DIVIDER; strokeWidth = 2f }
        canvas.drawLine(center, statY - 110f, center, labelY + 20f, dividerPaint)
    }

    private fun directionWord(direction: GoalDirection): String = when (direction) {
        GoalDirection.LOSE -> "down"
        GoalDirection.GAIN -> "up"
        GoalDirection.MAINTAIN -> "maintained"
    }

    fun share(context: Context, bitmap: Bitmap) {
        val directory = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(directory, "measured_progress.png")
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
