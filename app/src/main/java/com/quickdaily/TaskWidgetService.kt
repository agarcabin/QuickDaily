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
    @Volatile
    private var tasks: List<PreparedTaskWidgetItem> = emptyList()

    override fun onCreate() {
        loadTasks()
    }

    override fun onDataSetChanged() {
        BetaLogger.log("TaskWidgetSvc", "onDataSetChanged start")
        loadTasks()
        BetaLogger.log("TaskWidgetSvc", "onDataSetChanged complete tasks=${tasks.size}")
    }

    override fun onDestroy() {
        tasks = emptyList()
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
        if (!WidgetGeneration.isCurrent(context, WidgetFillInContract.KIND_TASK, widgetId, generation)) {
            tasks = emptyList()
            return
        }
        val snapshot = WidgetSnapshots.tasks[widgetId]?.takeIf { generation <= 0 || it.generation == generation }
        tasks = snapshot?.items.orEmpty()
        if (snapshot == null) {
            publishEmptyStatus(UiText.Resource(R.string.qd_widget_refresh_ready))
            WidgetRefreshCoordinator.refreshTasks(context)
        } else {
            snapshot.message?.let(::publishEmptyStatus)
        }
    }

    private fun publishEmptyStatus(message: UiText) {
        val uiContext = LocaleController.localizedContext(context)
        val resolvedMessage = message.resolve(uiContext)
        if (resolvedMessage.isBlank()) return
        try {
            val manager = android.appwidget.AppWidgetManager.getInstance(context)
            if (widgetId < 0 || !WidgetGeneration.isCurrent(context, WidgetFillInContract.KIND_TASK, widgetId, generation)) return
            val ids = intArrayOf(widgetId)
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
