package com.quickdaily

import androidx.compose.ui.text.TextRange

data class TextIndentResult(
    val text: String,
    val selection: TextRange,
)

object TextIndentPolicy {
    const val INDENT_WIDTH = 4
    const val INDENT_UNIT = "    "

    fun indent(text: String, selection: TextRange): TextIndentResult =
        transform(text, selection, outdent = false)

    fun outdent(text: String, selection: TextRange): TextIndentResult =
        transform(text, selection, outdent = true)

    private fun transform(text: String, selection: TextRange, outdent: Boolean): TextIndentResult {
        val start = selection.start.coerceIn(0, text.length)
        val end = selection.end.coerceIn(0, text.length)
        val firstLineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val endProbe = if (end > start) (end - 1).coerceAtLeast(0) else end
        val lastLineStart = text.lastIndexOf('\n', endProbe - 1).let { if (it < 0) 0 else it + 1 }
        val lineStarts = buildList {
            var cursor = firstLineStart
            while (cursor <= lastLineStart) {
                add(cursor)
                val nextBreak = text.indexOf('\n', cursor)
                if (nextBreak < 0) break
                cursor = nextBreak + 1
            }
        }

        if (lineStarts.isEmpty()) return TextIndentResult(text, selection)

        val removals = lineStarts.associateWith { lineStart ->
            if (!outdent) {
                0
            } else {
                when {
                    text.startsWith("\t", lineStart) -> 1
                    text.regionMatches(lineStart, "    ", 0, 4) -> 4
                    text.regionMatches(lineStart, "   ", 0, 3) -> 3
                    text.regionMatches(lineStart, "  ", 0, 2) -> 2
                    text.startsWith(" ", lineStart) -> 1
                    else -> 0
                }
            }
        }

        val output = buildString(text.length + lineStarts.size * INDENT_WIDTH) {
            var cursor = 0
            lineStarts.forEach { lineStart ->
                append(text, cursor, lineStart)
                val remove = removals.getValue(lineStart)
                if (!outdent) append(INDENT_UNIT)
                append(text, lineStart + remove, nextLineEnd(text, lineStart))
                cursor = nextLineEnd(text, lineStart)
            }
            append(text, cursor, text.length)
        }

        fun mapPosition(position: Int): Int {
            var mapped = position
            lineStarts.forEach { lineStart ->
                val delta = if (outdent) -removals.getValue(lineStart) else INDENT_WIDTH
                if (lineStart < position || (!outdent && lineStart == position)) mapped += delta
            }
            return mapped.coerceIn(0, output.length)
        }

        return TextIndentResult(output, TextRange(mapPosition(start), mapPosition(end)))
    }

    private fun nextLineEnd(text: String, lineStart: Int): Int =
        text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
}
