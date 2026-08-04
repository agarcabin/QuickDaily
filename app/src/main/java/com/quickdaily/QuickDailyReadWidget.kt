// QuickDailyReadWidget.kt
package com.quickdaily

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.quickdaily.util.DateUtil

class QuickDailyReadWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        BetaLogger.init(ctx)
        BetaLogger.log("ReadWidget", "onUpdate widgetIds=${ids.joinToString()}")
        for (id in ids) {
            try { updateWidget(ctx, manager, id, null) } catch (_: Exception) { }
        }
        WidgetRefreshCoordinator.refreshRead(ctx, immediate = true)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        BetaLogger.init(context)
        BetaLogger.log("ReadWidget", "size changed widgetId=$appWidgetId options=$newOptions")
        updateWidget(context, appWidgetManager, appWidgetId, null)
        WidgetRefreshCoordinator.refreshRead(context, immediate = true)
    }

    override fun onReceive(context: Context, intent: Intent) {
        BetaLogger.init(context)
        BetaLogger.log("ReadWidget", "onReceive action=${intent.action} widgetId=${intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)}")
        try { super.onReceive(context, intent) } catch (_: Exception) { }
        if (ACTION_TOGGLE_MARKDOWN == intent.action) {
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            val current = prefs.getBoolean("render_markdown", true)
            prefs.edit().putBoolean("render_markdown", !current).apply()
            BetaLogger.log("ReadWidget/Render", "renderMarkdown changed from=$current to=${!current}")
            refreshAllWidgets(context, immediate = true)
        } else if (ACTION_MIDNIGHT_REFRESH == intent.action) {
            refreshAllWidgets(context, immediate = true)
        } else if (ACTION_TOGGLE_TASK == intent.action) {
            val path = intent.getStringExtra(TaskWidget.EXTRA_TASK_PATH).orEmpty()
            val lineIndex = intent.getIntExtra(TaskWidget.EXTRA_TASK_LINE, -1)
            val expectedRaw = intent.getStringExtra(TaskWidget.EXTRA_TASK_RAW).orEmpty()
            if (path.isNotBlank() && lineIndex >= 0) {
                val pendingResult = goAsync()
                WidgetAsyncWorkRunner.launch(
                    finishable = WidgetAsyncFinishable { pendingResult.finish() },
                ) {
                    toggleTask(context.applicationContext, path, lineIndex, expectedRaw)
                }
            } else {
                BetaLogger.log("ReadWidget", "toggle ignored invalid path=$path line=$lineIndex")
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_MARKDOWN = "com.quickdaily.READ_TOGGLE_MARKDOWN"
        const val ACTION_MIDNIGHT_REFRESH = "com.quickdaily.READ_MIDNIGHT_REFRESH"

        fun scheduleMidnightRefresh(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, QuickDailyReadWidget::class.java).apply { action = ACTION_MIDNIGHT_REFRESH }
                val pi = PendingIntent.getBroadcast(context, 999, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val calendar = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 1)
                    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                }
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
            } catch (_: Exception) { }
        }

        fun refreshAllWidgets(context: Context, immediate: Boolean = false) {
            WidgetRefreshCoordinator.refreshRead(context, immediate)
        }

        internal suspend fun refreshNow(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, QuickDailyReadWidget::class.java)
                val ids = manager.getAppWidgetIds(component)
                BetaLogger.log("ReadWidget", "refreshNow widgetCount=${ids.size} widgetIds=${ids.joinToString()}")
                val result = WidgetContentLoader.loadRead(context)
                logWidgetResult("ReadWidget", result)
                for (id in ids) updateWidget(context, manager, id, result)
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                    manager.notifyAppWidgetViewDataChanged(ids, R.id.content_list)
                    BetaLogger.log("ReadWidget", "notifyDataChanged sent widgetCount=${ids.size}")
                } else {
                    BetaLogger.log("ReadWidget", "direct collection submitted widgetCount=${ids.size}")
                }
            } catch (e: Exception) { BetaLogger.log("ReadWidget", "refreshNow failed exception=${e.javaClass.simpleName} message=${e.message}") }
        }

        private fun toggleTask(
            context: Context,
            path: String,
            lineIndex: Int,
            expectedRaw: String,
        ) {
            val result = TaskToggleUseCase.toggle(
                context = context,
                path = path,
                lineIndex = lineIndex,
                expectedRaw = expectedRaw,
                logTag = "ReadWidget",
            )
            if (result.succeeded) {
                WidgetRefreshCoordinator.refreshAll(context, immediate = true)
            } else if (result.failureReason == "stale_line") {
                WidgetRefreshCoordinator.refreshRead(context, immediate = true)
            }
        }

        private fun updateWidget(
            ctx: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            result: WidgetLoadResult<List<ReadWidgetItem>>?
        ) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_diary_read)
            val appearance = WidgetAppearance.colors(ctx)
            val size = WidgetSizePolicy.forWidget(manager, widgetId)
            BetaLogger.log(
                "ReadWidget/Render",
                "widgetId=$widgetId size=$size result=${result?.javaClass?.simpleName ?: "loading"}",
            )
            WidgetAppearance.applyRoot(views, R.id.widget_root, appearance)
            WidgetSizePolicy.applyReadChrome(views, size)

            // Editor page button
            val homeIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val homePi = PendingIntent.getActivity(ctx, widgetId + 100, homeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_home, homePi)

            // Add button
            val addIntent = Intent(ctx, NoteEditActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("floating_source", FloatingNoteSource.WIDGET.name)
                putExtra(NoteEditActivity.EXTRA_REMEMBER_TARGET, false)
            }
            val addPi = PendingIntent.getActivity(ctx, widgetId + 200, addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_add, addPi)

            // Eye button - toggle via broadcast (no ToggleMarkdownActivity needed)
            val toggleIntent = Intent(ctx, QuickDailyReadWidget::class.java).apply { action = ACTION_TOGGLE_MARKDOWN }
            val togglePi = PendingIntent.getBroadcast(ctx, widgetId + 300, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_eye, togglePi)
            val renderMd = ctx.getSharedPreferences("QuickDaily", 0).getBoolean("render_markdown", true)
            views.setImageViewResource(R.id.btn_eye,
                if (renderMd) R.drawable.ic_eye_on_white else R.drawable.ic_eye_off_white)

            // Title
            val prefs = ctx.getSharedPreferences("QuickDaily", 0)
            val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
            views.setTextViewText(R.id.widget_title, DateUtil.todayStr(dateFormat))
            views.setTextColor(R.id.widget_title, appearance.foreground)
            views.setTextColor(R.id.empty_view, appearance.muted)
            val effectiveResult = result ?: WidgetLoadResult.Empty("正在加载…")
            val status = when (effectiveResult) {
                is WidgetLoadResult.Success -> "暂无日记"
                is WidgetLoadResult.Empty -> effectiveResult.message
                is WidgetLoadResult.Failure -> effectiveResult.message
            }
            views.setTextViewText(R.id.empty_view, status)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val items = (effectiveResult as? WidgetLoadResult.Success)?.value.orEmpty()
                views.setRemoteAdapter(R.id.content_list, ReadWidgetViews.collection(ctx, items, size))
            } else {
                // API 26-30 fallback: bind the RemoteViewsService with BIND_REMOTEVIEWS.
                val serviceIntent = Intent(ctx, QuickDailyReadWidgetService::class.java).apply {
                    data = Uri.parse("quickdaily://read-widget/$widgetId")
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                views.setRemoteAdapter(R.id.content_list, serviceIntent)
            }
            views.setEmptyView(R.id.content_list, R.id.empty_view)

            // Task toggle click template
            val toggleTaskIntent = Intent(ctx, QuickDailyReadWidget::class.java).apply { action = ACTION_TOGGLE_TASK }
            val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else { PendingIntent.FLAG_UPDATE_CURRENT }
            val toggleTaskPi = PendingIntent.getBroadcast(ctx, widgetId, toggleTaskIntent, piFlags)
            views.setPendingIntentTemplate(R.id.content_list, toggleTaskPi)

            manager.updateAppWidget(widgetId, views)
        }

        private const val ACTION_TOGGLE_TASK = "com.quickdaily.READ_TOGGLE_TASK"
    }
}
