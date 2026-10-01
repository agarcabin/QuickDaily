package com.quickdaily

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.quickdaily.ui.theme.quickDailyColorScheme
import kotlin.math.pow

/** Shared, persisted appearance for all desktop widgets. */
object WidgetAppearance {
    private const val PREFS = "QuickDaily"
    const val DEFAULT_STYLE = "dark"
    const val DEFAULT_OPACITY_PERCENT = 75
    private const val MD2_ICON_FOREGROUND = 0xFF3C4043.toInt()
    private const val MD2_ICON_MUTED = 0xFF5F6368.toInt()

    data class Colors(
        val background: Int,
        val foreground: Int,
        val muted: Int,
        val tagForeground: Int,
        val iconForeground: Int,
        val iconMuted: Int,
        val iconAccent: Int,
    )

    fun colors(context: Context): Colors {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val style = resolveStyle(prefs.getString("widget_style", null))
        val base = when (style) {
            "light" -> Color.WHITE
            "dark" -> Color.rgb(32, 33, 36)
            "system" -> if (isSystemDark(context)) Color.rgb(32, 33, 36) else Color.WHITE
            else -> prefs.getLong(
                "widget_background_color",
                SettingsSliderDefaults.DEFAULT_WIDGET_BACKGROUND_COLOR,
            ).toInt()
        }
        val alpha = (prefs.getInt("widget_opacity", DEFAULT_OPACITY_PERCENT).coerceIn(0, 100) * 255 / 100)
        val background = Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
        // Calculate contrast from the opaque base. Launcher wallpaper is unknown, so this is
        // intentionally conservative for transparent backgrounds.
        val luminance = (0.2126 * Color.red(base) + 0.7152 * Color.green(base) + 0.0722 * Color.blue(base)) / 255.0
        val foreground = if (luminance > 0.52) Color.BLACK else Color.WHITE
        val muted = Color.argb(if (foreground == Color.BLACK) 145 else 185, Color.red(foreground), Color.green(foreground), Color.blue(foreground))
        val appTheme = quickDailyColorScheme(context)
        val lightBackground = luminance > 0.52
        val iconForeground = if (lightBackground) MD2_ICON_FOREGROUND else foreground
        val iconMuted = if (lightBackground) MD2_ICON_MUTED else muted
        val iconAccent = selectIconAccent(
            primary = appTheme.primary.toArgb(),
            primaryContainer = appTheme.primaryContainer.toArgb(),
            base = base,
            lightBackground = lightBackground,
        )
        return Colors(
            background = background,
            foreground = foreground,
            muted = muted,
            tagForeground = appTheme.primary.toArgb(),
            iconForeground = iconForeground,
            iconMuted = iconMuted,
            iconAccent = iconAccent,
        )
    }

    internal fun resolveStyle(storedStyle: String?): String = storedStyle ?: DEFAULT_STYLE

    /**
     * Picks the most useful Monet role for widget accents while keeping enough contrast
     * against the opaque widget surface. A light editor theme often exposes a dark primary
     * that is not visible on a dark launcher widget, so the container role is tried first
     * there instead of silently losing the theme hue to a white fallback.
     */
    internal fun selectIconAccent(
        primary: Int,
        primaryContainer: Int,
        base: Int,
        lightBackground: Boolean,
    ): Int {
        val candidates = if (lightBackground) {
            listOf(primary, primaryContainer)
        } else {
            listOf(primaryContainer, primary)
        }
        return candidates.firstOrNull { contrastRatio(it, base) >= 3.0 } ?: candidates.first()
    }

    private fun contrastRatio(first: Int, second: Int): Double {
        val light = maxOf(relativeLuminance(first), relativeLuminance(second))
        val dark = minOf(relativeLuminance(first), relativeLuminance(second))
        return (light + 0.05) / (dark + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        val red = (color ushr 16) and 0xFF
        val green = (color ushr 8) and 0xFF
        val blue = color and 0xFF

        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.03928) {
                normalized / 12.92
            } else {
                ((normalized + 0.055) / 1.055).pow(2.4)
            }
        }
        return 0.2126 * channel(red) +
            0.7152 * channel(green) +
            0.0722 * channel(blue)
    }

    private fun isSystemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun applyRoot(views: RemoteViews, rootId: Int, colors: Colors) {
        // Keep the XML shape as the background: replacing it with a plain color removes
        // its 16dp rounded corners on launcher widgets.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setInt(
                rootId,
                "setBackgroundResource",
                R.drawable.widget_background,
            )
            views.setColorStateList(rootId, "setBackgroundTintList", ColorStateList.valueOf(colors.background))
        } else {
            // RemoteViews#setColorStateList is unavailable before Android 12. Keep the
            // rounded shape on older devices instead of risking a NoSuchMethodError.
            val fallback = if (colors.foreground == Color.BLACK) {
                R.drawable.widget_background_light
            } else {
                R.drawable.widget_background
            }
            views.setInt(rootId, "setBackgroundResource", fallback)
        }
    }

    fun applyIcon(views: RemoteViews, viewId: Int, color: Int) {
        views.setInt(viewId, "setColorFilter", color)
    }
}
