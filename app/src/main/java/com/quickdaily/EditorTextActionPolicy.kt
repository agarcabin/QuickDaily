package com.quickdaily

import androidx.compose.ui.text.TextRange

data class EditorTextActionResult(
    val text: String,
    val selection: TextRange,
    val clipboardText: String? = null,
)

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

    private fun move(text: String, selection: TextRange, direction: Int): EditorTextActionResult {
        val block = selectedBlock(text, selection) ?: return EditorTextActionResult(text, selection)
        if (direction < 0) {
            if (block.start == 0) return EditorTextActionResult(text, selection)
            val previousStart = text.lastIndexOf('\n', block.start - 2).let { if (it < 0) 0 else it + 1 }
            val previousSegment = text.substring(previousStart, block.start)
            val blockSegment = text.substring(block.start, block.after)
            val output = buildString(text.length) {
                append(text, 0, previousStart)
                append(blockSegment)
                append(previousSegment)
                append(text, block.after, text.length)
            }
            return EditorTextActionResult(
                output,
                mapSelectionIntoBlock(selection, block.start, block.after, previousStart),
            )
        }

        if (block.after >= text.length) return EditorTextActionResult(text, selection)
        val nextEnd = text.indexOf('\n', block.after).let { if (it < 0) text.length else it }
        val nextAfter = if (nextEnd < text.length) nextEnd + 1 else nextEnd
        val blockSegment = text.substring(block.start, block.after)
        val nextSegment = text.substring(block.after, nextAfter)
        val output = buildString(text.length) {
            append(text, 0, block.start)
            append(nextSegment)
            append(blockSegment)
            append(text, nextAfter, text.length)
        }
        return EditorTextActionResult(
            output,
            mapSelectionIntoBlock(selection, block.start, block.after, block.start + nextSegment.length),
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
        oldAfter: Int,
        newStart: Int,
    ): TextRange {
        fun map(position: Int): Int =
            (newStart + (position - oldStart).coerceIn(0, oldAfter - oldStart))
        return TextRange(map(selection.start), map(selection.end))
    }
}
