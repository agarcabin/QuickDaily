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
    val renderInlineMarkdown: Boolean = true,
    val sourcePath: String? = null,
    val lineIndex: Int = -1,
    val rawLine: String? = null,
    val indentLevel: Int = 0,
)

object WidgetContentLoader {
    /** Extra left padding per nested task level in both widget renderers. */
    const val SUBTASK_INDENT_DP = 20

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

            val parsedContent = ContentUtil.parseFrontmatter(content)
            val normalizedContent = content.replace("\r\n", "\n")
            val filterFrontmatter = prefs.getBoolean("filter_frontmatter", false)
            val displayContent = if (filterFrontmatter && parsedContent.hasFrontmatter) {
                parsedContent.body
            } else {
                normalizedContent
            }
            if (displayContent.isBlank()) return WidgetLoadResult.Empty("暂无日记", path)

            val renderMarkdown = prefs.getBoolean("render_markdown", true)
            val items = if (!renderMarkdown) {
                parsePlainReadLines(displayContent)
            } else {
                parseReadLines(
                    displayContent = displayContent,
                    taskBody = if (parsedContent.hasFrontmatter) parsedContent.body else normalizedContent,
                    sourcePath = path,
                    bodyLineOffset = if (!filterFrontmatter && parsedContent.hasFrontmatter) {
                        normalizedContent.substring(0, normalizedContent.length - parsedContent.body.length)
                            .count { it == '\n' }
                    } else {
                        0
                    },
                )
            }
            val taskCount = items.count { it.type == "task" }
            BetaLogger.log(
                "ReadWidget/Parse",
                "path=$path renderMarkdown=$renderMarkdown filterFrontmatter=$filterFrontmatter " +
                    "displayLines=${displayContent.lines().size} visibleTasks=$taskCount",
            )
            if (items.isEmpty()) WidgetLoadResult.Empty("暂无日记", path)
            else WidgetLoadResult.Success(items, path)
        } catch (e: Exception) {
            WidgetLoadResult.Failure("读取失败", exception = e)
        }
    }

    internal fun parseReadLines(
        displayContent: String,
        taskBody: String,
        sourcePath: String,
        bodyLineOffset: Int,
    ): List<ReadWidgetItem> {
        val parsedTasks = TaskWidgetTaskParser.parse(taskBody, sourcePath)
        val tasksByDisplayLine = parsedTasks.associateBy { it.lineIndex + bodyLineOffset }

        return buildList {
            displayContent.lines().forEachIndexed { displayLineIndex, line ->
                val task = tasksByDisplayLine[displayLineIndex]
                if (task != null) {
                    add(
                        ReadWidgetItem(
                            type = "task",
                            text = task.text,
                            checked = task.checked,
                            sourcePath = task.sourcePath,
                            lineIndex = task.lineIndex,
                            rawLine = task.rawLine,
                            indentLevel = task.indentLevel,
                        )
                    )
                } else {
                    add(parseReadLine(line))
                }
            }
        }
    }

    internal fun parsePlainReadLines(displayContent: String): List<ReadWidgetItem> =
        displayContent.lines().map { line ->
            ReadWidgetItem("plain", if (line.trim().isEmpty()) " " else line.trim(), renderInlineMarkdown = false)
        }

    fun loadTasks(
        context: Context,
        widgetConfig: TaskWidgetConfig,
    ): WidgetLoadResult<List<TaskWidgetItem>> {
        return try {
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            val vaultPath = prefs.getString("vault_path", "") ?: ""
            if (vaultPath.isBlank() && widgetConfig.scope != TaskWidgetScope.CUSTOM) {
                return WidgetLoadResult.Empty("请先配置仓库")
            }

            val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
            val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
            val showCompleted = prefs.getBoolean(
                TaskWidgetDisplayPolicy.SHOW_COMPLETED_PREF_KEY,
                TaskWidgetDisplayPolicy.DEFAULT_SHOW_COMPLETED,
            )
            val tasks = mutableListOf<TaskWidgetItem>()
            var lastPath: String? = null
            val paths: List<Pair<String, String?>> = when (widgetConfig.scope) {
                TaskWidgetScope.TODAY,
                TaskWidgetScope.WEEK,
                TaskWidgetScope.MONTH -> {
                    val daysToLoad = when (widgetConfig.scope) {
                        TaskWidgetScope.WEEK -> 7
                        TaskWidgetScope.MONTH -> 30
                        else -> 1
                    }
                    (0 until daysToLoad).map { offset ->
                        val date = DateUtil.dateStr(dateFormat, -offset.toLong())
                        "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/$date.md" to date
                    }
                }
                TaskWidgetScope.CUSTOM -> {
                    val customPath = TaskWidgetConfigStore.customFilePath(context, widgetConfig)
                        ?: return WidgetLoadResult.Failure("自定义页面不可用")
                    listOf(customPath to null)
                }
            }
            BetaLogger.log(
                "TaskWidget/Load",
                "scope=${widgetConfig.scope.key} customPath=${widgetConfig.customRelativePath} paths=${paths.joinToString("|") { it.first }}",
            )

            for ((path, date) in paths) {
                lastPath = path
                val fileContent = when (val result = FileUtil.readResult(path)) {
                    is ReadResult.Success -> result.content
                    is ReadResult.NotFound -> {
                        if (widgetConfig.scope == TaskWidgetScope.CUSTOM) {
                            return WidgetLoadResult.Failure("自定义页面不可用", path)
                        }
                        continue
                    }
                    is ReadResult.Error -> return WidgetLoadResult.Failure("读取失败", path, result.exception)
                }
                if (fileContent.isEmpty()) continue

                val parsed = ContentUtil.parseFrontmatter(fileContent)
                val body = if (parsed.hasFrontmatter) parsed.body else fileContent
                val visibleTasks = TaskWidgetTaskParser.parseVisible(body, path, date, showCompleted)
                tasks += visibleTasks
                BetaLogger.log(
                    "TaskWidget/Parse",
                    "path=$path date=${date.orEmpty()} hasFrontmatter=${parsed.hasFrontmatter} " +
                        "showCompleted=$showCompleted bodyLength=${body.length} visibleTasks=${visibleTasks.size}",
                )
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
    fun create(
        context: Context,
        item: ReadWidgetItem,
        size: WidgetSize = WidgetSize.DEFAULT
    ): RemoteViews {
        val colors = WidgetAppearance.colors(context)
        return when (item.type) {
            "task" -> RemoteViews(context.packageName, R.layout.widget_diary_read_task).apply {
                setTextViewText(R.id.task_text, item.text)
                setFloat(R.id.task_text, "setTextSize", if (size.isTiny) 11f else 12f)
                setInt(R.id.task_text, "setMaxLines", size.readMaxLines)
                setTextColor(R.id.task_text, if (item.checked) colors.muted else colors.foreground)
                setImageViewResource(
                    R.id.task_checkbox,
                    if (item.checked) android.R.drawable.checkbox_on_background
                    else android.R.drawable.checkbox_off_background
                )
                setViewPadding(
                    R.id.task_row,
                    (item.indentLevel * WidgetContentLoader.SUBTASK_INDENT_DP * context.resources.displayMetrics.density).toInt(),
                    2,
                    0,
                    2,
                )
                val fillIntent = Intent().apply {
                    putExtra(TaskWidget.EXTRA_TASK_PATH, item.sourcePath.orEmpty())
                    putExtra(TaskWidget.EXTRA_TASK_LINE, item.lineIndex)
                    putExtra(TaskWidget.EXTRA_TASK_RAW, item.rawLine.orEmpty())
                }
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
                setInt(R.id.task_text, "setMaxLines", size.readMaxLines)
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
        }.apply {
            setInt(R.id.task_text, "setMaxLines", size.readMaxLines)
            if (size.isTiny) setFloat(R.id.task_text, "setTextSize", 11f)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun collection(
        context: Context,
        items: List<ReadWidgetItem>,
        size: WidgetSize = WidgetSize.DEFAULT
    ): RemoteViews.RemoteCollectionItems {
        val builder = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(false)
            .setViewTypeCount(3)
        items.forEachIndexed { index, item ->
            builder.addItem(index.toLong(), create(context, item, size))
        }
        return builder.build()
    }

    private fun simpleLine(
        context: Context,
        text: CharSequence,
        color: Int,
        size: WidgetSize = WidgetSize.DEFAULT
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_diary_read_line).apply {
            setFloat(R.id.task_text, "setTextSize", if (size.isTiny) 11f else 12f)
            setInt(R.id.task_text, "setMaxLines", size.readMaxLines)
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
    fun create(
        context: Context,
        item: TaskWidgetItem,
        size: WidgetSize = WidgetSize.DEFAULT
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_task_item).apply {
            setTextViewText(R.id.task_text, item.text.trim())
            setFloat(R.id.task_text, "setTextSize", if (size.isTiny) 11f else 12f)
            val showFullContent = context
                .getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
                .getBoolean(
                    TaskWidgetDisplayPolicy.SHOW_FULL_CONTENT_PREF_KEY,
                    TaskWidgetDisplayPolicy.DEFAULT_SHOW_FULL_CONTENT,
                )
            setInt(
                R.id.task_text,
                "setMaxLines",
                if (showFullContent) Int.MAX_VALUE else size.taskMaxLines,
            )
            val colors = WidgetAppearance.colors(context)
            setTextColor(R.id.task_text, if (item.checked) colors.muted else colors.foreground)
            setImageViewResource(
                R.id.task_checkbox,
                if (item.checked) android.R.drawable.checkbox_on_background
                else android.R.drawable.checkbox_off_background
            )
            setViewPadding(
                R.id.task_row,
                (item.indentLevel * WidgetContentLoader.SUBTASK_INDENT_DP * context.resources.displayMetrics.density).toInt(),
                3,
                0,
                3
            )
            val fillIntent = Intent().apply {
                putExtra(TaskWidget.EXTRA_TASK_PATH, item.sourcePath)
                putExtra(TaskWidget.EXTRA_TASK_LINE, item.lineIndex)
                putExtra(TaskWidget.EXTRA_TASK_RAW, item.rawLine)
            }
            setOnClickFillInIntent(R.id.task_checkbox, fillIntent)
        }

    @RequiresApi(Build.VERSION_CODES.S)
    fun collection(
        context: Context,
        items: List<TaskWidgetItem>,
        size: WidgetSize = WidgetSize.DEFAULT
    ): RemoteViews.RemoteCollectionItems {
        val builder = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(false)
            .setViewTypeCount(1)
        items.forEachIndexed { index, item ->
            builder.addItem(index.toLong(), create(context, item, size))
        }
        return builder.build()
    }
}

fun logWidgetResult(tag: String, result: WidgetLoadResult<*>) {
    when (result) {
        is WidgetLoadResult.Success<*> -> BetaLogger.log(tag, "load success count=${(result.value as? List<*>)?.size ?: -1} path=${result.sourcePath}")
        is WidgetLoadResult.Empty -> BetaLogger.log(tag, "load empty message=${result.message} path=${result.sourcePath ?: "none"}")
        is WidgetLoadResult.Failure -> {
            BetaLogger.log(tag, "load failure message=${result.message} path=${result.sourcePath ?: "none"} exception=${result.exception?.javaClass?.simpleName} detail=${result.exception?.message}")
            result.exception?.let { error ->
                BetaLogger.logException(tag, "load failure stack path=${result.sourcePath ?: "none"}", error)
            }
        }
    }
}
