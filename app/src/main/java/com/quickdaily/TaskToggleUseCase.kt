package com.quickdaily

import android.content.Context
import com.quickdaily.util.FileUtil
import com.quickdaily.util.ReadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

internal fun interface WidgetAsyncFinishable {
    fun finish()
}

/** Runs the complete widget mutation off the broadcast main thread. */
internal object WidgetAsyncWorkRunner {
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(
        finishable: WidgetAsyncFinishable,
        block: () -> Unit,
    ): Job = workerScope.launch {
        try {
            block()
        } catch (error: Exception) {
            BetaLogger.logException("WidgetAsync", "background task failed", error)
        } finally {
            runCatching { finishable.finish() }
        }
    }
}

/** Shared file mutation path for the task and diary-read widgets. */
internal object TaskToggleUseCase {
    fun toggle(
        context: Context,
        path: String,
        lineIndex: Int,
        expectedRaw: String,
        logTag: String,
        expectedSeparator: String? = null,
    ): TaskToggleResult = FileUtil.withPathMutation(path) {
        toggleLocked(context, path, lineIndex, expectedRaw, logTag, expectedSeparator)
    }

    private fun toggleLocked(
        context: Context,
        path: String,
        lineIndex: Int,
        expectedRaw: String,
        logTag: String,
        expectedSeparator: String?,
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

        val sourceDocument = SourceDocument.from(content)
        val sourceLine = sourceDocument.lines.getOrNull(lineIndex)
        if (sourceLine == null) {
            val reason = "line_out_of_range"
            BetaLogger.log(logTag, "toggle aborted reason=$reason path=$path line=$lineIndex lineCount=${sourceDocument.lines.size}")
            return TaskToggleResult(false, failureReason = reason)
        }
        if (expectedRaw.isNotBlank() && sourceLine.rawLine != expectedRaw) {
            val reason = "stale_line"
            BetaLogger.log(
                logTag,
                "toggle aborted reason=$reason path=$path line=$lineIndex expectedRaw=$expectedRaw actualRaw=${sourceLine.rawLine}",
            )
            return TaskToggleResult(false, failureReason = reason)
        }
        if (expectedSeparator != null && sourceLine.separator != expectedSeparator) {
            val reason = "stale_line"
            BetaLogger.log(
                logTag,
                "toggle aborted reason=$reason path=$path line=$lineIndex expectedSeparator=${expectedSeparator.length} actualSeparator=${sourceLine.separator.length}",
            )
            return TaskToggleResult(false, failureReason = reason)
        }

        val body = sourceDocument.bodyTextPreservingSeparators()
        val item = TaskWidgetTaskParser.parse(
            body = body,
            sourcePath = path,
            lineIndexOffset = sourceDocument.bodyStartLine,
        ).firstOrNull { it.lineIndex == lineIndex }
        val toggledLine = TaskWidgetTaskParser.toggleLine(sourceLine.rawLine)
        if (item == null || toggledLine == null) {
            val reason = "non_task_line"
            BetaLogger.log(logTag, "toggle aborted reason=$reason path=$path line=$lineIndex raw=${sourceLine.rawLine}")
            return TaskToggleResult(false, failureReason = reason)
        }

        val preferences = context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
        val timestampEnabled = preferences
            .getBoolean(
                TaskCompletionTimestampPolicy.PREF_KEY,
                TaskCompletionTimestampPolicy.DEFAULT_ENABLED,
            )
        val timestampFormat = TaskCompletionTimestampPolicy.normalizeFormat(
            preferences.getString(
                TaskCompletionTimestampPolicy.PREF_FORMAT_KEY,
                TaskCompletionTimestampPolicy.DEFAULT_FORMAT,
            ),
        )
        val savedLine: String
        val timestampAction: String
        if (item.checked) {
            savedLine = TaskCompletionTimestampPolicy.removeIfPresent(toggledLine, timestampFormat)
            timestampAction = if (savedLine != toggledLine) "removed" else "none"
        } else {
            savedLine = TaskCompletionTimestampPolicy.appendIfEnabled(
                line = toggledLine,
                enabled = timestampEnabled,
                date = com.quickdaily.util.DateUtil.todayStr("YYYY-MM-DD"),
                format = timestampFormat,
            )
            timestampAction = when {
                savedLine != toggledLine -> "appended"
                timestampEnabled -> "already_present"
                else -> "disabled"
            }
        }

        val saveContent = sourceDocument.replaceLine(lineIndex, savedLine)
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
