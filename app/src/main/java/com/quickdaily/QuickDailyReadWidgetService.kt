package com.quickdaily

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class QuickDailyReadWidgetService : LocalizedRemoteViewsService() {
    companion object {
        /** Prevent separate widget factories from reading the same diary concurrently. */
        internal val readLoadLock = Any()
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory {
        BetaLogger.init(applicationContext, "QuickDailyReadWidgetService")
        val widgetId = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val generation = intent.getLongExtra(WidgetFillInContract.EXTRA_GENERATION, 0L)
        BetaLogger.log(
            "ReadWidgetSvc",
            "factory created widgetId=$widgetId generation=$generation data=${intent.data}"
        )
        return ReadViewsFactory(applicationContext, widgetId, generation)
    }
}

class ReadViewsFactory(
    private val context: Context,
    private val widgetId: Int,
    private val generation: Long,
) : RemoteViewsService.RemoteViewsFactory {
    @Volatile
    private var lines: List<PreparedReadWidgetItem> = emptyList()

    override fun onCreate() {
        loadContent()
    }

    @Synchronized
    override fun onDataSetChanged() {
        BetaLogger.log("ReadWidgetSvc", "onDataSetChanged start")
        loadContent()
        BetaLogger.log("ReadWidgetSvc", "onDataSetChanged complete lines=${lines.size}")
    }

    override fun onDestroy() {
        lines = emptyList()
    }

    override fun getCount(): Int = lines.size

    override fun getViewAt(position: Int): RemoteViews =
        lines.getOrNull(position)?.let {
            val size = WidgetSizePolicy.forWidget(
                android.appwidget.AppWidgetManager.getInstance(context), widgetId
            )
            ReadWidgetViews.create(
                context = context,
                item = it,
                size = size,
                widgetId = widgetId,
                generation = generation,
            )
        }
            ?: RemoteViews(context.packageName, R.layout.widget_diary_read_line)

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 3
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private fun loadContent() {
        if (!WidgetGeneration.isCurrent(context, WidgetFillInContract.KIND_READ, widgetId, generation)) {
            lines = emptyList()
            return
        }
        val snapshot = WidgetSnapshots.read[widgetId]?.takeIf { generation <= 0 || it.generation == generation }
        lines = snapshot?.items.orEmpty()
        if (snapshot == null) {
            publishEmptyStatus(UiText.Resource(R.string.qd_widget_refresh_ready))
            WidgetRefreshCoordinator.refreshRead(context)
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
            if (widgetId < 0 || !WidgetGeneration.isCurrent(context, WidgetFillInContract.KIND_READ, widgetId, generation)) return
            val ids = intArrayOf(widgetId)
            if (ids.isNotEmpty()) {
                val views = RemoteViews(context.packageName, R.layout.widget_diary_read)
                views.setTextViewText(R.id.empty_view, resolvedMessage)
                views.setTextColor(R.id.empty_view, WidgetAppearance.colors(context).muted)
                manager.partiallyUpdateAppWidget(ids, views)
            }
        } catch (e: Exception) {
            BetaLogger.log("ReadWidgetSvc", "publish empty status failed=${e.javaClass.simpleName} detail=${e.message}")
        }
    }
}
