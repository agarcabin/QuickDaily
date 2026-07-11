package com.quickdaily

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaActionSound
import android.os.Build
import android.widget.RemoteViews
import com.quickdaily.util.DateUtil
import com.quickdaily.util.FileUtil

class TaskWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_REFRESH == intent.action) {
            flashRefreshFeedback(context)
        } else if (ACTION_ADD_TASK == intent.action) {
            val intent2 = Intent(context, NoteEditActivity::class.java).apply {
                putExtra("prefill_text", "- [ ] ")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent2)
        } else if (ACTION_TOGGLE_TASK == intent.action) {
            val taskIdx = intent.getIntExtra(EXTRA_TASK_INDEX, -1)
            if (taskIdx >= 0) {
                toggleTask(context, taskIdx)
            }
        }
    }

    companion object {
        private const val ACTION_TOGGLE_TASK = "com.quickdaily.TOGGLE_TASK"
        private const val ACTION_REFRESH = "com.quickdaily.REFRESH_TASKS"
        private const val ACTION_ADD_TASK = "com.quickdaily.ADD_TASK"
        private const val EXTRA_TASK_INDEX = "task_index"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_tasks)

            // Use RemoteViewsService for the list
            val serviceIntent = Intent(context, TaskWidgetService::class.java)
            views.setRemoteAdapter(R.id.task_list, serviceIntent)
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

        fun refreshAllWidgets(context: Context) {
            android.util.Log.d("QuickDaily", "TaskWidget.refreshAllWidgets")
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TaskWidget::class.java)
            manager.notifyAppWidgetViewDataChanged(manager.getAppWidgetIds(component), R.id.task_list)
        }

        private fun toggleTask(context: Context, taskIndex: Int) {
            // 完成任务播放 ding 声
            try {
                MediaActionSound().play(MediaActionSound.FOCUS_COMPLETE)
            } catch (_: Exception) {}
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            val vaultPath = prefs.getString("vault_path", "") ?: ""
            if (vaultPath.isBlank()) return
            val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
            val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"

            val date = DateUtil.todayStr(dateFormat)
            val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/$date.md"
            val content = FileUtil.read(path)
            if (content.isEmpty()) return

            val lines = content.lines().toMutableList()
            var foundIdx = -1
            var taskLineIdx = 0
            for (i in lines.indices) {
                if (lines[i].contains("- [ ] ")) {
                    if (taskLineIdx == taskIndex) {
                        foundIdx = i
                        break
                    }
                    taskLineIdx++
                }
            }
            if (foundIdx < 0) return

            // Change - [ ] to - [x]
            val oldLine = lines[foundIdx]
            lines[foundIdx] = oldLine.replaceFirst("- [ ] ", "- [x] ")
            FileUtil.write(path, lines.joinToString("\n"))

            // Refresh widget
            refreshAllWidgets(context)
        }
        /**
         * 刷新时在 widget 标题上显示 "✓ 已刷新" 反馈（比 Toast 可靠，兼容国产 ROM）。
         * 1 秒后自动恢复并刷新列表。
         */
        private fun flashRefreshFeedback(context: Context) {
            android.util.Log.d("QuickDaily", "flashRefreshFeedback")
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TaskWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) {
                updateWidget(context, manager, id)
                val views = RemoteViews(context.packageName, R.layout.widget_tasks)
                views.setTextViewText(R.id.widget_title, "✓ 已刷新")
                manager.updateAppWidget(id, views)
            }
            // 延迟刷新任务列表
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val mgr = AppWidgetManager.getInstance(context)
                val comp = ComponentName(context, TaskWidget::class.java)
                for (id in mgr.getAppWidgetIds(comp)) {
                    updateWidget(context, mgr, id)
                }
            }, 800L)
        }
    }
}
