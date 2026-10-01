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
    private val lines = mutableListOf<PreparedReadWidgetItem>()

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
        lines.clear()
        if (!WidgetGeneration.isCurrent(
                context,
                WidgetFillInContract.KIND_READ,
                widgetId,
                generation,
            )
        ) {
            BetaLogger.log("ReadWidgetSvc", "skip stale factory widgetId=$widgetId generation=$generation")
            return
        }
        val config = ReadWidgetConfigStore.load(context, widgetId)
        val result = WidgetContentLoader.loadRead(context, config)
        if (!WidgetGeneration.isCurrent(
                context,
                WidgetFillInContract.KIND_READ,
                widgetId,
                generation,
            )
        ) {
            BetaLogger.log("ReadWidgetSvc", "discard loaded stale factory widgetId=$widgetId generation=$generation")
            return
        }
        if (result is WidgetLoadResult.Success) {
            lines.addAll(
                WidgetDisplayPreparer.prepareRead(
                    result.value,
                    WidgetDisplayPreparer.rules(context),
                    imagePlaceholder = LocaleController.localizedContext(context).getString(R.string.qd_widget_image_placeholder),
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
        logWidgetResult("ReadWidgetSvc", result)
    }

    private fun publishEmptyStatus(message: UiText) {
        val uiContext = LocaleController.localizedContext(context)
        val resolvedMessage = message.resolve(uiContext)
        if (resolvedMessage.isBlank()) return
        try {
            val manager = android.appwidget.AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, QuickDailyReadWidget::class.java)
            )
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
