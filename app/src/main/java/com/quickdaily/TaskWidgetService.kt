package com.quickdaily

import android.util.Log
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.quickdaily.util.DateUtil
import com.quickdaily.util.FileUtil

class TaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TaskViewsFactory(applicationContext)
    }
}

class TaskViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private val tasks = mutableListOf<String>()

    override fun onCreate() { loadTasks() }

    override fun onDataSetChanged() { loadTasks() }

    override fun onDestroy() { tasks.clear() }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = RemoteViews(context.packageName, R.layout.widget_task_item)
        val taskLine = tasks.getOrNull(position) ?: return item
        val taskText = taskLine.replace("- [ ] ", "").replace("- [x] ", "").trim()
        item.setTextViewText(R.id.task_text, taskText)

        val fillIntent = Intent().apply {
            putExtra("task_index", position)
        }
        item.setOnClickFillInIntent(R.id.task_checkbox, fillIntent)

        return item
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private fun loadTasks() {
        tasks.clear()
        android.util.Log.d("QuickDaily", "TaskViewsFactory.loadTasks() 开始读取任务")
        try {
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            val vaultPath = prefs.getString("vault_path", "") ?: ""
            if (vaultPath.isBlank()) {
                android.util.Log.w("QuickDaily", "TaskViewsFactory: vaultPath 为空")
                return
            }
            val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
            val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
            val date = DateUtil.todayStr(dateFormat)
            val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/$date.md"
            val content = FileUtil.read(path)
            if (content.isEmpty()) {
                android.util.Log.w("QuickDaily", "TaskViewsFactory: 日记内容为空，path=$path")
                return
            }

            for (line in content.lines()) {
                if (line.contains("- [ ] ")) {
                    tasks.add(line.trim())
                }
            }
            android.util.Log.d("QuickDaily", "TaskViewsFactory: 找到 ${tasks.size} 个待办任务")
        } catch (e: Exception) {
            android.util.Log.e("QuickDaily", "TaskViewsFactory.loadTasks() 异常", e)
        }
    }
}
