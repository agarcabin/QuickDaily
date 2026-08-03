package com.quickdaily

import com.quickdaily.util.DateUtil
import androidx.compose.ui.text.TextRange

object EditorStampPolicy {
    /** Toolbar timestamp: fixed 24-hour `HH:mm` format, followed by a space. */
    fun toolbarTimestampInsertion(): String = "${DateUtil.nowTimeStr()} "

    fun timestampInsertion(format: String): String? = when (format) {
        "none" -> null
        "time_only" -> "${DateUtil.nowTimeStr()} "
        "time_only_seconds" -> "${DateUtil.nowTimeSecondsStr()} "
        "list" -> "- "
        "ordered" -> "1. "
        "list_time" -> "- ${DateUtil.nowTimeStr()} "
        "list_time_seconds" -> "- ${DateUtil.nowTimeSecondsStr()} "
        "date_time" -> "${DateUtil.nowDateTimeChineseStr()} "
        "list_date_time" -> "- ${DateUtil.nowDateTimeChineseStr()} "
        else -> "${DateUtil.nowTimeStr()} "
    }

    fun dateInsertion(): String = "${DateUtil.todayStr("YYYY-MM-DD")} "
}

enum class EditorStampAction {
    TIMESTAMP,
    DATE_STAMP,
}

data class EditorStampToggleRecord(
    val action: EditorStampAction,
    val start: Int,
    val insertedText: String,
    val replacedText: String,
)

data class EditorStampToggleState(
    val record: EditorStampToggleRecord? = null,
) {
    fun clear(): EditorStampToggleState = EditorStampToggleState()

    fun toggle(
        text: String,
        selection: TextRange,
        action: EditorStampAction,
        insertion: String,
    ): Pair<EditorTextActionResult, EditorStampToggleState> {
        val existing = record
        if (existing != null && existing.action == action) {
            val end = existing.start + existing.insertedText.length
            if (existing.start >= 0 && end <= text.length &&
                text.substring(existing.start, end) == existing.insertedText
            ) {
                val restored = text.substring(0, existing.start) +
                    existing.replacedText +
                    text.substring(end)
                val restoredCursor = existing.start + existing.replacedText.length
                return EditorTextActionResult(
                    text = restored,
                    selection = TextRange(restoredCursor),
                ) to EditorStampToggleState()
            }
        }

        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        val result = EditorTextActionPolicy.insert(text, selection, insertion)
        return result to EditorStampToggleState(
            record = EditorStampToggleRecord(
                action = action,
                start = start,
                insertedText = insertion,
                replacedText = text.substring(start, end),
            )
        )
    }
}
