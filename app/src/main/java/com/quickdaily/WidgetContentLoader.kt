package com.quickdaily

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.ScaleXSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.quickdaily.util.DateUtil
import com.quickdaily.util.FileUtil
import com.quickdaily.util.ReadResult
import com.quickdaily.util.VaultStoragePrefs

sealed interface WidgetLoadResult<out T> {
    data class Success<T>(val value: T, val sourcePath: String) : WidgetLoadResult<T>
    data class Empty(val message: UiText, val sourcePath: String? = null) : WidgetLoadResult<Nothing>
    data class Failure(
        val message: UiText,
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
    val lineSeparator: String = "",
)

object WidgetContentLoader {
    /** Extra left padding per nested task level in both widget renderers. */
    const val SUBTASK_INDENT_DP = 20

    fun loadRead(context: Context): WidgetLoadResult<List<ReadWidgetItem>> =
        loadRead(context, ReadWidgetConfig())

    fun loadRead(
        context: Context,
        widgetConfig: ReadWidgetConfig,
    ): WidgetLoadResult<List<ReadWidgetItem>> {
        val customTarget = widgetConfig.target == ReadWidgetTarget.CUSTOM
        return try {
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            val vaultPath = VaultStoragePrefs.current(context).rootPath
            if (vaultPath.isBlank()) {
                return if (customTarget) {
                    WidgetLoadResult.Failure(UiText.Resource(R.string.qd_widget_page_missing))
                } else {
                    WidgetLoadResult.Empty(UiText.Resource(R.string.qd_widget_vault_required))
                }
            }

            val path = ReadWidgetConfigStore.targetFilePath(context, widgetConfig)
                ?: return if (customTarget) {
                    WidgetLoadResult.Failure(UiText.Resource(R.string.qd_widget_page_missing))
                } else {
                    WidgetLoadResult.Empty(UiText.Resource(R.string.qd_widget_vault_required))
                }
            val emptyMessage = UiText.Resource(
                if (customTarget) R.string.qd_widget_no_content else R.string.qd_widget_no_diary,
            )
            val content = when (val result = FileUtil.readResult(path)) {
                is ReadResult.Success -> result.content
                is ReadResult.NotFound -> return if (customTarget) {
                    WidgetLoadResult.Failure(UiText.Resource(R.string.qd_widget_page_missing), path)
                } else {
                    WidgetLoadResult.Empty(emptyMessage, path)
                }
                is ReadResult.Error -> return WidgetLoadResult.Failure(UiText.Resource(R.string.qd_widget_read_failed), path, result.exception)
            }
            if (content.isBlank()) return WidgetLoadResult.Empty(emptyMessage, path)

            val sourceDocument = SourceDocument.from(content)
            val bodyContent = sourceDocument.bodyTextPreservingSeparators()
            val filterFrontmatter = prefs.getBoolean("filter_frontmatter", false)
            val displayContent = if (filterFrontmatter && sourceDocument.hasFrontmatter) {
                bodyContent
            } else {
                content
            }
            if (displayContent.isBlank()) return WidgetLoadResult.Empty(emptyMessage, path)

            val renderMarkdown = prefs.getBoolean("render_markdown", true)
            val items = if (!renderMarkdown) {
                parsePlainReadLines(displayContent)
            } else {
                parseReadLines(
                    displayContent = displayContent,
                    taskBody = bodyContent,
                    sourcePath = path,
                    bodyLineOffset = sourceDocument.bodyStartLine,
                    displayLineOffset = if (filterFrontmatter && sourceDocument.hasFrontmatter) {
                        sourceDocument.bodyStartLine
                    } else 0,
                )
            }
            val taskCount = items.count { it.type == "task" }
            BetaLogger.log(
                "ReadWidget/Parse",
                "path=$path target=${widgetConfig.target.key} renderMarkdown=$renderMarkdown " +
                    "filterFrontmatter=$filterFrontmatter displayLines=${SourceDocument.splitLines(displayContent).size} " +
                    "visibleTasks=$taskCount",
            )
            if (items.isEmpty()) WidgetLoadResult.Empty(emptyMessage, path)
            else WidgetLoadResult.Success(items, path)
        } catch (e: Exception) {
            WidgetLoadResult.Failure(UiText.Resource(R.string.qd_widget_read_failed), exception = e)
        }
    }
    internal fun parseReadLines(
        displayContent: String,
        taskBody: String,
        sourcePath: String,
        bodyLineOffset: Int,
        displayLineOffset: Int = 0,
    ): List<ReadWidgetItem> {
        val parsedTasks = TaskWidgetTaskParser.parse(
            body = taskBody,
            sourcePath = sourcePath,
            lineIndexOffset = bodyLineOffset,
        )
        val tasksBySourceLine = parsedTasks.associateBy { it.lineIndex }

        return buildList {
            SourceDocument.splitLines(displayContent).forEach { record ->
                val sourceLineIndex = record.index + displayLineOffset
                val task = tasksBySourceLine[sourceLineIndex]
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
                            lineSeparator = task.lineSeparator,
                        )
                    )
                } else {
                    add(
                        parseReadLine(
                            line = record.rawLine,
                            sourcePath = sourcePath,
                            sourceLineIndex = sourceLineIndex,
                            separator = record.separator,
                        )
                    )
                }
            }
        }
    }

    internal fun parsePlainReadLines(displayContent: String): List<ReadWidgetItem> =
        SourceDocument.splitLines(displayContent).map { record ->
            if (record.rawLine.trim().isEmpty()) {
                ReadWidgetItem(
                    type = "blank",
                    renderInlineMarkdown = false,
                    rawLine = record.rawLine,
                    lineSeparator = record.separator,
                )
            } else {
                ReadWidgetItem(
                    type = "plain",
                    text = record.rawLine.trim(),
                    renderInlineMarkdown = false,
                    rawLine = record.rawLine,
                    lineSeparator = record.separator,
                )
            }
        }

    fun loadTasks(
        context: Context,
        widgetConfig: TaskWidgetConfig,
    ): WidgetLoadResult<List<TaskWidgetItem>> {
        return try {
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            val vaultPath = VaultStoragePrefs.current(context).rootPath
            if (vaultPath.isBlank() && widgetConfig.scope != TaskWidgetScope.CUSTOM &&
                widgetConfig.scope != TaskWidgetScope.CUSTOM_FOLDER
            ) {
                return WidgetLoadResult.Empty(UiText.Resource(R.string.qd_widget_vault_required))
            }

            val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
            val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
            val showCompleted = prefs.getBoolean(
                TaskWidgetDisplayPolicy.SHOW_COMPLETED_PREF_KEY,
                TaskWidgetDisplayPolicy.DEFAULT_SHOW_COMPLETED,
            )
            val groupByDate = prefs.getBoolean(
                TaskWidgetDisplayPolicy.GROUP_BY_DATE_PREF_KEY,
                TaskWidgetDisplayPolicy.DEFAULT_GROUP_BY_DATE,
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
                        ?: return WidgetLoadResult.Failure(UiText.Resource(R.string.qd_widget_custom_page_unavailable))
                    listOf(customPath to null)
                }
                TaskWidgetScope.CUSTOM_FOLDER -> emptyList()
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
                            return WidgetLoadResult.Failure(UiText.Resource(R.string.qd_widget_custom_page_unavailable), path)
                        }
                        continue
                    }
                    is ReadResult.Error -> return WidgetLoadResult.Failure(UiText.Resource(R.string.qd_widget_read_failed), path, result.exception)
                }
                if (fileContent.isEmpty()) continue

                val sourceDocument = SourceDocument.from(fileContent)
                val body = sourceDocument.bodyTextPreservingSeparators()
                val visibleTasks = TaskWidgetTaskParser.parseVisible(
                    body = body,
                    sourcePath = path,
                    date = date,
                    showCompleted = showCompleted,
                    lineIndexOffset = sourceDocument.bodyStartLine,
                )
                tasks += visibleTasks
                BetaLogger.log(
                    "TaskWidget/Parse",
                    "path=$path date=${date.orEmpty()} hasFrontmatter=${sourceDocument.hasFrontmatter} " +
                        "showCompleted=$showCompleted bodyLength=${body.length} visibleTasks=${visibleTasks.size}",
                )
            }

            if (widgetConfig.scope == TaskWidgetScope.CUSTOM_FOLDER) {
                val folderUri = widgetConfig.customFolderUri.trim().takeIf { it.isNotBlank() }
                    ?: return WidgetLoadResult.Failure(UiText.Resource(R.string.qd_widget_custom_folder_unavailable))
                val snapshot = TaskWidgetFolderResolver.scan(context, widgetConfig)
                snapshot.scanError?.let { message ->
                    return WidgetLoadResult.Failure(message, folderUri, snapshot.scanException)
                }
                val selectedFiles = snapshot.candidates
                var readableFiles = 0
                var inaccessibleFiles = 0
                var firstFileHeader = true
                var firstReadablePath: String? = null
                selectedFiles.forEach { candidate ->
                    val file = candidate.file
                    if (!candidate.isReadable || candidate.content == null) {
                        inaccessibleFiles++
                        return@forEach
                    }
                    readableFiles++
                    if (firstReadablePath == null) firstReadablePath = file.sourcePath
                    val fileContent = candidate.content
                    if (fileContent.isEmpty()) return@forEach
                    val sourceDocument = SourceDocument.from(fileContent)
                    val body = sourceDocument.bodyTextPreservingSeparators()
                    val visibleTasks = TaskWidgetTaskParser.parseVisible(
                        body = body,
                        sourcePath = file.sourcePath,
                        date = null,
                        showCompleted = showCompleted,
                        lineIndexOffset = sourceDocument.bodyStartLine,
                    )
                    if (visibleTasks.isNotEmpty()) {
                        tasks += TaskWidgetTaskParser.fileHeader(
                            TaskWidgetFolderResolver.displayName(context, file.displayName),
                            firstFileHeader,
                        )
                        firstFileHeader = false
                        tasks += visibleTasks
                    }
                    BetaLogger.log(
                        "TaskWidget/FolderParse",
                        "path=${file.sourcePath} relative=${file.relativePath} effectiveTime=${file.effectiveTime} " +
                            "usedCreationTime=${file.usedCreationTime} showCompleted=$showCompleted tasks=${visibleTasks.size}",
                    )
                }
                if (selectedFiles.isEmpty()) {
                    return WidgetLoadResult.Empty(UiText.Resource(R.string.qd_widget_folder_no_files), folderUri)
                }
                if (readableFiles == 0 && inaccessibleFiles > 0) {
                    return WidgetLoadResult.Failure(UiText.Resource(R.string.qd_widget_folder_all_unavailable), folderUri)
                }
                lastPath = firstReadablePath ?: folderUri
                BetaLogger.log(
                    "TaskWidget/FolderGroup",
                    "uri=$folderUri window=${widgetConfig.folderTimeWindow.key} recursive=${widgetConfig.folderIncludeSubfolders} " +
                        "selected=${selectedFiles.size} readable=$readableFiles inaccessible=$inaccessibleFiles tasks=${tasks.size}",
                )
            }
            val displayTasks = if (
                groupByDate &&
                (widgetConfig.scope == TaskWidgetScope.WEEK || widgetConfig.scope == TaskWidgetScope.MONTH)
            ) {
                TaskWidgetDateGrouping.withHeaders(tasks, DateUtil.dateStr(dateFormat))
            } else {
                tasks
            }
            BetaLogger.log(
                "TaskWidget/Group",
                "scope=${widgetConfig.scope.key} enabled=$groupByDate raw=${tasks.size} display=${displayTasks.size}",
            )
            if (tasks.isEmpty()) WidgetLoadResult.Empty(UiText.Resource(R.string.qd_widget_tasks_empty), lastPath)
            else WidgetLoadResult.Success(displayTasks, lastPath ?: vaultPath)
        } catch (e: Exception) {
            WidgetLoadResult.Failure(UiText.Resource(R.string.qd_widget_read_failed), exception = e)
        }
    }

    private fun parseReadLine(
        line: String,
        sourcePath: String? = null,
        sourceLineIndex: Int = -1,
        separator: String = "",
    ): ReadWidgetItem {
        val trimmed = line.trim()
        fun item(type: String, text: String = "", level: Int = 1) = ReadWidgetItem(
            type = type,
            text = text,
            level = level,
            sourcePath = sourcePath,
            lineIndex = sourceLineIndex,
            rawLine = line,
            lineSeparator = separator,
        )
        return when {
            trimmed.isEmpty() -> item("blank")
            trimmed.startsWith("#### ") -> item("heading", trimmed.drop(5).trimStart(), 4)
            trimmed.startsWith("### ") -> item("heading", trimmed.drop(4).trimStart(), 3)
            trimmed.startsWith("## ") -> item("heading", trimmed.drop(3).trimStart(), 2)
            trimmed.startsWith("# ") -> item("heading", trimmed.drop(2).trimStart(), 1)
            trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]") -> {
                item("task", trimmed.substringAfter(']').trimStart()).copy(checked = true)
            }
            trimmed.startsWith("- [ ]") -> {
                item("task", trimmed.substringAfter(']').trimStart()).copy(checked = false)
            }
            trimmed.startsWith("> ") -> item("quote", trimmed.drop(2).trimStart())
            Regex("^[-*_]{3,}$").matches(trimmed) -> item("hr")
            trimmed.startsWith("- ") -> item("bullet", trimmed.drop(2).trimStart())
            Regex("^\\d+\\.").containsMatchIn(trimmed) -> {
                item("bullet", trimmed.dropWhile { it != ' ' }.trimStart())
            }
            trimmed.startsWith("![[") && trimmed.endsWith("]]") -> item("image")
            Regex("^!\\[.*\\]\\(.*\\)").containsMatchIn(trimmed) -> {
                val alt = trimmed.substringAfter("![").substringBefore("](")
                val path = trimmed.substringAfter("](").substringBefore(")")
                item("image", if (path.isNotEmpty()) path.substringAfterLast("/") else alt)
            }
            else -> item("plain", trimmed)
        }
    }
}

object ReadWidgetViews {
    fun create(
        context: Context,
        item: ReadWidgetItem,
        size: WidgetSize = WidgetSize.DEFAULT,
        widgetId: Int = -1,
        generation: Long = 0L,
    ): RemoteViews = create(
        context = context,
        item = WidgetDisplayPreparer.prepareRead(
            listOf(item),
            WidgetDisplayPreparer.rules(context),
            imagePlaceholder = LocaleController.localizedContext(context).getString(R.string.qd_widget_image_placeholder),
        ).single(),
        size = size,
        widgetId = widgetId,
        generation = generation,
    )

    internal fun create(
        context: Context,
        item: PreparedReadWidgetItem,
        size: WidgetSize = WidgetSize.DEFAULT,
        widgetId: Int = -1,
        generation: Long = 0L,
    ): RemoteViews {
        val source = item.source
        val colors = WidgetAppearance.colors(context)
        return when (source.type) {
            "task" -> RemoteViews(context.packageName, R.layout.widget_diary_read_task).apply {
                val uiContext = LocaleController.localizedContext(context)
                setContentDescription(R.id.task_checkbox, uiContext.getString(R.string.qd_widget_toggle_task))
                setTextViewText(
                    R.id.task_text,
                    renderDisplay(
                        item.display,
                        colors,
                        item.hiddenRanges,
                        highlightTags = source.renderInlineMarkdown,
                    ),
                )
                setFloat(R.id.task_text, "setTextSize", if (size.isTiny) 11f else 12f)
                setInt(R.id.task_text, "setMaxLines", size.readMaxLines)
                setTextColor(R.id.task_text, if (source.checked) colors.muted else colors.foreground)
                setImageViewBitmap(
                    R.id.task_checkbox,
                    WidgetTaskCheckboxRenderer.bitmap(context, source.checked, colors),
                )
                setViewPadding(
                    R.id.task_row,
                    (source.indentLevel * WidgetContentLoader.SUBTASK_INDENT_DP * context.resources.displayMetrics.density).toInt(),
                    2,
                    0,
                    2,
                )
                val fillIntent = Intent().apply {
                    putExtra(TaskWidget.EXTRA_TASK_PATH, source.sourcePath.orEmpty())
                    putExtra(TaskWidget.EXTRA_TASK_LINE, source.lineIndex)
                    putExtra(TaskWidget.EXTRA_TASK_RAW, source.rawLine.orEmpty())
                    putExtra(TaskWidget.EXTRA_TASK_SEPARATOR, source.lineSeparator)
                    if (widgetId >= 0) {
                        putExtra(WidgetFillInContract.EXTRA_KIND, WidgetFillInContract.KIND_READ)
                        putExtra(WidgetFillInContract.EXTRA_WIDGET_ID, widgetId)
                        putExtra(WidgetFillInContract.EXTRA_GENERATION, generation)
                    }
                }
                setOnClickFillInIntent(R.id.task_row, fillIntent)
                setOnClickFillInIntent(R.id.task_checkbox, fillIntent)
            }
            "heading" -> RemoteViews(context.packageName, R.layout.widget_diary_read_line).apply {
                setTextViewText(
                    R.id.task_text,
                    renderDisplay(
                        item.display,
                        colors,
                        item.hiddenRanges,
                        highlightTags = source.renderInlineMarkdown,
                    ),
                )
                setTextColor(R.id.task_text, colors.foreground)
                setFloat(R.id.task_text, "setTextSize", when (source.level) {
                    1 -> 18f
                    2 -> 16f
                    3 -> 14f
                    else -> 13f
                })
                setInt(R.id.task_text, "setMaxLines", size.readMaxLines)
            }
            "image" -> simpleLine(context, item.display, colors.muted, item.hiddenRanges)
            "quote" -> simpleLine(
                context,
                item.display,
                colors.foreground,
                item.hiddenRanges,
            )
            "hr" -> simpleLine(context, item.display, colors.muted, item.hiddenRanges)
            "bullet" -> simpleLine(
                context,
                item.display,
                colors.foreground,
                item.hiddenRanges,
            )
            "blank" -> simpleLine(context, item.display, colors.foreground, item.hiddenRanges, blank = true)
            else -> simpleLine(
                context,
                item.display,
                colors.foreground,
                item.hiddenRanges,
                highlightTags = source.renderInlineMarkdown,
            )
        }.apply {
            setInt(R.id.task_text, "setMaxLines", size.readMaxLines)
            if (size.isTiny) setFloat(R.id.task_text, "setTextSize", 11f)
            if (item.collapseLine) {
                setViewVisibility(
                    if (source.type == "task") R.id.task_row else R.id.task_text,
                    View.GONE,
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun collection(
        context: Context,
        items: List<ReadWidgetItem>,
        size: WidgetSize = WidgetSize.DEFAULT,
        widgetId: Int = -1,
        generation: Long = 0L,
    ): RemoteViews.RemoteCollectionItems {
        val builder = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(false)
            .setViewTypeCount(3)
        val prepared = WidgetDisplayPreparer.prepareRead(
            items,
            WidgetDisplayPreparer.rules(context),
            imagePlaceholder = LocaleController.localizedContext(context).getString(R.string.qd_widget_image_placeholder),
        )
        prepared.forEachIndexed { index, item ->
            builder.addItem(index.toLong(), create(context, item, size, widgetId, generation))
        }
        return builder.build()
    }

    private fun simpleLine(
        context: Context,
        display: DisplayText,
        color: Int,
        hiddenRanges: List<DisplayRange> = emptyList(),
        size: WidgetSize = WidgetSize.DEFAULT,
        blank: Boolean = false,
        highlightTags: Boolean = true,
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_diary_read_line).apply {
            setFloat(R.id.task_text, "setTextSize", if (size.isTiny) 11f else 12f)
            setInt(R.id.task_text, "setMaxLines", size.readMaxLines)
            setTextViewText(
                R.id.task_text,
                renderDisplay(
                    display,
                    WidgetAppearance.colors(context),
                    hiddenRanges,
                    highlightTags = highlightTags,
                ),
            )
            setTextColor(R.id.task_text, color)
            if (blank) {
                setInt(
                    R.id.task_text,
                    "setMinHeight",
                    (16 * context.resources.displayMetrics.density).toInt(),
                )
            }
        }

    internal fun renderDisplay(
        display: DisplayText,
        colors: WidgetAppearance.Colors,
        hiddenRanges: List<DisplayRange> = emptyList(),
        highlightTags: Boolean = true,
    ): CharSequence {
        val ssb = SpannableStringBuilder(display.text)
        display.styles.forEach { range ->
            if (range.start !in 0..ssb.length || range.end !in 0..ssb.length || range.start >= range.end) {
                return@forEach
            }
            when (range.kind) {
                DisplayStyleKind.BOLD -> ssb.setSpan(
                    StyleSpan(Typeface.BOLD),
                    range.start,
                    range.end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                DisplayStyleKind.ITALIC -> ssb.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    range.start,
                    range.end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                DisplayStyleKind.STRIKETHROUGH -> ssb.setSpan(
                    StrikethroughSpan(),
                    range.start,
                    range.end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                DisplayStyleKind.CODE -> ssb.setSpan(
                    TypefaceSpan("monospace"),
                    range.start,
                    range.end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                DisplayStyleKind.LINK -> {
                    ssb.setSpan(
                        ForegroundColorSpan(colors.tagForeground),
                        range.start,
                        range.end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    ssb.setSpan(
                        UnderlineSpan(),
                        range.start,
                        range.end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                DisplayStyleKind.WIKILINK -> ssb.setSpan(
                    ForegroundColorSpan(colors.tagForeground),
                    range.start,
                    range.end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        if (highlightTags) renderTags(ssb, colors)
        hiddenRanges.forEach { range ->
            val start = range.start.coerceIn(0, ssb.length)
            val end = range.end.coerceIn(start, ssb.length)
            if (start < end) {
                ssb.setSpan(
                    ForegroundColorSpan(Color.TRANSPARENT),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                ssb.setSpan(
                    ScaleXSpan(0f),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        return ssb
    }

    internal fun renderTags(
        text: CharSequence,
        colors: WidgetAppearance.Colors,
    ): CharSequence {
        val ssb = if (text is SpannableStringBuilder) text else SpannableStringBuilder(text)
        TagHighlightPolicy.ranges(ssb.toString()).forEach { range ->
            ssb.setSpan(
                ForegroundColorSpan(colors.tagForeground),
                range.start,
                range.end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return ssb
    }
}

object TaskWidgetViews {
    fun create(
        context: Context,
        item: TaskWidgetItem,
        size: WidgetSize = WidgetSize.DEFAULT,
        widgetId: Int = -1,
        generation: Long = 0L,
    ): RemoteViews = create(
        context = context,
        item = WidgetDisplayPreparer.prepareTasks(
            listOf(item),
            WidgetDisplayPreparer.rules(context),
        ).single(),
        size = size,
        widgetId = widgetId,
        generation = generation,
    )

    internal fun create(
        context: Context,
        item: PreparedTaskWidgetItem,
        size: WidgetSize = WidgetSize.DEFAULT,
        widgetId: Int = -1,
        generation: Long = 0L,
    ): RemoteViews {
        val source = item.source
        if (source.isDateHeader || source.isFileHeader) {
            val colors = WidgetAppearance.colors(context)
            val layout = if (source.isFirstDateHeader || source.isFirstFileHeader) {
                R.layout.widget_task_date_header
            } else {
                R.layout.widget_task_date_header_spaced
            }
            return RemoteViews(context.packageName, layout).apply {
                setTextViewText(R.id.date_header, source.text)
                setTextColor(R.id.date_header, colors.iconMuted)
                setFloat(R.id.date_header, "setTextSize", if (size.isTiny) 12f else 13f)
            }
        }
        return RemoteViews(context.packageName, R.layout.widget_task_item).apply {
            val colors = WidgetAppearance.colors(context)
            val uiContext = LocaleController.localizedContext(context)
            setContentDescription(R.id.task_checkbox, uiContext.getString(R.string.qd_widget_toggle_task))
            setTextViewText(R.id.task_text, ReadWidgetViews.renderDisplay(item.display, colors, item.hiddenRanges))
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
            setTextColor(R.id.task_text, if (source.checked) colors.muted else colors.foreground)
            setImageViewBitmap(
                R.id.task_checkbox,
                WidgetTaskCheckboxRenderer.bitmap(context, source.checked, colors),
            )
            setViewPadding(
                R.id.task_row,
                (source.indentLevel * WidgetContentLoader.SUBTASK_INDENT_DP * context.resources.displayMetrics.density).toInt(),
                3,
                0,
                3
            )
            val fillIntent = Intent().apply {
                putExtra(TaskWidget.EXTRA_TASK_PATH, source.sourcePath)
                putExtra(TaskWidget.EXTRA_TASK_LINE, source.lineIndex)
                putExtra(TaskWidget.EXTRA_TASK_RAW, source.rawLine)
                putExtra(TaskWidget.EXTRA_TASK_SEPARATOR, source.lineSeparator)
                if (widgetId >= 0) {
                    putExtra(WidgetFillInContract.EXTRA_KIND, WidgetFillInContract.KIND_TASK)
                    putExtra(WidgetFillInContract.EXTRA_WIDGET_ID, widgetId)
                    putExtra(WidgetFillInContract.EXTRA_GENERATION, generation)
                }
            }
            setOnClickFillInIntent(R.id.task_checkbox, fillIntent)
            if (item.collapseLine) {
                setViewVisibility(R.id.task_row, View.GONE)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun collection(
        context: Context,
        items: List<TaskWidgetItem>,
        size: WidgetSize = WidgetSize.DEFAULT,
        widgetId: Int = -1,
        generation: Long = 0L,
    ): RemoteViews.RemoteCollectionItems {
        val builder = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(false)
            .setViewTypeCount(3)
        val prepared = WidgetDisplayPreparer.prepareTasks(items, WidgetDisplayPreparer.rules(context))
        prepared.forEachIndexed { index, item ->
            builder.addItem(index.toLong(), create(context, item, size, widgetId, generation))
        }
        return builder.build()
    }
}

fun logWidgetResult(tag: String, result: WidgetLoadResult<*>) {
    when (result) {
        is WidgetLoadResult.Success<*> -> BetaLogger.log(tag, "load success count=${(result.value as? List<*>)?.size ?: -1} path=${result.sourcePath}")
        is WidgetLoadResult.Empty -> BetaLogger.log(tag, "load empty messageType=${result.message::class.simpleName} path=${result.sourcePath ?: "none"}")
        is WidgetLoadResult.Failure -> {
            BetaLogger.log(tag, "load failure messageType=${result.message::class.simpleName} path=${result.sourcePath ?: "none"} exception=${result.exception?.javaClass?.simpleName} detail=${result.exception?.message}")
            result.exception?.let { error ->
                BetaLogger.logException(tag, "load failure stack path=${result.sourcePath ?: "none"}", error)
            }
        }
    }
}
