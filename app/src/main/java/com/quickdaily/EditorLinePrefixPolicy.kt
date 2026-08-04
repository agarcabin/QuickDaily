package com.quickdaily

import androidx.compose.ui.text.TextRange

/** Pure line-prefix transformations shared by the Activity editor and the overlay editor. */
object EditorLinePrefixPolicy {
    private val mutuallyExclusiveActions = setOf(
        EditorToolbarAction.TASK,
        EditorToolbarAction.LIST,
        EditorToolbarAction.HEADING,
        EditorToolbarAction.QUOTE,
        EditorToolbarAction.CODE_BLOCK,
        EditorToolbarAction.ORDERED_LIST,
    )

    private val taskPrefix = Regex("""^-\s*\[\s*([ xX])\s*\]\s*""")
    private val listPrefix = Regex("""^-\s+""")
    private val headingPrefix = Regex("""^(#{1,6})\s+""")
    private val quotePrefix = Regex("""^>\s+""")
    private val codeBlockPrefix = Regex("""^```""")
    private val orderedListPrefix = Regex("""^(\d+)\.\s+""")

    private data class LineBounds(val start: Int, val end: Int)

    private data class PrefixMatch(
        val action: EditorToolbarAction,
        val start: Int,
        val end: Int,
        val level: Int? = null,
        val number: Long? = null,
        val checked: Boolean = false,
    )

    private data class LineMapping(
        val oldStart: Int,
        val oldEnd: Int,
        val oldContentStart: Int,
        val oldContentLength: Int,
        val newStart: Int,
        val newContentStart: Int,
    )

    fun isMutuallyExclusive(action: EditorToolbarAction): Boolean = action in mutuallyExclusiveActions

    fun clearOtherPrefix(text: String, selection: TextRange, action: EditorToolbarAction): EditorTextActionResult {
        if (!isMutuallyExclusive(action)) return EditorTextActionResult(text, selection)
        return if (selection.start == selection.end) {
            val bounds = lineBounds(text, selection.start)
            val current = prefixAt(text, bounds)
            if (current == null || current.action == action) {
                EditorTextActionResult(text, selection)
            } else {
                rewriteSingleLine(text, selection, action, bounds, current, forceOnlyClear = true)
            }
        } else {
            applyLines(text, selection, action, clearOnly = true)
        }
    }

    fun apply(text: String, selection: TextRange, action: EditorToolbarAction): EditorTextActionResult {
        if (!isMutuallyExclusive(action)) return EditorTextActionResult(text, selection)
        if (action == EditorToolbarAction.CODE_BLOCK && selection.start != selection.end) {
            return applyFencedBlock(text, selection)
        }
        return if (selection.start == selection.end) {
            val bounds = lineBounds(text, selection.start)
            rewriteSingleLine(text, selection, action, bounds, prefixAt(text, bounds))
        } else {
            applyLines(text, selection, action, clearOnly = false)
        }
    }

    private fun rewriteSingleLine(
        text: String,
        selection: TextRange,
        action: EditorToolbarAction,
        bounds: LineBounds,
        currentPrefix: PrefixMatch?,
        forceOnlyClear: Boolean = false,
    ): EditorTextActionResult {
        val line = text.substring(bounds.start, bounds.end)
        val indentLength = leadingWhitespaceLength(line)
        val indent = line.substring(0, indentLength)
        val currentIsSame = currentPrefix?.action == action
        val bodyStart = currentPrefix?.end ?: bounds.start + indentLength
        val body = line.substring((bodyStart - bounds.start).coerceIn(0, line.length)).trimStart()
        val replacementPrefix = if (forceOnlyClear) {
            ""
        } else {
            when (action) {
                EditorToolbarAction.TASK -> when {
                    currentIsSame && currentPrefix?.checked == true -> ""
                    currentIsSame -> "- [x] "
                    else -> "- [ ] "
                }
                EditorToolbarAction.LIST -> if (currentIsSame) "" else "- "
                EditorToolbarAction.HEADING -> {
                    val currentLevel = currentPrefix?.takeIf { currentIsSame }?.level
                    val nextLevel = when {
                        currentLevel == null -> 1
                        currentLevel < 3 -> currentLevel + 1
                        else -> 0
                    }
                    if (nextLevel == 0) "" else "#".repeat(nextLevel) + " "
                }
                EditorToolbarAction.QUOTE -> if (currentIsSame) "" else "> "
                EditorToolbarAction.CODE_BLOCK -> if (currentIsSame) "" else "```"
                EditorToolbarAction.ORDERED_LIST -> if (currentIsSame) "" else "${previousNumber(text, bounds.start) ?: 1}. "
                else -> ""
            }
        }
        val oldContentStart = currentPrefix?.end ?: bounds.start + indentLength
        return rewriteLine(
            text = text,
            selection = selection,
            bounds = bounds,
            oldContentStart = oldContentStart,
            newLine = indent + replacementPrefix + body,
            newContentStart = indentLength + replacementPrefix.length,
        )
    }

    private fun applyLines(
        text: String,
        selection: TextRange,
        action: EditorToolbarAction,
        clearOnly: Boolean,
    ): EditorTextActionResult {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        val firstLineStart = lineStart(text, start)
        val lastLineStart = lineStart(text, if (end > start) end - 1 else end)
        val boundsList = buildList {
            var lineStart = firstLineStart
            while (true) {
                val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
                add(LineBounds(lineStart, lineEnd))
                if (lineStart == lastLineStart) break
                lineStart = lineEnd + 1
            }
        }
        val matches = boundsList.map { prefixAt(text, it) }
        val removeSame = !clearOnly && matches.isNotEmpty() && matches.all { it?.action == action }
        val builder = StringBuilder(text.substring(0, firstLineStart))
        val mappings = mutableListOf<LineMapping>()
        boundsList.forEachIndexed { index, bounds ->
            val line = text.substring(bounds.start, bounds.end)
            val indentLength = leadingWhitespaceLength(line)
            val indent = line.substring(0, indentLength)
            val current = matches[index]
            val currentIsSame = current?.action == action
            val bodyStart = current?.end ?: bounds.start + indentLength
            val body = line.substring((bodyStart - bounds.start).coerceIn(0, line.length)).trimStart()
            val prefix = if (clearOnly || (removeSame && currentIsSame)) {
                ""
            } else {
                when (action) {
                    EditorToolbarAction.TASK -> "- [ ] "
                    EditorToolbarAction.LIST -> "- "
                    EditorToolbarAction.HEADING -> "# "
                    EditorToolbarAction.QUOTE -> "> "
                    EditorToolbarAction.CODE_BLOCK -> "```"
                    EditorToolbarAction.ORDERED_LIST -> "${index + (previousNumber(text, firstLineStart) ?: 1)}. "
                    else -> ""
                }
            }
            val newLine = indent + prefix + body
            val newStart = builder.length
            builder.append(newLine)
            mappings += LineMapping(
                oldStart = bounds.start,
                oldEnd = bounds.end,
                oldContentStart = bodyStart,
                oldContentLength = (bounds.end - bodyStart).coerceAtLeast(0),
                newStart = newStart,
                newContentStart = indentLength + prefix.length,
            )
            if (index < boundsList.lastIndex) builder.append('\n')
        }
        val output = builder.toString() + text.substring(boundsList.last().end)
        fun map(position: Int): Int {
            val mapping = mappings.firstOrNull { position <= it.oldEnd && position >= it.oldStart }
            if (mapping != null) {
                val relative = (position - mapping.oldContentStart).coerceIn(0, mapping.oldContentLength)
                return (mapping.newStart + mapping.newContentStart + relative).coerceIn(0, output.length)
            }
            val delta = output.length - text.length
            return (position + delta).coerceIn(0, output.length)
        }
        return EditorTextActionResult(output, TextRange(map(selection.start), map(selection.end)))
    }

    private fun applyFencedBlock(text: String, selection: TextRange): EditorTextActionResult {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        val selected = text.substring(start, end)
        val insertion = "```\n$selected\n```"
        val output = text.substring(0, start) + insertion + text.substring(end)
        val offset = 4
        return EditorTextActionResult(
            output,
            TextRange(selection.start + offset, selection.end + offset),
        )
    }

    private fun rewriteLine(
        text: String,
        selection: TextRange,
        bounds: LineBounds,
        oldContentStart: Int,
        newLine: String,
        newContentStart: Int,
    ): EditorTextActionResult {
        val oldLineLength = bounds.end - bounds.start
        val output = text.substring(0, bounds.start) + newLine + text.substring(bounds.end)
        val delta = newLine.length - oldLineLength
        val oldContentLength = (bounds.end - oldContentStart).coerceAtLeast(0)
        fun map(position: Int): Int {
            val mapped = when {
                position < bounds.start -> position
                position <= oldContentStart -> bounds.start + newContentStart
                position < bounds.end -> bounds.start + newContentStart +
                    (position - oldContentStart).coerceAtMost(oldContentLength)
                else -> position + delta
            }
            return mapped.coerceIn(0, output.length)
        }
        return EditorTextActionResult(output, TextRange(map(selection.start), map(selection.end)))
    }

    private fun previousNumber(text: String, currentLineStart: Int): Long? {
        if (currentLineStart <= 0) return null
        val previousEnd = currentLineStart - 1
        val previousStart = lineStart(text, previousEnd)
        return prefixAt(text, LineBounds(previousStart, previousEnd))
            ?.takeIf { it.action == EditorToolbarAction.ORDERED_LIST }
            ?.number
            ?.let { if (it == Long.MAX_VALUE) null else it + 1 }
    }

    private fun prefixAt(text: String, bounds: LineBounds): PrefixMatch? {
        val line = text.substring(bounds.start, bounds.end)
        val indentLength = leadingWhitespaceLength(line)
        val content = line.substring(indentLength)
        taskPrefix.find(content)?.let { found ->
            return PrefixMatch(
                EditorToolbarAction.TASK,
                bounds.start + indentLength,
                bounds.start + indentLength + found.value.length,
                checked = found.groupValues[1].trim().isNotEmpty(),
            )
        }
        listPrefix.find(content)?.let { found ->
            return PrefixMatch(EditorToolbarAction.LIST, bounds.start + indentLength, bounds.start + indentLength + found.value.length)
        }
        headingPrefix.find(content)?.let { found ->
            return PrefixMatch(EditorToolbarAction.HEADING, bounds.start + indentLength, bounds.start + indentLength + found.value.length, level = found.groupValues[1].length)
        }
        quotePrefix.find(content)?.let { found ->
            return PrefixMatch(EditorToolbarAction.QUOTE, bounds.start + indentLength, bounds.start + indentLength + found.value.length)
        }
        codeBlockPrefix.find(content)?.let { found ->
            return PrefixMatch(EditorToolbarAction.CODE_BLOCK, bounds.start + indentLength, bounds.start + indentLength + found.value.length)
        }
        orderedListPrefix.find(content)?.let { found ->
            return PrefixMatch(EditorToolbarAction.ORDERED_LIST, bounds.start + indentLength, bounds.start + indentLength + found.value.length, number = found.groupValues[1].toLongOrNull())
        }
        return null
    }

    private fun lineBounds(text: String, position: Int): LineBounds {
        val cursor = position.coerceIn(0, text.length)
        val start = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val end = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        return LineBounds(start, end)
    }

    private fun lineStart(text: String, position: Int): Int =
        text.lastIndexOf('\n', (position - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }

    private fun leadingWhitespaceLength(line: String): Int =
        line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) line.length else it }
}