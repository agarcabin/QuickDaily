package com.quickdaily

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal data class EditorAutoIndentState(
    val indentStart: Int,
    val indentEnd: Int,
)

internal data class EditorAutoIndentResult(
    val value: TextFieldValue,
    val state: EditorAutoIndentState?,
)

internal object EditorAutoIndentPolicy {
    private val taskPrefix = Regex("""^([-+*])\s+\[\s*[ xX]\s*]\s+""")
    private val unorderedListPrefix = Regex("""^([-+*])\s+""")
    private val orderedListPrefix = Regex("""^(\d+)\.\s+""")

    fun apply(
        previous: TextFieldValue,
        proposed: TextFieldValue,
        state: EditorAutoIndentState?,
    ): EditorAutoIndentResult {
        if (proposed.composition != null) {
            return EditorAutoIndentResult(proposed, null)
        }

        if (state != null && isImmediateIndentBackspace(previous, proposed, state)) {
            val remainingIndentEnd = (state.indentEnd - 1).coerceAtLeast(state.indentStart)
            val text = proposed.text.removeRange(state.indentStart, remainingIndentEnd)
            return EditorAutoIndentResult(
                TextFieldValue(text = text, selection = TextRange(state.indentStart)),
                null,
            )
        }

        val cursor = singleInsertedNewlineCursor(previous, proposed)
            ?: return EditorAutoIndentResult(proposed, null)
        val lineStart = previous.text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        var indentEnd = lineStart
        while (indentEnd < previous.text.length && previous.text[indentEnd].isIndentWhitespace()) {
            indentEnd++
        }
        if (cursor < indentEnd) {
            return EditorAutoIndentResult(proposed, null)
        }

        val sourceIndent = previous.text.substring(lineStart, indentEnd)
        val indent = continuationIndent(sourceIndent)
        val lineEnd = previous.text.indexOf('\n', cursor).let {
            if (it < 0) previous.text.length else it
        }
        val lineContent = previous.text.substring(indentEnd, lineEnd)
        val structuralPrefix = continuationPrefix(lineContent, cursor - indentEnd)
        val autoPrefix = indent + structuralPrefix
        if (autoPrefix.isEmpty()) {
            return EditorAutoIndentResult(proposed, null)
        }

        val insertionStart = cursor + 1
        val text = proposed.text.substring(0, insertionStart) + autoPrefix + proposed.text.substring(insertionStart)
        val selection = TextRange(insertionStart + autoPrefix.length)
        return EditorAutoIndentResult(
            TextFieldValue(text = text, selection = selection),
            EditorAutoIndentState(insertionStart, insertionStart + autoPrefix.length),
        )
    }

    private fun continuationIndent(source: String): String {
        if (source.isEmpty()) return ""
        var columns = 0
        source.forEach { character ->
            columns = if (character == '\t') {
                columns + (TextIndentPolicy.INDENT_WIDTH - columns % TextIndentPolicy.INDENT_WIDTH)
            } else {
                columns + 1
            }
        }
        val indentLevels =
            (columns + TextIndentPolicy.INDENT_WIDTH - 1) / TextIndentPolicy.INDENT_WIDTH
        return TextIndentPolicy.INDENT_UNIT.repeat(indentLevels)
    }

    private fun continuationPrefix(lineContent: String, cursorInContent: Int): String {
        taskPrefix.find(lineContent)?.let { match ->
            if (cursorInContent >= match.value.length) {
                return "${match.groupValues[1]} [ ] "
            }
        }
        unorderedListPrefix.find(lineContent)?.let { match ->
            if (cursorInContent >= match.value.length) {
                return "${match.groupValues[1]} "
            }
        }
        orderedListPrefix.find(lineContent)?.let { match ->
            if (cursorInContent >= match.value.length) {
                val current = match.groupValues[1]
                val next = current.toLongOrNull()
                    ?.takeIf { it < Long.MAX_VALUE }
                    ?.plus(1)
                    ?.toString()
                    ?: current
                return "$next. "
            }
        }
        return ""
    }

    private fun singleInsertedNewlineCursor(
        previous: TextFieldValue,
        proposed: TextFieldValue,
    ): Int? {
        if (!previous.selection.collapsed || !proposed.selection.collapsed) return null
        if (proposed.text.length != previous.text.length + 1) return null
        val cursor = previous.selection.start.coerceIn(0, previous.text.length)
        if (proposed.selection.start != cursor + 1) return null
        if (proposed.text.getOrNull(cursor) != '\n') return null
        return cursor.takeIf {
            proposed.text.regionMatches(0, previous.text, 0, cursor) &&
                proposed.text.regionMatches(cursor + 1, previous.text, cursor, previous.text.length - cursor)
        }
    }

    private fun isImmediateIndentBackspace(
        previous: TextFieldValue,
        proposed: TextFieldValue,
        state: EditorAutoIndentState,
    ): Boolean {
        if (!previous.selection.collapsed || !proposed.selection.collapsed) return false
        if (previous.selection.start != state.indentEnd) return false
        if (state.indentStart !in 0..state.indentEnd || state.indentEnd > previous.text.length) return false
        if (previous.text.indexOf('\n', state.indentStart).let { it >= 0 && it < state.indentEnd }) return false
        val lineEnd = previous.text.indexOf('\n', state.indentStart).let { if (it < 0) previous.text.length else it }
        if (lineEnd != state.indentEnd) return false
        if (proposed.text.length != previous.text.length - 1) return false
        if (proposed.selection.start != state.indentEnd - 1) return false
        return previous.text.removeRange(state.indentEnd - 1, state.indentEnd) == proposed.text
    }

    private fun Char.isIndentWhitespace(): Boolean = this == ' ' || this == '\t'
}
