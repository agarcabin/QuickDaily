package com.quickdaily

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class TaskWidgetService : RemoteViewsService() {
    companion object {
        /** AppWidget hosts create one factory per widget; serialize their shared vault read. */
        internal val taskLoadLock = Any()
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory {
        val widgetId = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        BetaLogger.log(
            "TaskWidgetSvc",
            "factory created widgetId=$widgetId data=${intent.data}"
        )
        return TaskViewsFactory(applicationContext, widgetId)
    }
}

class TaskViewsFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private val tasks = mutableListOf<TaskWidgetItem>()

    override fun onCreate() {
        synchronized(TaskWidgetService.taskLoadLock) { loadTasks() }
    }

    override fun onDataSetChanged() {
        BetaLogger.log("TaskWidgetSvc", "onDataSetChanged start")
        synchronized(TaskWidgetService.taskLoadLock) { loadTasks() }
        BetaLogger.log("TaskWidgetSvc", "onDataSetChanged complete tasks=${tasks.size}")
    }

    override fun onDestroy() {
        tasks.clear()
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews =
        tasks.getOrNull(position)?.let {
            val size = WidgetSizePolicy.forWidget(
                android.appwidget.AppWidgetManager.getInstance(context), widgetId
            )
            TaskWidgetViews.create(context, it, size)
        }
            ?: RemoteViews(context.packageName, R.layout.widget_task_item)

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private fun loadTasks() {
        tasks.clear()
        val result = WidgetContentLoader.loadTasks(context)
        if (result is WidgetLoadResult.Success) {
            tasks.addAll(result.value)
        } else {
            publishEmptyStatus(
                when (result) {
                    is WidgetLoadResult.Empty -> result.message
                    is WidgetLoadResult.Failure -> result.message
                    is WidgetLoadResult.Success -> ""
                }
            )
        }
        logWidgetResult("TaskWidgetSvc", result)
    }

    private fun publishEmptyStatus(message: String) {
        if (message.isBlank()) return
        try {
            val manager = android.appwidget.AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, TaskWidget::class.java)
            )
            if (ids.isNotEmpty()) {
                val views = RemoteViews(context.packageName, R.layout.widget_tasks)
                views.setTextViewText(R.id.empty_view, message)
                views.setTextColor(R.id.empty_view, WidgetAppearance.colors(context).muted)
                manager.partiallyUpdateAppWidget(ids, views)
            }
        } catch (e: Exception) {
            BetaLogger.log("TaskWidgetSvc", "publish empty status failed=${e.javaClass.simpleName} detail=${e.message}")
        }
    }
}
