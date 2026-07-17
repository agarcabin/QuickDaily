// QuickDailyReadWidget.kt
package com.quickdaily

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaActionSound
import android.os.Build
import android.widget.RemoteViews
import com.quickdaily.BetaLogger
import com.quickdaily.util.DateUtil
import com.quickdaily.util.FileUtil
import com.quickdaily.util.ContentUtil

class QuickDailyReadWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            try { updateWidget(ctx, manager, id) } catch (_: Exception) { }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        try { super.onReceive(context, intent) } catch (_: Exception) { }
        if (ACTION_TOGGLE_MARKDOWN == intent.action) {
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            val current = prefs.getBoolean("render_markdown", true)
            prefs.edit().putBoolean("render_markdown", !current).commit()
            refreshAllWidgets(context)
    } else if (ACTION_MIDNIGHT_REFRESH == intent.action) {
        refreshAllWidgets(context)
    } else if (ACTION_TOGGLE_TASK == intent.action) {
        val taskIdx = intent.getIntExtra("task_index", -1)
        if (taskIdx >= 0) {
            toggleTaskInDiary(context, taskIdx)
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

        fun refreshAllWidgets(context: Context) {
            try {
                BetaLogger.log("ReadWidget", "refreshAllWidgets called")
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, QuickDailyReadWidget::class.java)
                manager.notifyAppWidgetViewDataChanged(manager.getAppWidgetIds(component), R.id.content_list)
                BetaLogger.log("ReadWidget", "notifyAppWidgetViewDataChanged sent")
            } catch (_: Exception) { }
        }

        private fun toggleTaskInDiary(context: Context, lineIndex: Int) {
            try {
                val prefs = context.getSharedPreferences("QuickDaily", 0)
                val vaultPath = prefs.getString("vault_path", "") ?: ""
                if (vaultPath.isBlank()) return
                val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
                val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
                val date = DateUtil.todayStr(dateFormat)
                val path = vaultPath.trimEnd('/') + "/" + diaryFolder.trimEnd('/') + "/" + date + ".md"
                val content = FileUtil.read(path)
                if (content.isEmpty()) return

                val filterFm = prefs.getBoolean("filter_frontmatter", false)
                val parsed = ContentUtil.parseFrontmatter(content)
                val displayContent = if (filterFm && parsed.hasFrontmatter) parsed.body else content

                val displayLines = displayContent.lines().toMutableList()
                if (lineIndex >= displayLines.size) return

                val targetLine = displayLines[lineIndex]
                val trimmed = targetLine.trim()

                val newLine = when {
                    trimmed.startsWith("- [ ] ") -> targetLine.replaceFirst("- [ ] ", "- [x] ")
                    trimmed.startsWith("- [ ]") -> targetLine.replaceFirst("- [ ]", "- [x]")
                    trimmed.startsWith("- [x] ") -> targetLine.replaceFirst("- [x] ", "- [ ] ")
                    trimmed.startsWith("- [X] ") -> targetLine.replaceFirst("- [X] ", "- [ ] ")
                    trimmed.startsWith("- [x]") -> targetLine.replaceFirst("- [x]", "- [ ]")
                    trimmed.startsWith("- [X]") -> targetLine.replaceFirst("- [X]", "- [ ]")
                    else -> return
                }

                displayLines[lineIndex] = newLine
                val newDisplayContent = displayLines.joinToString("\n")
                val saveContent = if (filterFm && parsed.hasFrontmatter) {
                    ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, newDisplayContent)
                } else {
                    newDisplayContent
                }

                FileUtil.write(path, saveContent)

                // Refresh both widgets
                refreshAllWidgets(context)
                TaskWidget.refreshAllWidgets(context)
            } catch (_: Exception) { }
        }

        private fun updateWidget(ctx: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_diary_read)

            // Home button
            val homeIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val homePi = PendingIntent.getActivity(ctx, widgetId + 100, homeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_home, homePi)

            // Add button
            val addIntent = Intent(ctx, NoteEditActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
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

            // Connect to RemoteViewsService (one call per refresh = one onDataSetChanged)
            val serviceIntent = Intent(ctx, QuickDailyReadWidgetService::class.java)
            views.setRemoteAdapter(R.id.content_list, serviceIntent)
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
