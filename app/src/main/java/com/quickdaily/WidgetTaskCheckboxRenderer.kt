package com.quickdaily

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.roundToInt

/**
 * Draws the task checkbox used by both collection-widget renderers.
 *
 * RemoteViews cannot use Compose's Material checkbox, so this keeps the visual
 * contract in one small MD2-style renderer: a rounded outlined box when empty,
 * and an accent-filled box with a high-contrast check when checked.
 */
internal object WidgetTaskCheckboxRenderer {
    private const val SIZE_DP = 24f
    private const val CHECKED_INSET_DP = 3f
    // The outline stroke is centered on its bounds, so this extra 1dp keeps
    // its visible outer edge aligned with the filled checked box.
    private const val UNCHECKED_INSET_DP = 4f
    private const val CORNER_RADIUS_DP = 3f
    private const val STROKE_WIDTH_DP = 2f

    fun bitmap(
        context: Context,
        checked: Boolean,
        colors: WidgetAppearance.Colors,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (SIZE_DP * density).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = (if (checked) CHECKED_INSET_DP else UNCHECKED_INSET_DP) * density
        val strokeWidth = STROKE_WIDTH_DP * density
        val bounds = RectF(inset, inset, size - inset, size - inset)

        val accent = checkboxAccent(colors)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = if (checked) Paint.Style.FILL else Paint.Style.STROKE
            color = if (checked) accent else colors.iconMuted
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawRoundRect(bounds, CORNER_RADIUS_DP * density, CORNER_RADIUS_DP * density, paint)

        if (checked) {
            val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = contrastingColor(accent)
                this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val check = Path().apply {
                moveTo(size * 0.29f, size * 0.52f)
                lineTo(size * 0.45f, size * 0.68f)
                lineTo(size * 0.73f, size * 0.36f)
            }
            canvas.drawPath(check, checkPaint)
        }
        return bitmap
    }

    private fun checkboxAccent(colors: WidgetAppearance.Colors): Int =
        if (contrastRatio(colors.iconAccent, colors.background) >= 3f) {
            colors.iconAccent
        } else {
            colors.foreground
        }

    private fun contrastingColor(color: Int): Int =
        if (relativeLuminance(color) > 0.45) Color.BLACK else Color.WHITE

    private fun contrastRatio(first: Int, second: Int): Double {
        val light = maxOf(relativeLuminance(first), relativeLuminance(second))
        val dark = minOf(relativeLuminance(first), relativeLuminance(second))
        return (light + 0.05) / (dark + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.03928) normalized / 12.92
            else ((normalized + 0.055) / 1.055).let { it * it * it }
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }
}
