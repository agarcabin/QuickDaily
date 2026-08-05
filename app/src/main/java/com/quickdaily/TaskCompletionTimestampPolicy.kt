package com.quickdaily

/** Adds and removes the completion-date suffix shared by both task widgets. */
internal object TaskCompletionTimestampPolicy {
    const val PREF_KEY = "task_completion_timestamp"
    const val DEFAULT_ENABLED = false
    const val DATE_FORMAT = "YYYY-MM-DD"
    const val SETTING_DESCRIPTION = "从任务小部件完成任务时，在任务末尾添加 ✅️ $DATE_FORMAT"

    // Accept the previous beta format too, so updating the same task cannot duplicate its date.
    private val suffixRegex = Regex("✅\\uFE0F? ?\\d{4}-\\d{2}-\\d{2}$")

    fun appendIfEnabled(line: String, enabled: Boolean, date: String): String {
        if (!enabled) return line

        val trimmedLine = line.trimEnd()
        if (suffixRegex.containsMatchIn(trimmedLine)) return line

        return "$trimmedLine ✅️ $date"
    }

    /** Removes only a trailing completion-date suffix, preserving the task text. */
    fun removeIfPresent(line: String): String {
        val trailingWhitespace = line.takeLastWhile(Char::isWhitespace)
        val content = line.dropLast(trailingWhitespace.length)
        val match = suffixRegex.find(content) ?: return line
        var prefixEnd = match.range.first
        if (prefixEnd > 0 && content[prefixEnd - 1].isWhitespace()) {
            prefixEnd -= 1
        }
        return content.substring(0, prefixEnd) + trailingWhitespace
    }
}
