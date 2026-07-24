package com.quickdaily.util

/** Text-only insertion rules shared by quick-note and shared-image append paths. */
object DiaryAppendUtil {
    private val headingPattern = Regex("^#{1,6}\\s+.*$")

    /**
     * Appends a complete entry at the end of the anchor's Markdown section.
     * Existing trailing blank lines remain after the new entry, so the entry is
     * always below the previous entry's text and images.
     */
    fun appendAtAnchorSectionEnd(
        body: String,
        anchor: String,
        entryLines: List<String>
    ): String {
        if (entryLines.isEmpty()) return body

        val normalizedEntryLines = entryLines.flatMap { line ->
            line.replace("\r\n", "\n").split('\n')
        }
        if (body.isEmpty()) return normalizedEntryLines.joinToString("\n")

        val lines = body.replace("\r\n", "\n").split('\n').toMutableList()
        val normalizedAnchor = anchor.trim()
        val anchorIndex = if (normalizedAnchor.isBlank()) {
            -1
        } else {
            lines.indexOfFirst { it.trim().contains(normalizedAnchor) }
        }
        val sectionStart = if (anchorIndex >= 0) anchorIndex + 1 else 0
        val sectionEnd = if (anchorIndex >= 0) {
            (sectionStart until lines.size).firstOrNull { index ->
                headingPattern.matches(lines[index].trim())
            } ?: lines.size
        } else {
            lines.size
        }

        var insertAt = sectionEnd
        while (insertAt > sectionStart && lines[insertAt - 1].isBlank()) {
            insertAt--
        }

        lines.addAll(insertAt, normalizedEntryLines)
        return lines.joinToString("\n")
    }
}
