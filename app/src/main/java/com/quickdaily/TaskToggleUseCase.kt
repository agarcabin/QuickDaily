package com.quickdaily

import android.content.Context
import com.quickdaily.util.ContentUtil
import com.quickdaily.util.FileUtil
import com.quickdaily.util.ReadResult

/** Result of a widget-driven Markdown task mutation. */
internal data class TaskToggleResult(
    val succeeded: Boolean,
    val beforeChecked: Boolean? = null,
    val afterChecked: Boolean? = null,
    val beforeLine: String? = null,
    val afterLine: String? = null,
    val timestampAction: String = "none",
    val failureReason: String? = null,
)

/** Shared file mutation path for the task and diary-read widgets. */
internal object TaskToggleUseCase {
    fun toggle(
        context: Context,
        path: String,
        lineIndex: Int,
        expectedRaw: String,
        logTag: String,
    ): TaskToggleResult {
        if (path.isBlank() || lineIndex < 0) {
            val reason = "invalid_target"
            BetaLogger.log(logTag, "toggle aborted reason=$reason path=$path line=$lineIndex")
            return TaskToggleResult(false, failureReason = reason)
        }

        val content = when (val result = FileUtil.readResult(path)) {
            is ReadResult.Success -> result.content
            ReadResult.NotFound -> {
                val reason = "file_not_found"
                BetaLogger.log(logTag, "toggle aborted reason=$reason path=$path line=$lineIndex")
                return TaskToggleResult(false, failureReason = reason)
            }
            is ReadResult.Error -> {
                val reason = "read_failed:${result.exception.javaClass.simpleName}"
                BetaLogger.logException(logTag, "toggle aborted reason=read_failed path=$path line=$lineIndex", result.exception)
                return TaskToggleResult(false, failureReason = reason)
            }
        }

        val parsed = ContentUtil.parseFrontmatter(content)
        val body = if (parsed.hasFrontmatter) parsed.body else content
        val lines = body.lines().toMutableList()
        if (lineIndex !in lines.indices) {
            val reason = "line_out_of_range"
            BetaLogger.log(logTag, "toggle aborted reason=$reason path=$path line=$lineIndex lineCount=${lines.size}")
            return TaskToggleResult(false, failureReason = reason)
        }
        if (expectedRaw.isNotBlank() && lines[lineIndex] != expectedRaw) {
            val reason = "stale_line"
            BetaLogger.log(
                logTag,
                "toggle aborted reason=$reason path=$path line=$lineIndex expectedRaw=$expectedRaw actualRaw=${lines[lineIndex]}",
            )
            return TaskToggleResult(false, failureReason = reason)
        }

        val item = TaskWidgetTaskParser.parse(body, path).firstOrNull { it.lineIndex == lineIndex }
        val toggledLine = TaskWidgetTaskParser.toggleLine(lines[lineIndex])
        if (item == null || toggledLine == null) {
            val reason = "non_task_line"
            BetaLogger.log(logTag, "toggle aborted reason=$reason path=$path line=$lineIndex raw=${lines[lineIndex]}")
            return TaskToggleResult(false, failureReason = reason)
        }

        val timestampEnabled = context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
            .getBoolean(
                TaskCompletionTimestampPolicy.PREF_KEY,
                TaskCompletionTimestampPolicy.DEFAULT_ENABLED,
            )
        val savedLine: String
        val timestampAction: String
        if (item.checked) {
            savedLine = TaskCompletionTimestampPolicy.removeIfPresent(toggledLine)
            timestampAction = if (savedLine != toggledLine) "removed" else "none"
        } else {
            savedLine = TaskCompletionTimestampPolicy.appendIfEnabled(
                line = toggledLine,
                enabled = timestampEnabled,
                date = com.quickdaily.util.DateUtil.todayStr("yyyy-MM-dd"),
            )
            timestampAction = when {
                savedLine != toggledLine -> "appended"
                timestampEnabled -> "already_present"
                else -> "disabled"
            }
        }

        lines[lineIndex] = savedLine
        val newBody = lines.joinToString("\n")
        val saveContent = if (parsed.hasFrontmatter) {
            ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, newBody)
        } else {
            newBody
        }
        BetaLogger.log(
                logTag,
                "toggle prepared path=$path line=$lineIndex checkedBefore=${item.checked} checkedAfter=${!item.checked} " +
                "timestampAction=$timestampAction timestampEnabled=$timestampEnabled beforeRaw=${item.rawLine} afterRaw=$savedLine",
        )

        if (!FileUtil.write(path, saveContent)) {
            BetaLogger.log(logTag, "toggle failed to write path=$path line=$lineIndex afterRaw=$savedLine")
            return TaskToggleResult(
                succeeded = false,
                beforeChecked = item.checked,
                afterChecked = !item.checked,
                beforeLine = item.rawLine,
                afterLine = savedLine,
                timestampAction = timestampAction,
                failureReason = "write_failed",
            )
        }

        BetaLogger.log(
            logTag,
            "toggle saved path=$path line=$lineIndex checkedBefore=${item.checked} checkedAfter=${!item.checked} " +
                "timestampAction=$timestampAction beforeRaw=${item.rawLine} afterRaw=$savedLine",
        )
        return TaskToggleResult(
            succeeded = true,
            beforeChecked = item.checked,
            afterChecked = !item.checked,
            beforeLine = item.rawLine,
            afterLine = savedLine,
            timestampAction = timestampAction,
        )
    }

}
