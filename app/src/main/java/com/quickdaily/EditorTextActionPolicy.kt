package com.quickdaily

import androidx.compose.ui.text.TextRange

data class EditorTextActionResult(
    val text: String,
    val selection: TextRange,
    val clipboardText: String? = null,
)

/** Keeps multi-selection transforms and their UI commits in the selector's order. */
object EditorImageInsertPolicy {
    suspend fun <T, R> processInSelectionOrder(
        items: List<T>,
        transform: suspend (T) -> R,
    ): List<R> = items.map { transform(it) }
}

/** Pure text transformations shared by the full editor and the floating editor. */
object EditorTextActionPolicy {
    fun insert(text: String, selection: TextRange, insertion: String): EditorTextActionResult {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        val output = text.substring(0, start) + insertion + text.substring(end)
        val cursor = start + insertion.length
        return EditorTextActionResult(output, TextRange(cursor))
    }

    fun cutLine(text: String, selection: TextRange): EditorTextActionResult {
        val block = selectedBlock(text, selection)
        if (block == null) return EditorTextActionResult(text, selection)

        val removeStart: Int
        val removeEnd: Int
        if (block.after < text.length) {
            removeStart = block.start
            removeEnd = block.after
        } else if (block.start > 0) {
            removeStart = block.start - 1
            removeEnd = block.end
        } else {
            removeStart = block.start
            removeEnd = block.end
        }

        val output = text.removeRange(removeStart, removeEnd)
        return EditorTextActionResult(
            text = output,
            selection = TextRange(removeStart.coerceAtMost(output.length)),
            clipboardText = text.substring(block.start, block.end),
        )
    }

    fun moveLineUp(text: String, selection: TextRange): EditorTextActionResult =
        move(text, selection, direction = -1)

    fun moveLineDown(text: String, selection: TextRange): EditorTextActionResult =
        move(text, selection, direction = 1)

    fun toggleDelimiter(
        text: String,
        selection: TextRange,
        delimiter: String,
    ): EditorTextActionResult {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        if (start == end && start >= delimiter.length && start + delimiter.length <= text.length) {
            val before = text.substring(start - delimiter.length, start)
            val after = text.substring(start, start + delimiter.length)
            if (before == delimiter && after == delimiter) {
                val output = text.removeRange(start, start + delimiter.length)
                    .removeRange(start - delimiter.length, start)
                return EditorTextActionResult(output, TextRange(start - delimiter.length))
            }
        }
        if (start == end) {
            val openingStart = start - delimiter.length
            if (openingStart >= 0 && text.substring(openingStart, start) == delimiter) {
                val closingStart = text.indexOf(delimiter, start)
                if (closingStart >= start) {
                    val output = text.removeRange(closingStart, closingStart + delimiter.length)
                        .removeRange(openingStart, start)
                    return EditorTextActionResult(output, TextRange(openingStart))
                }
            }
            if (start + delimiter.length <= text.length && text.substring(start, start + delimiter.length) == delimiter) {
                val openingStartBefore = text.lastIndexOf(delimiter, start - delimiter.length)
                if (openingStartBefore >= 0) {
                    val output = text.removeRange(start, start + delimiter.length)
                        .removeRange(openingStartBefore, openingStartBefore + delimiter.length)
                    return EditorTextActionResult(output, TextRange(openingStartBefore))
                }
            }
        }
        val selected = text.substring(start, end)
        val output = text.substring(0, start) + delimiter + selected + delimiter + text.substring(end)
        val cursor = if (selected.isEmpty()) start + delimiter.length else start + delimiter.length + selected.length
        return EditorTextActionResult(output, TextRange(cursor))
    }

    fun prefixLines(
        text: String,
        selection: TextRange,
        prefix: String,
    ): EditorTextActionResult {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        val firstLineStart = lineStart(text, start)
        val lastLineStart = lineStart(text, if (end > start) end - 1 else end)
        val blockEnd = text.indexOf('\n', lastLineStart).let { if (it < 0) text.length else it }
        val block = text.substring(firstLineStart, blockEnd)
        val outputBlock = block.lines().joinToString("\n") { line ->
            if (line.startsWith(prefix)) line.removePrefix(prefix) else prefix + line
        }
        val output = text.substring(0, firstLineStart) + outputBlock + text.substring(blockEnd)
        val delta = outputBlock.length - block.length
        return EditorTextActionResult(
            text = output,
            selection = TextRange(
                (selection.start + delta).coerceIn(0, output.length),
                (selection.end + delta).coerceIn(0, output.length),
            ),
        )
    }

    fun codeBlock(text: String, selection: TextRange): EditorTextActionResult {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        val selected = text.substring(start, end)
        val insertion = if (selected.isEmpty()) "```\n\n```" else "```\n$selected\n```"
        val output = text.substring(0, start) + insertion + text.substring(end)
        val cursor = if (selected.isEmpty()) start + 4 else start + insertion.length
        return EditorTextActionResult(output, TextRange(cursor))
    }

    fun horizontalRule(text: String, selection: TextRange): EditorTextActionResult =
        insert(text, selection, "\n---\n")

    fun markdownLink(text: String, selection: TextRange): EditorTextActionResult {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        val label = text.substring(start, end).ifBlank { "链接文本" }
        val insertion = "[$label](https://)"
        val output = text.substring(0, start) + insertion + text.substring(end)
        return EditorTextActionResult(output, TextRange(start + label.length + 3))
    }

    private fun move(text: String, selection: TextRange, direction: Int): EditorTextActionResult {
        val block = selectedBlock(text, selection) ?: return EditorTextActionResult(text, selection)
        if (direction < 0) {
            if (block.start == 0) return EditorTextActionResult(text, selection)
            val previousStart = text.lastIndexOf('\n', block.start - 2).let { if (it < 0) 0 else it + 1 }
            val previousSegment = text.substring(previousStart, block.start - 1)
            val blockSegment = text.substring(block.start, block.end)
            val output = buildString(text.length) {
                append(text, 0, previousStart)
                append(blockSegment)
                append('\n')
                append(previousSegment)
                append(text, block.end, text.length)
            }
            return EditorTextActionResult(
                output,
                mapSelectionIntoBlock(selection, block.start, block.end, previousStart),
            )
        }

        if (block.after >= text.length) return EditorTextActionResult(text, selection)
        val nextEnd = text.indexOf('\n', block.after).let { if (it < 0) text.length else it }
        val blockSegment = text.substring(block.start, block.end)
        val nextSegment = text.substring(block.after, nextEnd)
        val output = buildString(text.length) {
            append(text, 0, block.start)
            append(nextSegment)
            append('\n')
            append(blockSegment)
            append(text, nextEnd, text.length)
        }
        return EditorTextActionResult(
            output,
            mapSelectionIntoBlock(selection, block.start, block.end, block.start + nextSegment.length + 1),
        )
    }

    private data class LineBlock(
        val start: Int,
        val end: Int,
        val after: Int,
    )

    private fun selectedBlock(text: String, selection: TextRange): LineBlock? {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        val firstLineStart = lineStart(text, start)
        val lastProbe = if (end > start) end - 1 else end
        val lastLineStart = lineStart(text, lastProbe)
        val lastLineEnd = text.indexOf('\n', lastLineStart).let { if (it < 0) text.length else it }
        val after = if (lastLineEnd < text.length) lastLineEnd + 1 else lastLineEnd
        return LineBlock(firstLineStart, lastLineEnd, after)
    }

    private fun lineStart(text: String, position: Int): Int =
        text.lastIndexOf('\n', (position - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }

    private fun mapSelectionIntoBlock(
        selection: TextRange,
        oldStart: Int,
        oldEnd: Int,
        newStart: Int,
    ): TextRange {
        fun map(position: Int): Int =
            (newStart + (position - oldStart).coerceIn(0, oldEnd - oldStart))
        return TextRange(map(selection.start), map(selection.end))
    }
}
