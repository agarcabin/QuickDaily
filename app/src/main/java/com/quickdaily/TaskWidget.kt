package com.quickdaily

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast

class TaskWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        BetaLogger.init(context)
        BetaLogger.log("TaskWidget", "onUpdate widgetIds=${appWidgetIds.joinToString()}")
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it, null) }
        WidgetRefreshCoordinator.refreshTasks(context, immediate = true)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        BetaLogger.init(context)
        BetaLogger.log("TaskWidget", "size changed widgetId=$appWidgetId options=$newOptions")
        updateWidget(context, appWidgetManager, appWidgetId, null)
        WidgetRefreshCoordinator.refreshTasks(context, immediate = true)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        BetaLogger.init(context)
        BetaLogger.log("TaskWidget", "onDeleted widgetIds=${appWidgetIds.joinToString()}")
        appWidgetIds.forEach { TaskWidgetConfigStore.clear(context, it) }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        BetaLogger.init(context)
        super.onReceive(context, intent)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, INVALID_WIDGET_ID)
        BetaLogger.log("TaskWidget", "onReceive action=${intent.action} widgetId=$widgetId")
        when (intent.action) {
            ACTION_REFRESH -> {
                WidgetRefreshCoordinator.refreshTasks(context, immediate = true)
                showRefreshSuccess(context, widgetId)
            }
            ACTION_SCOPE -> {
                if (widgetId >= 0) {
                    context.startActivity(Intent(context, TaskWidgetConfigActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    })
                }
            }
            ACTION_ADD_TASK -> addTask(context, widgetId)
            ACTION_TOGGLE_TASK -> {
                val path = intent.getStringExtra(EXTRA_TASK_PATH).orEmpty()
                val lineIndex = intent.getIntExtra(EXTRA_TASK_LINE, -1)
                val expectedRaw = intent.getStringExtra(EXTRA_TASK_RAW).orEmpty()
                if (path.isNotBlank() && lineIndex >= 0) {
                    val pendingResult = goAsync()
                    WidgetAsyncWorkRunner.launch(
                        finishable = WidgetAsyncFinishable { pendingResult.finish() },
                    ) {
                        toggleTask(context.applicationContext, path, lineIndex, expectedRaw)
                    }
                } else {
                    BetaLogger.log("TaskWidget", "toggle ignored invalid path=$path line=$lineIndex")
                }
            }
            ACTION_MIDNIGHT_REFRESH -> refreshAllWidgets(context, immediate = true)
        }
    }

    companion object {
        internal const val ACTION_TOGGLE_TASK = "com.quickdaily.TOGGLE_TASK"
        private const val ACTION_REFRESH = "com.quickdaily.REFRESH_TASKS"
        private const val ACTION_SCOPE = "com.quickdaily.SELECT_TASK_SCOPE"
        private const val ACTION_ADD_TASK = "com.quickdaily.ADD_TASK"
        private const val ACTION_MIDNIGHT_REFRESH = "com.quickdaily.TASK_MIDNIGHT_REFRESH"
        internal const val EXTRA_TASK_PATH = "task_path"
        internal const val EXTRA_TASK_LINE = "task_line"
        internal const val EXTRA_TASK_RAW = "task_raw"
        private const val INVALID_WIDGET_ID = -1

        fun scheduleMidnightRefresh(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, TaskWidget::class.java).apply { action = ACTION_MIDNIGHT_REFRESH }
                val pi = PendingIntent.getBroadcast(
                    context,
                    888,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val calendar = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 1)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pi,
                )
            } catch (_: Exception) {
            }
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
            result: WidgetLoadResult<List<TaskWidgetItem>>?,
        ) {
            val config = TaskWidgetConfigStore.load(context, widgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_tasks)
            val appearance = WidgetAppearance.colors(context)
            val size = WidgetSizePolicy.forWidget(appWidgetManager, widgetId)
            BetaLogger.log(
                "TaskWidget/Render",
                "widgetId=$widgetId scope=${config.scope.key} customPath=${config.customRelativePath} size=$size result=${result?.javaClass?.simpleName ?: "loading"}",
            )
            WidgetAppearance.applyRoot(views, R.id.widget_root, appearance)
            WidgetSizePolicy.applyTaskChrome(views, size)

            val title = if (config.scope == TaskWidgetScope.CUSTOM) {
                TaskWidgetConfigStore.displayName(config).ifBlank { config.scope.label }
            } else {
                config.scope.label
            }
            views.setTextViewText(R.id.widget_title, title)
            views.setTextColor(R.id.widget_title, appearance.foreground)
            views.setTextColor(R.id.empty_view, appearance.muted)

            val effectiveResult = result ?: WidgetLoadResult.Empty("正在加载…")
            val status = when (effectiveResult) {
                is WidgetLoadResult.Success -> "暂无待办事项"
                is WidgetLoadResult.Empty -> effectiveResult.message
                is WidgetLoadResult.Failure -> effectiveResult.message
            }
            views.setTextViewText(R.id.empty_view, status)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val items = (effectiveResult as? WidgetLoadResult.Success)?.value.orEmpty()
                views.setRemoteAdapter(R.id.task_list, TaskWidgetViews.collection(context, items, size))
            } else {
                val serviceIntent = Intent(context, TaskWidgetService::class.java).apply {
                    data = Uri.parse("quickdaily://task-widget/$widgetId")
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                views.setRemoteAdapter(R.id.task_list, serviceIntent)
            }
            views.setEmptyView(R.id.task_list, R.id.empty_view)

            val clickIntent = Intent(context, TaskWidget::class.java).apply { action = ACTION_TOGGLE_TASK }
            val fillFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            views.setPendingIntentTemplate(
                R.id.task_list,
                PendingIntent.getBroadcast(context, widgetId, clickIntent, fillFlags),
            )

            val refreshIntent = Intent(context, TaskWidget::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            views.setOnClickPendingIntent(
                R.id.btn_refresh,
                PendingIntent.getBroadcast(
                    context,
                    widgetId + 200,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ),
            )

            val scopeIntent = Intent(context, TaskWidget::class.java).apply {
                action = ACTION_SCOPE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            views.setOnClickPendingIntent(
                R.id.btn_scope,
                PendingIntent.getBroadcast(
                    context,
                    widgetId + 250,
                    scopeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ),
            )

            val addTaskIntent = Intent(context, TaskWidget::class.java).apply {
                action = ACTION_ADD_TASK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            views.setOnClickPendingIntent(
                R.id.btn_add_task,
                PendingIntent.getBroadcast(
                    context,
                    widgetId + 300,
                    addTaskIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ),
            )

            val homeIntent = MainActivity.editorIntent(
                context,
                TaskWidgetConfigStore.customFilePath(context, config)
            )
            val homePi = PendingIntent.getActivity(
                context,
                widgetId + 100,
                homeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.btn_home, homePi)
            views.setOnClickPendingIntent(R.id.widget_title, homePi)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        fun refreshAllWidgets(context: Context, immediate: Boolean = false) {
            WidgetRefreshCoordinator.refreshTasks(context, immediate)
        }

        internal suspend fun refreshNow(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, TaskWidget::class.java))
                BetaLogger.log("TaskWidget", "refreshNow widgetCount=${ids.size} widgetIds=${ids.joinToString()}")
                ids.forEach { widgetId ->
                    val config = TaskWidgetConfigStore.load(context, widgetId)
                    val result = WidgetContentLoader.loadTasks(context, config)
                    logWidgetResult("TaskWidget[$widgetId]", result)
                    updateWidget(context, manager, widgetId, result)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        manager.notifyAppWidgetViewDataChanged(intArrayOf(widgetId), R.id.task_list)
                    }
                }
            } catch (e: Exception) {
                BetaLogger.log(
                    "TaskWidget",
                    "refreshNow failed exception=${e.javaClass.simpleName} message=${e.message}",
                )
            }
        }

        private fun addTask(context: Context, widgetId: Int) {
            val config = if (widgetId >= 0) {
                TaskWidgetConfigStore.load(context, widgetId)
            } else {
                TaskWidgetConfig()
            }
            val target = if (config.scope == TaskWidgetScope.CUSTOM) {
                TaskWidgetConfigStore.customFilePath(context, config)
            } else {
                null
            }
            if (config.scope == TaskWidgetScope.CUSTOM && target.isNullOrBlank()) {
                Toast.makeText(context, "自定义页面不可用", Toast.LENGTH_SHORT).show()
                return
            }

            val title = target?.let { TaskWidgetConfigStore.displayName(config) + " 速记" }
            val request = FloatingNoteRequest(
                source = FloatingNoteSource.WIDGET,
                prefillText = "- [ ] ",
                returnToHomeAfterClose = false,
                targetRelativePath = target,
                displayTitle = title,
            )
            if (target != null && FloatingNoteControllerProvider.forContext(context).showOrFocus(request)) {
                return
            }

            context.startActivity(Intent(context, NoteEditActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("prefill_text", "- [ ] ")
                putExtra(NoteEditActivity.EXTRA_TARGET_RELATIVE_PATH, target.orEmpty())
                putExtra(NoteEditActivity.EXTRA_DIALOG_TITLE, title.orEmpty())
            })
        }

        private fun showRefreshSuccess(context: Context, widgetId: Int) {
            if (widgetId < 0) return
            try {
                val manager = AppWidgetManager.getInstance(context)
                val views = RemoteViews(context.packageName, R.layout.widget_tasks)
                views.setTextViewText(R.id.widget_title, "刷新成功")
                views.setTextColor(R.id.widget_title, WidgetAppearance.colors(context).foreground)
                manager.partiallyUpdateAppWidget(widgetId, views)
                Handler(Looper.getMainLooper()).postDelayed({ refreshAllWidgets(context) }, 1_600L)
            } catch (e: Exception) {
                BetaLogger.log("TaskWidget", "refresh feedback failed exception=${e.javaClass.simpleName}")
            }
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
                logTag = "TaskWidget",
            )
            if (!result.succeeded) {
                if (result.failureReason == "stale_line") {
                    WidgetRefreshCoordinator.refreshTasks(context, immediate = true)
                }
                return
            }

            if (result.beforeChecked == false && TaskCompletionSoundPolicy.shouldPlay(
                    enabled = context.getSharedPreferences("QuickDaily", 0).getBoolean(
                        TaskCompletionSoundPolicy.PREF_KEY,
                        TaskCompletionSoundPolicy.DEFAULT_ENABLED,
                    ),
                    saveSucceeded = true,
                )) {
                runCatching { MediaActionSound().play(MediaActionSound.FOCUS_COMPLETE) }
            }
            WidgetRefreshCoordinator.refreshAll(context, immediate = true)
        }
    }
}
