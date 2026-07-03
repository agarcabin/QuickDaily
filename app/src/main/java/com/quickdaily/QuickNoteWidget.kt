package com.quickdaily

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class QuickNoteWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = android.content.ComponentName(context, QuickNoteWidget::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(component)
            for (widgetId in widgetIds) {
                updateWidget(context, appWidgetManager, widgetId)
            }
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_quicknote)

            val intent = Intent(context, NoteEditActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, widgetId, intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.quicknote_root, pendingIntent)

            // 加载自定义图片
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            val imageUri = prefs.getString("widget_image_uri", "") ?: ""

            if (imageUri.isNotEmpty()) {
                // 显示图片，隐藏+号
                views.setViewVisibility(R.id.quicknote_image, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.quicknote_label, android.view.View.GONE)
                views.setImageViewUri(R.id.quicknote_image, android.net.Uri.parse(imageUri))
            } else {
                // 显示+号，隐藏图片
                views.setViewVisibility(R.id.quicknote_image, android.view.View.GONE)
                views.setViewVisibility(R.id.quicknote_label, android.view.View.VISIBLE)
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
