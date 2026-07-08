package com.quickdaily

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import com.quickdaily.util.DateUtil
import com.quickdaily.util.FileUtil
import com.quickdaily.util.ContentUtil

class QuickDailyWidget : AppWidgetProvider() {

    companion object {
        /** 刷新所有桌面便签小部件。线程安全：可在任意线程调用。 */
        fun updateAllWidgets(context: Context) {
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                // RemoteViews/AppWidgetManager 必须在主线程调用
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    doUpdateAllWidgets(context)
                }
            } else {
                doUpdateAllWidgets(context)
            }
        }

        private fun doUpdateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, QuickDailyWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) updateWidget(context, manager, id)
        }

        private fun updateWidget(ctx: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = ctx.getSharedPreferences("QuickDaily", 0)
            val vaultPath = prefs.getString("vault_path", "") ?: ""
            val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
            val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
            val views = RemoteViews(ctx.packageName, R.layout.widget_diary)

            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(ctx, widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_content, pi)
            views.setOnClickPendingIntent(R.id.widget_title, pi)

            if (vaultPath.isBlank()) {
                views.setTextViewText(R.id.widget_title, "QuickDaily")
                views.setTextViewText(R.id.widget_content, "请先设置仓库路径")
                manager.updateAppWidget(widgetId, views)
                return
            }

            val date = DateUtil.todayStr(dateFormat)

            val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/$date.md"
            val content = FileUtil.read(path)
            val displayContent = if (prefs.getBoolean("filter_frontmatter", false)) {
                ContentUtil.stripFrontmatter(content)
            } else {
                content
            }

            views.setTextViewText(R.id.widget_title, date)
            views.setTextViewText(R.id.widget_content, displayContent.ifEmpty { "(空白日记)" })

            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(ctx, manager, id)
    }
}
