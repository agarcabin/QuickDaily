package com.quickdaily

import com.quickdaily.BetaLogger

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.quickdaily.util.DateUtil
import com.quickdaily.util.FileUtil
import com.quickdaily.util.ReadResult
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.text.style.StrikethroughSpan
import android.text.style.TypefaceSpan
import android.text.style.ForegroundColorSpan
import android.graphics.Color
import android.graphics.Typeface
import android.text.Html
import android.text.TextUtils
import com.quickdaily.util.ContentUtil

class QuickDailyReadWidgetService : RemoteViewsService() {
    companion object {
        /** Prevent separate widget factories from reading the same diary concurrently. */
        internal val readLoadLock = Any()
    }
    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory {
        BetaLogger.log("ReadWidget", "factory created widgetId=${intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)} data=${intent.data}")
        return ReadViewsFactory(applicationContext)
    }
}

class ReadViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private val lines = mutableListOf<ReadLine>()

    data class ReadLine(
        val type: String,
        val text: String = "",
        val level: Int = 1,
        val checked: Boolean = false,
        /** Source mode must preserve raw Markdown, including unstyled #tags. */
        val renderInlineMarkdown: Boolean = true
    )

    override fun onCreate() { synchronized(QuickDailyReadWidgetService.readLoadLock) { loadContent() } }

    @Synchronized
    override fun onDataSetChanged() {
        BetaLogger.log("ReadWidget", "onDataSetChanged start")
        android.util.Log.d("QD-ReadWidget", "onDataSetChanged")
        synchronized(QuickDailyReadWidgetService.readLoadLock) { loadContent() }
        BetaLogger.log("ReadWidget", "onDataSetChanged complete lines=" + lines.size)
    }

    override fun onDestroy() { lines.clear() }

    override fun getCount(): Int = lines.size

    override fun getViewAt(position: Int): RemoteViews {
        try {
            val colors = WidgetAppearance.colors(context)
            val item = lines.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_diary_read_line)
            when (item.type) {
                "task" -> {
                    val rv = RemoteViews(context.packageName, R.layout.widget_diary_read_task)
                    rv.setTextViewText(R.id.task_text, item.text)
                    rv.setFloat(R.id.task_text, "setTextSize", 12f)
                    if (item.checked) {
                        rv.setTextColor(R.id.task_text, colors.muted)
                    } else {
                        rv.setTextColor(R.id.task_text, colors.foreground)
                    }
                    rv.setImageViewResource(R.id.task_checkbox,
                        if (item.checked) android.R.drawable.checkbox_on_background
                        else android.R.drawable.checkbox_off_background)
                    val fillIntent = Intent().apply { putExtra("task_index", position) }
                    rv.setOnClickFillInIntent(R.id.task_row, fillIntent)
                    rv.setOnClickFillInIntent(R.id.task_checkbox, fillIntent)
                    return rv
                }
                "heading" -> {
                    val rv = RemoteViews(context.packageName, R.layout.widget_diary_read_line)
                    val textSize = when (item.level) { 1 -> 18f; 2 -> 16f; 3 -> 14f; else -> 13f }
                    rv.setTextViewText(R.id.task_text, item.text)
                    rv.setTextColor(R.id.task_text, colors.foreground)
                    rv.setFloat(R.id.task_text, "setTextSize", textSize)
                    return rv
                }
                "image" -> {
                    val rv = RemoteViews(context.packageName, R.layout.widget_diary_read_line)
                    rv.setFloat(R.id.task_text, "setTextSize", 12f)
                    rv.setTextViewText(R.id.task_text, "[\u56fe\u7247]")
                    rv.setTextColor(R.id.task_text, colors.muted)
                    return rv
                }
                "quote" -> {
                    val rv = RemoteViews(context.packageName, R.layout.widget_diary_read_line)
                    rv.setFloat(R.id.task_text, "setTextSize", 12f)
                    rv.setTextViewText(R.id.task_text, renderMarkdown(" \u258d " + item.text))
                    rv.setTextColor(R.id.task_text, colors.foreground)
                    return rv
                }
                "hr" -> {
                    val rv = RemoteViews(context.packageName, R.layout.widget_diary_read_line)
                    rv.setTextViewText(R.id.task_text, " \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500")
                    rv.setTextColor(R.id.task_text, colors.muted)
                    return rv
                }
                "bullet" -> {
                    val rv = RemoteViews(context.packageName, R.layout.widget_diary_read_line)
                    rv.setFloat(R.id.task_text, "setTextSize", 12f)
                    rv.setTextViewText(R.id.task_text, android.text.TextUtils.concat("  \u2022  ", renderMarkdown(item.text)))
                    rv.setTextColor(R.id.task_text, colors.foreground)
                    return rv
                }
                "blank" -> {
                    val rv = RemoteViews(context.packageName, R.layout.widget_diary_read_line)
                    rv.setFloat(R.id.task_text, "setTextSize", 12f)
                    rv.setTextViewText(R.id.task_text, " ")
                    rv.setTextColor(R.id.task_text, colors.foreground)
                    return rv
                }
                else -> {
                    val rv = RemoteViews(context.packageName, R.layout.widget_diary_read_line)
                    rv.setFloat(R.id.task_text, "setTextSize", 12f)
                    rv.setTextViewText(R.id.task_text, if (item.renderInlineMarkdown) renderMarkdown(item.text) else item.text)
                    rv.setTextColor(R.id.task_text, colors.foreground)
                    return rv
                }
            }
        } catch (_: Exception) {
            return RemoteViews(context.packageName, R.layout.widget_diary_read_line)
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 3
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private fun renderMarkdown(text: String): CharSequence {
        val ssb = SpannableStringBuilder(text)
        for (match in Regex("""\*\*(.+?)\*\*""").findAll(ssb.toString()).toList().reversed()) {
            val content = match.groupValues[1]; ssb.replace(match.range.first, match.range.last + 1, content)
            ssb.setSpan(StyleSpan(Typeface.BOLD), match.range.first, match.range.first + content.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (match in Regex("""__(.+?)__""").findAll(ssb.toString()).toList().reversed()) {
            val content = match.groupValues[1]; ssb.replace(match.range.first, match.range.last + 1, content)
            ssb.setSpan(StyleSpan(Typeface.BOLD), match.range.first, match.range.first + content.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (match in Regex("""~~(.+?)~~""").findAll(ssb.toString()).toList().reversed()) {
            val content = match.groupValues[1]; ssb.replace(match.range.first, match.range.last + 1, content)
            ssb.setSpan(StrikethroughSpan(), match.range.first, match.range.first + content.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (match in Regex("""`(.+?)`""").findAll(ssb.toString()).toList().reversed()) {
            val content = match.groupValues[1]; ssb.replace(match.range.first, match.range.last + 1, content)
            ssb.setSpan(TypefaceSpan("monospace"), match.range.first, match.range.first + content.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (match in Regex("""\*(.+?)\*""").findAll(ssb.toString()).toList().reversed()) {
            val content = match.groupValues[1]; ssb.replace(match.range.first, match.range.last + 1, content)
            ssb.setSpan(StyleSpan(Typeface.ITALIC), match.range.first, match.range.first + content.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (match in Regex("""_(.+?)_""").findAll(ssb.toString()).toList().reversed()) {
            val content = match.groupValues[1]; ssb.replace(match.range.first, match.range.last + 1, content)
            ssb.setSpan(StyleSpan(Typeface.ITALIC), match.range.first, match.range.first + content.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Render inline diary tags in a stable blue, independent of widget theme.
        for (match in Regex("""(?<![\p{L}\p{N}_])#[\p{L}\p{N}_/-]+""").findAll(ssb.toString())) {
            ssb.setSpan(ForegroundColorSpan(Color.rgb(30, 136, 229)), match.range.first, match.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return ssb
    }

    @Synchronized
    private fun loadContent() {
        val startMs = System.currentTimeMillis()
        lines.clear()
        try {
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            val vaultPath = prefs.getString("vault_path", "") ?: ""
            if (vaultPath.isBlank()) {
                BetaLogger.log("ReadWidget", "loadContent vaultPath blank")
                publishEmptyStatus("无法读取日记")
                return
            }
            val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
            val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
            val date = DateUtil.todayStr(dateFormat)
            val path = vaultPath.trimEnd('/') + "/" + diaryFolder.trimEnd('/') + "/" + date + ".md"
            val content = when (val result = FileUtil.readResult(path)) {
                is ReadResult.Success -> result.content
                is ReadResult.NotFound -> {
                    BetaLogger.log("ReadWidget", "loadContent notFound path=$path")
                    publishEmptyStatus("暂无日记")
                    return
                }
                is ReadResult.Error -> {
                    BetaLogger.log("ReadWidget", "loadContent read error path=$path message=${result.exception.message}")
                    publishEmptyStatus("无法读取日记")
                    return
                }
            }
            if (content.isBlank()) {
                BetaLogger.log("ReadWidget", "loadContent empty path=" + path)
                publishEmptyStatus("暂无日记")
                return
            }

            val displayContent = if (prefs.getBoolean("filter_frontmatter", false)) {
                ContentUtil.stripFrontmatter(content)
            } else { content }
            if (displayContent.isBlank()) {
                publishEmptyStatus("暂无日记")
                return
            }

            val renderMd = prefs.getBoolean("render_markdown", true)
            if (!renderMd) {
                for (line in displayContent.lines()) {
                    val trimmed = line.trim()
                    lines.add(ReadLine("plain", if (trimmed.isEmpty()) " " else trimmed, renderInlineMarkdown = false))
                }
                publishEmptyStatus(if (lines.isEmpty()) "暂无日记" else null)
                return
            }

            for (line in displayContent.lines()) {
                val trimmed = line.trim()
                when {
                    trimmed.isEmpty() -> lines.add(ReadLine("blank"))
                    trimmed.startsWith("#### ") -> lines.add(ReadLine("heading", trimmed.drop(5).trimStart(), 4))
                    trimmed.startsWith("### ") -> lines.add(ReadLine("heading", trimmed.drop(4).trimStart(), 3))
                    trimmed.startsWith("## ") -> lines.add(ReadLine("heading", trimmed.drop(3).trimStart(), 2))
                    trimmed.startsWith("# ") -> lines.add(ReadLine("heading", trimmed.drop(2).trimStart(), 1))
                    trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]") -> lines.add(ReadLine("task", trimmed.drop(6).trimStart(), checked = true))
                    trimmed.startsWith("- [ ]") -> lines.add(ReadLine("task", trimmed.drop(6).trimStart(), checked = false))
                    trimmed.startsWith("> ") -> lines.add(ReadLine("quote", trimmed.drop(2).trimStart()))
                    Regex("""^[-*_]{3,}$""").matches(trimmed) -> lines.add(ReadLine("hr"))
                    trimmed.startsWith("- ") -> lines.add(ReadLine("bullet", trimmed.drop(2).trimStart()))
                    Regex("""^\d+\.""").containsMatchIn(trimmed) -> {
                        val text = trimmed.dropWhile { it != ' ' }.trimStart()
                        lines.add(ReadLine("bullet", text))
                    }
                    Regex("""^!\[.*\]\(.*\)""").containsMatchIn(trimmed) -> {
                        val alt = trimmed.substringAfter("![").substringBefore("](")
                        val p = trimmed.substringAfter("](").substringBefore(")")
                        lines.add(ReadLine("image", if (p.isNotEmpty()) p.substringAfterLast("/") else alt))
                    }
                    else -> lines.add(ReadLine("plain", trimmed))
                }
            }
            publishEmptyStatus(if (lines.isEmpty()) "暂无日记" else null)
        } catch (e: Exception) {
            BetaLogger.log("ReadWidget", "loadContent exception=${e.javaClass.simpleName} message=${e.message}")
            publishEmptyStatus("无法读取日记")
        }
        val elapsed = System.currentTimeMillis() - startMs
        BetaLogger.log("ReadWidget", "loadContent done lines=" + lines.size + " elapsed=" + elapsed + "ms")
    }

    private fun publishEmptyStatus(message: String?) {
        if (message == null) return
        try {
            val manager = android.appwidget.AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(android.content.ComponentName(context, QuickDailyReadWidget::class.java))
            if (ids.isNotEmpty()) {
                val views = RemoteViews(context.packageName, R.layout.widget_diary_read)
                views.setTextViewText(R.id.empty_view, message)
                views.setTextColor(R.id.empty_view, WidgetAppearance.colors(context).muted)
                manager.partiallyUpdateAppWidget(ids, views)
            }
        } catch (e: Exception) {
            BetaLogger.log("ReadWidget", "publish empty status failed=${e.javaClass.simpleName}")
        }
    }
}
