package com.quickdaily

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class TaskWidgetService : LocalizedRemoteViewsService() {
    companion object {
        /** AppWidget hosts create one factory per widget; serialize their shared vault read. */
        internal val taskLoadLock = Any()
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory {
        BetaLogger.init(applicationContext, "TaskWidgetService")
        val widgetId = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val generation = intent.getLongExtra(WidgetFillInContract.EXTRA_GENERATION, 0L)
        BetaLogger.log(
            "TaskWidgetSvc",
            "factory created widgetId=$widgetId generation=$generation data=${intent.data}"
        )
        return TaskViewsFactory(applicationContext, widgetId, generation)
    }
}

class TaskViewsFactory(
    private val context: Context,
    private val widgetId: Int,
    private val generation: Long,
) : RemoteViewsService.RemoteViewsFactory {
    private val tasks = mutableListOf<PreparedTaskWidgetItem>()

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
            TaskWidgetViews.create(
                context = context,
                item = it,
                size = size,
                widgetId = widgetId,
                generation = generation,
            )
        }
            ?: RemoteViews(context.packageName, R.layout.widget_task_item)

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 3
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private fun loadTasks() {
        tasks.clear()
        if (!WidgetGeneration.isCurrent(
                context,
                WidgetFillInContract.KIND_TASK,
                widgetId,
                generation,
            )
        ) {
            BetaLogger.log("TaskWidgetSvc", "skip stale factory widgetId=$widgetId generation=$generation")
            return
        }
        val config = TaskWidgetConfigStore.load(context, widgetId)
        val result = WidgetContentLoader.loadTasks(context, config)
        if (!WidgetGeneration.isCurrent(
                context,
                WidgetFillInContract.KIND_TASK,
                widgetId,
                generation,
            )
        ) {
            BetaLogger.log("TaskWidgetSvc", "discard loaded stale factory widgetId=$widgetId generation=$generation")
            return
        }
        if (result is WidgetLoadResult.Success) {
            tasks.addAll(
                WidgetDisplayPreparer.prepareTasks(
                    result.value,
                    WidgetDisplayPreparer.rules(context),
                ),
            )
        } else {
            publishEmptyStatus(
                when (result) {
                    is WidgetLoadResult.Empty -> result.message
                    is WidgetLoadResult.Failure -> result.message
                    is WidgetLoadResult.Success -> UiText.Raw("")
                }
            )
        }
        logWidgetResult("TaskWidgetSvc", result)
    }

    private fun publishEmptyStatus(message: UiText) {
        val uiContext = LocaleController.localizedContext(context)
        val resolvedMessage = message.resolve(uiContext)
        if (resolvedMessage.isBlank()) return
        try {
            val manager = android.appwidget.AppWidgetManager.getInstance(context)
            val ids = if (widgetId >= 0) {
                intArrayOf(widgetId)
            } else {
                manager.getAppWidgetIds(
                    android.content.ComponentName(context, TaskWidget::class.java)
                )
            }
            if (ids.isNotEmpty()) {
                val views = RemoteViews(context.packageName, R.layout.widget_tasks)
                views.setTextViewText(R.id.empty_view, resolvedMessage)
                views.setTextColor(R.id.empty_view, WidgetAppearance.colors(context).muted)
                manager.partiallyUpdateAppWidget(ids, views)
            }
        } catch (e: Exception) {
            BetaLogger.log("TaskWidgetSvc", "publish empty status failed=${e.javaClass.simpleName} detail=${e.message}")
        }
    }
}
