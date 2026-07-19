package com.quickdaily

import android.util.Log
import com.quickdaily.BetaLogger
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.quickdaily.util.DateUtil
import com.quickdaily.util.FileUtil

class TaskWidgetService : RemoteViewsService() {
    companion object {
        /** AppWidget hosts create one factory per widget; serialize their shared vault read. */
        internal val taskLoadLock = Any()
    }
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        BetaLogger.log("TaskWidgetSvc", "factory created widgetId=${intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)} data=${intent.data}")
        return TaskViewsFactory(applicationContext)
    }
}

class TaskViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private data class TaskEntry(val text: String, val date: String, val indexInDiary: Int)
    private val tasks = mutableListOf<TaskEntry>()

    override fun onCreate() { synchronized(TaskWidgetService.taskLoadLock) { loadTasks() } }

    override fun onDataSetChanged() { 
        BetaLogger.log("TaskWidgetSvc", "onDataSetChanged start")
        synchronized(TaskWidgetService.taskLoadLock) { loadTasks() }
        BetaLogger.log("TaskWidgetSvc", "onDataSetChanged complete tasks=" + tasks.size)
    }

    override fun onDestroy() { tasks.clear() }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = RemoteViews(context.packageName, R.layout.widget_task_item)
        val task = tasks.getOrNull(position) ?: return item
        val taskText = task.text.replace("- [ ] ", "").replace("- [x] ", "").replace("- [X] ", "").trim()
        item.setTextViewText(R.id.task_text, taskText)
        item.setTextColor(R.id.task_text, WidgetAppearance.colors(context).foreground)

        val fillIntent = Intent().apply {
            putExtra("task_index", task.indexInDiary)
            putExtra("task_date", task.date)
        }
        item.setOnClickFillInIntent(R.id.task_checkbox, fillIntent)

        return item
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private fun loadTasks() {
        val startMs = System.currentTimeMillis()
        tasks.clear()
        BetaLogger.log("TaskWidgetSvc", "loadTasks start")
        android.util.Log.d("QuickDaily", "TaskViewsFactory.loadTasks() start")
        try {
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            val vaultPath = prefs.getString("vault_path", "") ?: ""
            if (vaultPath.isBlank()) {
                BetaLogger.log("TaskWidgetSvc", "loadTasks vaultPath blank")
                android.util.Log.w("QuickDaily", "TaskViewsFactory: vaultPath blank")
                return
            }
            val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
            val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
            val taskPeriod = prefs.getString("task_period", "today") ?: "today"
            val daysToLoad = when (taskPeriod) {
                "week" -> 7
                "month" -> 30
                else -> 1
            }
            for (i in 0 until daysToLoad) {
                val dateStr = DateUtil.dateStr(dateFormat, -i.toLong())
                val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/$dateStr.md"
                BetaLogger.log("TaskWidgetSvc", "checking path=$path")
                val fileContent = FileUtil.read(path)
                if (fileContent.isEmpty()) continue

                var taskIndex = 0
                for (line in fileContent.lines()) {
                    if (line.contains("- [ ] ")) {
                        tasks.add(TaskEntry(line.trim(), dateStr, taskIndex))
                        taskIndex++
                    }
                }
            }
            android.util.Log.d("QuickDaily", "TaskViewsFactory: 找到 ${tasks.size} 个待办任务")
        } catch (e: Exception) {
            android.util.Log.e("QuickDaily", "TaskViewsFactory.loadTasks() 异常", e)
        }
    }
}
