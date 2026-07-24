package com.quickdaily

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

/** Size information exposed by the launcher in dp for one widget instance. */
data class WidgetSize(
    val widthDp: Int,
    val heightDp: Int
) {
    val isNarrow: Boolean get() = widthDp < 220
    val isTiny: Boolean get() = widthDp < 150
    val isShort: Boolean get() = heightDp < 120
    val readMaxLines: Int get() = if (isTiny || isShort) 1 else 3
    val taskMaxLines: Int get() = if (isTiny) 1 else 2

    companion object {
        val DEFAULT = WidgetSize(300, 180)
    }
}

object WidgetSizePolicy {
    private const val OPTION_MIN_WIDTH = "appWidgetMinWidth"
    private const val OPTION_MIN_HEIGHT = "appWidgetMinHeight"

    fun forWidget(manager: AppWidgetManager, widgetId: Int): WidgetSize =
        fromOptions(manager.getAppWidgetOptions(widgetId))

    fun fromOptions(options: Bundle?): WidgetSize {
        if (options == null) return WidgetSize.DEFAULT
        return fromDp(
            options.getInt(OPTION_MIN_WIDTH, WidgetSize.DEFAULT.widthDp),
            options.getInt(OPTION_MIN_HEIGHT, WidgetSize.DEFAULT.heightDp)
        )
    }

    fun fromDp(widthDp: Int, heightDp: Int): WidgetSize =
        WidgetSize(widthDp.coerceAtLeast(1), heightDp.coerceAtLeast(1))

    fun applyReadChrome(views: RemoteViews, size: WidgetSize) {
        views.setViewVisibility(R.id.btn_eye, if (size.isNarrow) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.btn_home, if (size.isTiny) View.GONE else View.VISIBLE)
        views.setFloat(R.id.widget_title, "setTextSize", if (size.isTiny) 11f else 13f)
    }

    fun applyTaskChrome(views: RemoteViews, size: WidgetSize) {
        views.setViewVisibility(R.id.btn_refresh, if (size.isNarrow) View.GONE else View.VISIBLE)
        views.setFloat(R.id.widget_title, "setTextSize", if (size.isTiny) 11f else 13f)
    }
}
