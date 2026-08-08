package com.quickdaily

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.quickdaily.ui.theme.quickDailyColorScheme

/** Shared, persisted appearance for all desktop widgets. */
object WidgetAppearance {
    private const val PREFS = "QuickDaily"
    const val DEFAULT_OPACITY_PERCENT = 75

    data class Colors(
        val background: Int,
        val foreground: Int,
        val muted: Int,
        val tagForeground: Int,
    )

    fun colors(context: Context): Colors {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val style = prefs.getString("widget_style", "dark") ?: "dark"
        val base = when (style) {
            "light" -> Color.WHITE
            "dark" -> Color.rgb(32, 33, 36)
            "system" -> if (isSystemDark(context)) Color.rgb(32, 33, 36) else Color.WHITE
            else -> prefs.getLong("widget_background_color", 0xFF202124L).toInt()
        }
        val alpha = (prefs.getInt("widget_opacity", DEFAULT_OPACITY_PERCENT).coerceIn(0, 100) * 255 / 100)
        val background = Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
        // Calculate contrast from the opaque base. Launcher wallpaper is unknown, so this is
        // intentionally conservative for transparent backgrounds.
        val luminance = (0.2126 * Color.red(base) + 0.7152 * Color.green(base) + 0.0722 * Color.blue(base)) / 255.0
        val foreground = if (luminance > 0.52) Color.BLACK else Color.WHITE
        val muted = Color.argb(if (foreground == Color.BLACK) 145 else 185, Color.red(foreground), Color.green(foreground), Color.blue(foreground))
        val appTheme = quickDailyColorScheme(context)
        return Colors(
            background = background,
            foreground = foreground,
            muted = muted,
            tagForeground = appTheme.primary.toArgb(),
        )
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
}
