package com.quickdaily

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.quickdaily.util.ContentUtil
import com.quickdaily.util.DateUtil
import com.quickdaily.util.FileUtil
import com.quickdaily.util.ReadResult

sealed interface WidgetLoadResult<out T> {
    data class Success<T>(val value: T, val sourcePath: String) : WidgetLoadResult<T>
    data class Empty(val message: String, val sourcePath: String? = null) : WidgetLoadResult<Nothing>
    data class Failure(
        val message: String,
        val sourcePath: String? = null,
        val exception: Throwable? = null
    ) : WidgetLoadResult<Nothing>
}

data class ReadWidgetItem(
    val type: String,
    val text: String = "",
    val level: Int = 1,
    val checked: Boolean = false,
    val renderInlineMarkdown: Boolean = true
)

data class TaskWidgetItem(
    val text: String,
    val date: String,
    val indexInDiary: Int
)

object WidgetContentLoader {
    fun loadRead(context: Context): WidgetLoadResult<List<ReadWidgetItem>> {
        return try {
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            val vaultPath = prefs.getString("vault_path", "") ?: ""
            if (vaultPath.isBlank()) return WidgetLoadResult.Empty("请先配置仓库")

            val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
            val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
            val date = DateUtil.todayStr(dateFormat)
            val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/$date.md"
            val content = when (val result = FileUtil.readResult(path)) {
                is ReadResult.Success -> result.content
                is ReadResult.NotFound -> return WidgetLoadResult.Empty("暂无日记", path)
                is ReadResult.Error -> return WidgetLoadResult.Failure("读取失败", path, result.exception)
            }
            if (content.isBlank()) return WidgetLoadResult.Empty("暂无日记", path)

            val displayContent = if (prefs.getBoolean("filter_frontmatter", false)) {
                ContentUtil.stripFrontmatter(content)
            } else {
                content
            }
            if (displayContent.isBlank()) return WidgetLoadResult.Empty("暂无日记", path)

            val renderMarkdown = prefs.getBoolean("render_markdown", true)
            val items = if (!renderMarkdown) {
                displayContent.lines().map { line ->
                    ReadWidgetItem("plain", if (line.trim().isEmpty()) " " else line.trim(), renderInlineMarkdown = false)
                }
            } else {
                displayContent.lines().map { line -> parseReadLine(line) }
            }
            if (items.isEmpty()) WidgetLoadResult.Empty("暂无日记", path)
            else WidgetLoadResult.Success(items, path)
        } catch (e: Exception) {
            WidgetLoadResult.Failure("读取失败", exception = e)
        }
    }

    fun loadTasks(context: Context): WidgetLoadResult<List<TaskWidgetItem>> {
        return try {
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            val vaultPath = prefs.getString("vault_path", "") ?: ""
            if (vaultPath.isBlank()) return WidgetLoadResult.Empty("请先配置仓库")

            val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
            val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
            val taskPeriod = prefs.getString("task_period", "today") ?: "today"
            val daysToLoad = when (taskPeriod) {
                "week" -> 7
                "month" -> 30
                else -> 1
            }
            val tasks = mutableListOf<TaskWidgetItem>()
            var lastPath: String? = null
            for (i in 0 until daysToLoad) {
                val date = DateUtil.dateStr(dateFormat, -i.toLong())
                val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/$date.md"
                lastPath = path
                val fileContent = when (val result = FileUtil.readResult(path)) {
                    is ReadResult.Success -> result.content
                    is ReadResult.NotFound -> continue
                    is ReadResult.Error -> return WidgetLoadResult.Failure("读取失败", path, result.exception)
                }
                if (fileContent.isEmpty()) continue

                var taskIndex = 0
                for (line in fileContent.lines()) {
                    if (line.trimStart().startsWith("- [ ]")) {
                        tasks += TaskWidgetItem(line.trim(), date, taskIndex)
                        taskIndex++
                    }
                }
            }
            if (tasks.isEmpty()) WidgetLoadResult.Empty("暂无待办事项", lastPath)
            else WidgetLoadResult.Success(tasks, lastPath ?: vaultPath)
        } catch (e: Exception) {
            WidgetLoadResult.Failure("读取失败", exception = e)
        }
    }

    private fun parseReadLine(line: String): ReadWidgetItem {
        val trimmed = line.trim()
        return when {
            trimmed.isEmpty() -> ReadWidgetItem("blank")
            trimmed.startsWith("#### ") -> ReadWidgetItem("heading", trimmed.drop(5).trimStart(), 4)
            trimmed.startsWith("### ") -> ReadWidgetItem("heading", trimmed.drop(4).trimStart(), 3)
            trimmed.startsWith("## ") -> ReadWidgetItem("heading", trimmed.drop(3).trimStart(), 2)
            trimmed.startsWith("# ") -> ReadWidgetItem("heading", trimmed.drop(2).trimStart(), 1)
            trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]") -> {
                ReadWidgetItem("task", trimmed.substringAfter(']').trimStart(), checked = true)
            }
            trimmed.startsWith("- [ ]") -> {
                ReadWidgetItem("task", trimmed.substringAfter(']').trimStart(), checked = false)
            }
            trimmed.startsWith("> ") -> ReadWidgetItem("quote", trimmed.drop(2).trimStart())
            Regex("^[-*_]{3,}$").matches(trimmed) -> ReadWidgetItem("hr")
            trimmed.startsWith("- ") -> ReadWidgetItem("bullet", trimmed.drop(2).trimStart())
            Regex("^\\d+\\.").containsMatchIn(trimmed) -> {
                ReadWidgetItem("bullet", trimmed.dropWhile { it != ' ' }.trimStart())
            }
            Regex("^!\\[.*\\]\\(.*\\)").containsMatchIn(trimmed) -> {
                val alt = trimmed.substringAfter("![").substringBefore("](")
                val path = trimmed.substringAfter("](").substringBefore(")")
                ReadWidgetItem("image", if (path.isNotEmpty()) path.substringAfterLast("/") else alt)
            }
            else -> ReadWidgetItem("plain", trimmed)
        }
    }
}

object ReadWidgetViews {
    fun create(context: Context, item: ReadWidgetItem, position: Int): RemoteViews {
        val colors = WidgetAppearance.colors(context)
        return when (item.type) {
            "task" -> RemoteViews(context.packageName, R.layout.widget_diary_read_task).apply {
                setTextViewText(R.id.task_text, item.text)
                setFloat(R.id.task_text, "setTextSize", 12f)
                setTextColor(R.id.task_text, if (item.checked) colors.muted else colors.foreground)
                setImageViewResource(
                    R.id.task_checkbox,
                    if (item.checked) android.R.drawable.checkbox_on_background
                    else android.R.drawable.checkbox_off_background
                )
                val fillIntent = Intent().apply { putExtra("task_index", position) }
                setOnClickFillInIntent(R.id.task_row, fillIntent)
                setOnClickFillInIntent(R.id.task_checkbox, fillIntent)
            }
            "heading" -> RemoteViews(context.packageName, R.layout.widget_diary_read_line).apply {
                setTextViewText(R.id.task_text, item.text)
                setTextColor(R.id.task_text, colors.foreground)
                setFloat(R.id.task_text, "setTextSize", when (item.level) {
                    1 -> 18f
                    2 -> 16f
                    3 -> 14f
                    else -> 13f
                })
            }
            "image" -> simpleLine(context, "[图片]", colors.muted)
            "quote" -> simpleLine(context, " ▍ " + renderMarkdown(item.text), colors.foreground)
            "hr" -> simpleLine(context, " ─────────────────", colors.muted)
            "bullet" -> simpleLine(context, TextUtils.concat("  •  ", renderMarkdown(item.text)), colors.foreground)
            "blank" -> simpleLine(context, " ", colors.foreground)
            else -> simpleLine(
                context,
                if (item.renderInlineMarkdown) renderMarkdown(item.text) else item.text,
                colors.foreground
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun collection(context: Context, items: List<ReadWidgetItem>): RemoteViews.RemoteCollectionItems {
        val builder = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(false)
            .setViewTypeCount(3)
        items.forEachIndexed { index, item ->
            builder.addItem(index.toLong(), create(context, item, index))
        }
        return builder.build()
    }

    private fun simpleLine(context: Context, text: CharSequence, color: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_diary_read_line).apply {
            setFloat(R.id.task_text, "setTextSize", 12f)
            setTextViewText(R.id.task_text, text)
            setTextColor(R.id.task_text, color)
        }

    private fun renderMarkdown(text: String): CharSequence {
        val ssb = SpannableStringBuilder(text)
        for (match in Regex("\\*\\*(.+?)\\*\\*").findAll(ssb.toString()).toList().reversed()) {
            val content = match.groupValues[1]
            ssb.replace(match.range.first, match.range.last + 1, content)
            ssb.setSpan(StyleSpan(Typeface.BOLD), match.range.first, match.range.first + content.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (match in Regex("__(.+?)__").findAll(ssb.toString()).toList().reversed()) {
            val content = match.groupValues[1]
            ssb.replace(match.range.first, match.range.last + 1, content)
            ssb.setSpan(StyleSpan(Typeface.BOLD), match.range.first, match.range.first + content.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (match in Regex("~~(.+?)~~").findAll(ssb.toString()).toList().reversed()) {
            val content = match.groupValues[1]
            ssb.replace(match.range.first, match.range.last + 1, content)
            ssb.setSpan(StrikethroughSpan(), match.range.first, match.range.first + content.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (match in Regex("`(.+?)`").findAll(ssb.toString()).toList().reversed()) {
            val content = match.groupValues[1]
            ssb.replace(match.range.first, match.range.last + 1, content)
            ssb.setSpan(TypefaceSpan("monospace"), match.range.first, match.range.first + content.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (match in Regex("\\*(.+?)\\*").findAll(ssb.toString()).toList().reversed()) {
            val content = match.groupValues[1]
            ssb.replace(match.range.first, match.range.last + 1, content)
            ssb.setSpan(StyleSpan(Typeface.ITALIC), match.range.first, match.range.first + content.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (match in Regex("_(.+?)_").findAll(ssb.toString()).toList().reversed()) {
            val content = match.groupValues[1]
            ssb.replace(match.range.first, match.range.last + 1, content)
            ssb.setSpan(StyleSpan(Typeface.ITALIC), match.range.first, match.range.first + content.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (match in Regex("(?<![\\p{L}\\p{N}_])#[\\p{L}\\p{N}_/-]+").findAll(ssb.toString())) {
            ssb.setSpan(ForegroundColorSpan(Color.rgb(30, 136, 229)), match.range.first, match.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return ssb
    }
}

object TaskWidgetViews {
    fun create(context: Context, item: TaskWidgetItem): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_task_item).apply {
            val taskText = item.text.replace("- [ ] ", "").replace("- [x] ", "").replace("- [X] ", "").trim()
            setTextViewText(R.id.task_text, taskText)
            setTextColor(R.id.task_text, WidgetAppearance.colors(context).foreground)
            val fillIntent = Intent().apply {
                putExtra("task_index", item.indexInDiary)
                putExtra("task_date", item.date)
            }
            setOnClickFillInIntent(R.id.task_checkbox, fillIntent)
        }

    @RequiresApi(Build.VERSION_CODES.S)
    fun collection(context: Context, items: List<TaskWidgetItem>): RemoteViews.RemoteCollectionItems {
        val builder = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(false)
            .setViewTypeCount(1)
        items.forEachIndexed { index, item ->
            builder.addItem(index.toLong(), create(context, item))
        }
        return builder.build()
    }
}

fun logWidgetResult(tag: String, result: WidgetLoadResult<*>) {
    when (result) {
        is WidgetLoadResult.Success<*> -> BetaLogger.log(tag, "load success count=${(result.value as? List<*>)?.size ?: -1} path=${result.sourcePath}")
        is WidgetLoadResult.Empty -> BetaLogger.log(tag, "load empty message=${result.message} path=${result.sourcePath ?: "none"}")
        is WidgetLoadResult.Failure -> BetaLogger.log(tag, "load failure message=${result.message} path=${result.sourcePath ?: "none"} exception=${result.exception?.javaClass?.simpleName} detail=${result.exception?.message}")
    }
}
