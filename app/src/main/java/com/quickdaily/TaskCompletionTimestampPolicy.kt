package com.quickdaily

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Adds and removes the completion-date suffix shared by both task widgets. */
internal object TaskCompletionTimestampPolicy {
    const val PREF_KEY = "task_completion_timestamp"
    const val PREF_FORMAT_KEY = "task_completion_timestamp_format"
    const val DEFAULT_ENABLED = false
    const val DEFAULT_FORMAT = "✅️ YYYY-MM-DD"

    private val supportedTokens = listOf(
        "YYYY",
        "YY",
        "MM",
        "M",
        "DD",
        "D",
        "HH",
        "H",
        "mm",
        "m",
        "ss",
        "s",
    )

    // Accept the previous beta format too, so updating the same task cannot duplicate its date.
    private val legacySuffixRegex = Regex("✅\\uFE0F? ?\\d{4}-\\d{2}-\\d{2}$")

    fun normalizeFormat(format: String?): String {
        val candidate = format?.trim().orEmpty()
        return if (isValidFormat(candidate)) candidate else DEFAULT_FORMAT
    }

    /** Returns false for an empty format or an unsupported ASCII placeholder. */
    fun isValidFormat(format: String): Boolean {
        if (format.isBlank()) return false
        var index = 0
        var hasToken = false
        while (index < format.length) {
            val token = supportedTokens.firstOrNull { format.startsWith(it, index) }
            if (token != null) {
                hasToken = true
                index += token.length
                continue
            }
            val character = format[index]
            if (character == '[') {
                val end = format.indexOf(']', startIndex = index + 1)
                if (end <= index + 1) return false
                index = end + 1
                continue
            }
            if (character in 'A'..'Z' || character in 'a'..'z') return false
            index += 1
        }
        return hasToken
    }

    fun formatTimestamp(format: String, timestamp: LocalDateTime): String {
        val normalized = normalizeFormat(format)
        return render(normalized, timestamp)
    }

    /** Compatibility overload for callers that only provide the historical ISO date. */
    fun appendIfEnabled(line: String, enabled: Boolean, date: String): String =
        appendIfEnabled(line, enabled, date, DEFAULT_FORMAT)

    /** Appends a configured timestamp while retaining the historical date-based API. */
    fun appendIfEnabled(
        line: String,
        enabled: Boolean,
        date: String,
        format: String,
    ): String {
        val timestamp = runCatching {
            LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
                .atTime(LocalDateTime.now().toLocalTime())
        }.getOrElse { LocalDateTime.now() }
        return appendIfEnabled(line, enabled, format, timestamp)
    }

    /** Testable and deterministic entry point for a full date/time value. */
    fun appendIfEnabled(
        line: String,
        enabled: Boolean,
        format: String,
        timestamp: LocalDateTime,
    ): String {
        if (!enabled) return line

        val normalized = normalizeFormat(format)
        val trimmedLine = line.trimEnd()
        if (hasTimestampSuffix(trimmedLine, normalized)) return line

        return "$trimmedLine ${render(normalized, timestamp)}"
    }

    /** Removes a trailing legacy or current-format completion timestamp. */
    fun removeIfPresent(line: String, format: String = DEFAULT_FORMAT): String {
        val trailingWhitespace = line.takeLastWhile(Char::isWhitespace)
        val content = line.dropLast(trailingWhitespace.length)
        val normalized = normalizeFormat(format)
        val matches = listOfNotNull(
            legacySuffixRegex.find(content),
            currentSuffixRegex(normalized).find(content),
        )
        val match = matches.maxByOrNull { it.range.first } ?: return line
        var prefixEnd = match.range.first
        if (prefixEnd > 0 && content[prefixEnd - 1].isWhitespace()) {
            prefixEnd -= 1
        }
        return content.substring(0, prefixEnd) + trailingWhitespace
    }

    private fun hasTimestampSuffix(line: String, normalizedFormat: String): Boolean =
        legacySuffixRegex.containsMatchIn(line) || currentSuffixRegex(normalizedFormat).containsMatchIn(line)

    private fun render(format: String, timestamp: LocalDateTime): String {
        val output = StringBuilder()
        var index = 0
        while (index < format.length) {
            val token = supportedTokens.firstOrNull { format.startsWith(it, index) }
            if (token != null) {
                output.append(tokenValue(token, timestamp))
                index += token.length
                continue
            }
            if (format[index] == '[') {
                val end = format.indexOf(']', startIndex = index + 1)
                if (end > index + 1) {
                    output.append(format, index + 1, end)
                    index = end + 1
                    continue
                }
            }
            output.append(format[index])
            index += 1
        }
        return output.toString()
    }

    private fun tokenValue(token: String, timestamp: LocalDateTime): String = when (token) {
        "YYYY" -> timestamp.year.toString().padStart(4, '0')
        "YY" -> (timestamp.year % 100).toString().padStart(2, '0')
        "MM" -> timestamp.monthValue.toString().padStart(2, '0')
        "M" -> timestamp.monthValue.toString()
        "DD" -> timestamp.dayOfMonth.toString().padStart(2, '0')
        "D" -> timestamp.dayOfMonth.toString()
        "HH" -> timestamp.hour.toString().padStart(2, '0')
        "H" -> timestamp.hour.toString()
        "mm" -> timestamp.minute.toString().padStart(2, '0')
        "m" -> timestamp.minute.toString()
        "ss" -> timestamp.second.toString().padStart(2, '0')
        "s" -> timestamp.second.toString()
        else -> token
    }

    private fun currentSuffixRegex(format: String): Regex {
        val pattern = StringBuilder()
        var index = 0
        while (index < format.length) {
            val token = supportedTokens.firstOrNull { format.startsWith(it, index) }
            if (token != null) {
                pattern.append(
                    when (token) {
                        "YYYY" -> "\\d{4}"
                        "YY", "MM", "DD", "HH", "mm", "ss" -> "\\d{2}"
                        else -> "\\d{1,2}"
                    },
                )
                index += token.length
                continue
            }
            if (format[index] == '[') {
                val end = format.indexOf(']', startIndex = index + 1)
                if (end > index + 1) {
                    pattern.append(Regex.escape(format.substring(index + 1, end)))
                    index = end + 1
                    continue
                }
            }
            pattern.append(Regex.escape(format[index].toString()))
            index += 1
        }
        return Regex("$pattern$")
    }
}
