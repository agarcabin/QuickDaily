package com.quickdaily

/** Adds an idempotent completion-date suffix to tasks completed from the task widget. */
internal object TaskCompletionTimestampPolicy {
    const val PREF_KEY = "task_completion_timestamp"
    const val DEFAULT_ENABLED = false

    // Accept the previous beta format too, so updating the same task cannot duplicate its date.
    private val suffixRegex = Regex("✅️ ?\\d{4}-\\d{2}-\\d{2}$")

    fun appendIfEnabled(line: String, enabled: Boolean, date: String): String {
        if (!enabled) return line

        val trimmedLine = line.trimEnd()
        if (suffixRegex.containsMatchIn(trimmedLine)) return line

        return "$trimmedLine ✅️ $date"
    }
}
