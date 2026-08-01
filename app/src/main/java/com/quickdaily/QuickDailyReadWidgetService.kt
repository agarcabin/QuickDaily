package com.quickdaily

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class QuickDailyReadWidgetService : RemoteViewsService() {
    companion object {
        /** Prevent separate widget factories from reading the same diary concurrently. */
        internal val readLoadLock = Any()
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory {
        BetaLogger.init(applicationContext, "QuickDailyReadWidgetService")
        val widgetId = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        BetaLogger.log(
            "ReadWidgetSvc",
            "factory created widgetId=$widgetId data=${intent.data}"
        )
        return ReadViewsFactory(applicationContext, widgetId)
    }
}

class ReadViewsFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private val lines = mutableListOf<ReadWidgetItem>()

    override fun onCreate() {
        synchronized(QuickDailyReadWidgetService.readLoadLock) { loadContent() }
    }

    @Synchronized
    override fun onDataSetChanged() {
        BetaLogger.log("ReadWidgetSvc", "onDataSetChanged start")
        synchronized(QuickDailyReadWidgetService.readLoadLock) { loadContent() }
        BetaLogger.log("ReadWidgetSvc", "onDataSetChanged complete lines=${lines.size}")
    }

    override fun onDestroy() {
        lines.clear()
    }

    override fun getCount(): Int = lines.size

    override fun getViewAt(position: Int): RemoteViews =
        lines.getOrNull(position)?.let {
            val size = WidgetSizePolicy.forWidget(
                android.appwidget.AppWidgetManager.getInstance(context), widgetId
            )
            ReadWidgetViews.create(context, it, size)
        }
            ?: RemoteViews(context.packageName, R.layout.widget_diary_read_line)

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 3
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private fun loadContent() {
        lines.clear()
        val result = WidgetContentLoader.loadRead(context)
        if (result is WidgetLoadResult.Success) {
            lines.addAll(result.value)
        } else {
            publishEmptyStatus(
                when (result) {
                    is WidgetLoadResult.Empty -> result.message
                    is WidgetLoadResult.Failure -> result.message
                    is WidgetLoadResult.Success -> ""
                }
            )
        }
        logWidgetResult("ReadWidgetSvc", result)
    }

    private fun publishEmptyStatus(message: String) {
        if (message.isBlank()) return
        try {
            val manager = android.appwidget.AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, QuickDailyReadWidget::class.java)
            )
            if (ids.isNotEmpty()) {
                val views = RemoteViews(context.packageName, R.layout.widget_diary_read)
                views.setTextViewText(R.id.empty_view, message)
                views.setTextColor(R.id.empty_view, WidgetAppearance.colors(context).muted)
                manager.partiallyUpdateAppWidget(ids, views)
            }
        } catch (e: Exception) {
            BetaLogger.log("ReadWidgetSvc", "publish empty status failed=${e.javaClass.simpleName} detail=${e.message}")
        }
    }
}
