package com.quickdaily

import android.app.PendingIntent
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.MediaActionSound
import android.os.Build
import android.widget.RemoteViews
import com.quickdaily.util.DateUtil
import com.quickdaily.util.ContentUtil
import com.quickdaily.BetaLogger
import com.quickdaily.util.FileUtil

class TaskWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        BetaLogger.log("TaskWidget", "onUpdate widgetIds=${appWidgetIds.joinToString()}")
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id, null)
        }
        WidgetRefreshCoordinator.refreshTasks(context, immediate = true)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        BetaLogger.log("TaskWidget", "size changed widgetId=$appWidgetId options=$newOptions")
        updateWidget(context, appWidgetManager, appWidgetId, null)
        WidgetRefreshCoordinator.refreshTasks(context, immediate = true)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        BetaLogger.log("TaskWidget", "onReceive action=${intent.action} widgetId=${intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)}")
        if (ACTION_REFRESH == intent.action) {
            android.util.Log.d("QuickDaily", "Refresh pressed")
            WidgetRefreshCoordinator.refreshTasks(context, immediate = true)
            BetaLogger.log("TaskWidget", "manual refresh queued")
            // MIUI suppresses background BroadcastReceiver toasts. Show the same
            // confirmation inside the widget, where the launcher cannot suppress it.
            showRefreshSuccess(context)
        } else if (ACTION_ADD_TASK == intent.action) {
            val intent2 = Intent(context, NoteEditActivity::class.java).apply {
                putExtra("prefill_text", "- [ ] ")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent2)
        } else if (ACTION_TOGGLE_TASK == intent.action) {
            val taskIdx = intent.getIntExtra(EXTRA_TASK_INDEX, -1)
            val taskDate = intent.getStringExtra(EXTRA_TASK_DATE)
            if (taskIdx >= 0 && !taskDate.isNullOrBlank()) {
                BetaLogger.log("TaskWidget", "toggle requested date=$taskDate taskIndex=$taskIdx")
                toggleTask(context, taskDate, taskIdx)
            } else {
                BetaLogger.log("TaskWidget", "toggle ignored invalid date=$taskDate taskIndex=$taskIdx")
            }
        } else if (ACTION_MIDNIGHT_REFRESH == intent.action) {
            refreshAllWidgets(context, immediate = true)
        }
    }

    companion object {
        private const val ACTION_TOGGLE_TASK = "com.quickdaily.TOGGLE_TASK"
        private const val ACTION_REFRESH = "com.quickdaily.REFRESH_TASKS"
        private const val ACTION_ADD_TASK = "com.quickdaily.ADD_TASK"
        private const val ACTION_MIDNIGHT_REFRESH = "com.quickdaily.TASK_MIDNIGHT_REFRESH"
        private const val EXTRA_TASK_INDEX = "task_index"
        private const val EXTRA_TASK_DATE = "task_date"

        fun scheduleMidnightRefresh(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, TaskWidget::class.java).apply { action = ACTION_MIDNIGHT_REFRESH }
                val pi = PendingIntent.getBroadcast(context, 888, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val calendar = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 1)
                    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                }
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
            } catch (_: Exception) { }
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
            result: WidgetLoadResult<List<TaskWidgetItem>>?
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_tasks)
            val appearance = WidgetAppearance.colors(context)
            val size = WidgetSizePolicy.forWidget(appWidgetManager, widgetId)
            WidgetAppearance.applyRoot(views, R.id.widget_root, appearance)
            WidgetSizePolicy.applyTaskChrome(views, size)

            // Set title based on task period
            val prefs_ = context.getSharedPreferences("QuickDaily", 0)
            val taskPeriod_ = prefs_.getString("task_period", "today") ?: "today"
            val title_ = when (taskPeriod_) {
                "week" -> "本周任务"
                "month" -> "本月任务"
                else -> "今日任务"
            }
            views.setTextViewText(R.id.widget_title, title_)
            views.setTextColor(R.id.widget_title, appearance.foreground)
            views.setTextColor(R.id.empty_view, appearance.muted)

            val effectiveResult = result ?: WidgetLoadResult.Empty("正在加载…")
            val status = when (effectiveResult) {
                is WidgetLoadResult.Success -> "暂无待办事项"
                is WidgetLoadResult.Empty -> effectiveResult.message
                is WidgetLoadResult.Failure -> effectiveResult.message
            }
            views.setTextViewText(R.id.empty_view, status)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val items = (effectiveResult as? WidgetLoadResult.Success)?.value.orEmpty()
                views.setRemoteAdapter(R.id.task_list, TaskWidgetViews.collection(context, items, size))
            } else {
                // API 26-30 fallback: bind the RemoteViewsService with BIND_REMOTEVIEWS.
                val serviceIntent = Intent(context, TaskWidgetService::class.java).apply {
                    data = Uri.parse("quickdaily://task-widget/$widgetId")
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                views.setRemoteAdapter(R.id.task_list, serviceIntent)
            }
            // 设置 emptyView，在某些国产 ROM 上不自动关联
            views.setEmptyView(R.id.task_list, R.id.empty_view)

            // Template for click intents on items
            val clickIntent = Intent(context, TaskWidget::class.java)
            clickIntent.action = ACTION_TOGGLE_TASK
            val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pi = PendingIntent.getBroadcast(context, widgetId, clickIntent, piFlags)
            views.setPendingIntentTemplate(R.id.task_list, pi)

            // Refresh button
            val refreshIntent = Intent(context, TaskWidget::class.java).apply { action = ACTION_REFRESH }
            val refreshPi = PendingIntent.getBroadcast(
                context, widgetId + 200, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refreshPi)

            // Add task button
            val addTaskIntent = Intent(context, TaskWidget::class.java).apply { action = ACTION_ADD_TASK }
            val addTaskPi = PendingIntent.getBroadcast(
                context, widgetId + 300, addTaskIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_add_task, addTaskPi)

            // Tap on title opens diary
            val diaryIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val diaryPi = PendingIntent.getActivity(
                context, widgetId + 100, diaryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, diaryPi)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        fun refreshAllWidgets(context: Context, immediate: Boolean = false) {
            WidgetRefreshCoordinator.refreshTasks(context, immediate)
        }

        internal suspend fun refreshNow(context: Context) {
            try {
            android.util.Log.d("QuickDaily", "TaskWidget.refreshAllWidgets")
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TaskWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            val period = context.getSharedPreferences("QuickDaily", 0)
                .getString("task_period", "today") ?: "today"
            BetaLogger.log("TaskWidget", "refreshNow widgetCount=${ids.size} period=$period widgetIds=${ids.joinToString()}")
            val result = WidgetContentLoader.loadTasks(context)
            logWidgetResult("TaskWidget", result)
            for (aid in ids) {
                updateWidget(context, manager, aid, result)
            }
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                manager.notifyAppWidgetViewDataChanged(ids, R.id.task_list)
                BetaLogger.log("TaskWidget", "notifyDataChanged sent widgetCount=${ids.size}")
            } else {
                BetaLogger.log("TaskWidget", "direct collection submitted widgetCount=${ids.size}")
            }
            } catch (e: Exception) {
                BetaLogger.log("TaskWidget", "refreshNow failed exception=${e.javaClass.simpleName} message=${e.message}")
            }
        }

        private fun showRefreshSuccess(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, TaskWidget::class.java))
                val colors = WidgetAppearance.colors(context)
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_tasks)
                    views.setTextViewText(R.id.widget_title, "刷新成功")
                    views.setTextColor(R.id.widget_title, colors.foreground)
                    manager.partiallyUpdateAppWidget(id, views)
                }
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    refreshAllWidgets(context)
                }, 1_600L)
            } catch (e: Exception) {
                BetaLogger.log("TaskWidget", "refresh feedback failed exception=${e.javaClass.simpleName}")
            }
        }

        private fun toggleTask(context: Context, date: String, taskIndex: Int) {
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            val vaultPath = prefs.getString("vault_path", "") ?: ""
            if (vaultPath.isBlank()) {
                BetaLogger.log("TaskWidget", "toggle aborted: vault path blank")
                return
            }
            val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
            val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/$date.md"
            val content = FileUtil.read(path)
            if (content.isEmpty()) {
                BetaLogger.log("TaskWidget", "toggle aborted: diary empty path=$path")
                return
            }

            val parsed = ContentUtil.parseFrontmatter(content)
            val body = if (parsed.hasFrontmatter) parsed.body else content
            val lines = body.lines().toMutableList()
            var foundIdx = -1
            var taskLineIdx = 0
            for (i in lines.indices) {
                if (lines[i].trimStart().startsWith("- [ ]")) {
                    if (taskLineIdx == taskIndex) {
                        foundIdx = i
                        break
                    }
                    taskLineIdx++
                }
            }
            if (foundIdx < 0) {
                BetaLogger.log("TaskWidget", "toggle aborted: task index no longer matches date=$date taskIndex=$taskIndex")
                return
            }

            // Change - [ ] to - [x]
            val oldLine = lines[foundIdx]
            lines[foundIdx] = oldLine.replaceFirst("- [ ] ", "- [X] ")
            val newBody = lines.joinToString("\n")
            val saveContent = if (parsed.hasFrontmatter) {
                ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, newBody)
            } else {
                newBody
            }
            val saveSucceeded = FileUtil.write(path, saveContent)
            if (!saveSucceeded) {
                BetaLogger.log("TaskWidget", "toggle failed to write date=$date taskIndex=$taskIndex path=$path")
                return
            }
            BetaLogger.log("TaskWidget", "toggle saved date=$date taskIndex=$taskIndex line=$foundIdx path=$path")

            if (TaskCompletionSoundPolicy.shouldPlay(
                    enabled = prefs.getBoolean(
                        TaskCompletionSoundPolicy.PREF_KEY,
                        TaskCompletionSoundPolicy.DEFAULT_ENABLED
                    ),
                    saveSucceeded = saveSucceeded
                )) {
                try {
                    MediaActionSound().play(MediaActionSound.FOCUS_COMPLETE)
                } catch (_: Exception) { }
            }

            // Refresh all widgets
            WidgetRefreshCoordinator.refreshAll(context, immediate = true)
        }
    }
}
