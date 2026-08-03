package com.quickdaily

import androidx.compose.ui.text.TextRange

/**
 * Pure line-prefix transformations shared by the Activity editor and the overlay editor.
 *
 * The six block-level prefixes are mutually exclusive on the cursor line. Inline actions
 * intentionally do not go through this policy.
 */
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

    private data class LineBounds(
        val start: Int,
        val end: Int,
    )

    private data class PrefixMatch(
        val action: EditorToolbarAction,
        val start: Int,
        val end: Int,
        val level: Int? = null,
        val number: Long? = null,
        val checked: Boolean = false,
    )

    fun isMutuallyExclusive(action: EditorToolbarAction): Boolean =
        action in mutuallyExclusiveActions

    /** Removes a different block prefix from the cursor line without applying a new action. */
    fun clearOtherPrefix(
        text: String,
        selection: TextRange,
        action: EditorToolbarAction,
    ): EditorTextActionResult {
        if (!isMutuallyExclusive(action)) return EditorTextActionResult(text, selection)
        val bounds = lineBounds(text, selection.start)
        val prefix = prefixAt(text, bounds)
        return if (prefix == null || prefix.action == action) {
            EditorTextActionResult(text, selection)
        } else {
            clearPrefix(text, selection, bounds, prefix)
        }
    }

    /** Applies a mutually-exclusive block action at the current cursor line. */
    fun apply(
        text: String,
        selection: TextRange,
        action: EditorToolbarAction,
    ): EditorTextActionResult {
        if (!isMutuallyExclusive(action)) return EditorTextActionResult(text, selection)

        val prepared = clearOtherPrefix(text, selection, action)
        val bounds = lineBounds(prepared.text, prepared.selection.start)
        val currentPrefix = prefixAt(prepared.text, bounds)

        return when (action) {
            EditorToolbarAction.TASK -> toggleTask(prepared.text, prepared.selection, bounds, currentPrefix)
            EditorToolbarAction.LIST -> toggleSimplePrefix(
                prepared.text,
                prepared.selection,
                bounds,
                currentPrefix,
                EditorToolbarAction.LIST,
                "- ",
            )
            EditorToolbarAction.HEADING -> cycleHeading(
                prepared.text,
                prepared.selection,
                bounds,
                currentPrefix,
            )
            EditorToolbarAction.QUOTE -> {
                val toggled = toggleSimplePrefix(
                    prepared.text,
                    prepared.selection,
                    bounds,
                    currentPrefix,
                    EditorToolbarAction.QUOTE,
                    "> ",
                )
                // Preserve the existing multi-line quote behavior when a selection spans lines.
                if (prepared.selection.min != prepared.selection.max) {
                    EditorTextActionPolicy.prefixLines(
                        prepared.text,
                        prepared.selection,
                        "> ",
                    )
                } else {
                    toggled
                }
            }
            EditorToolbarAction.CODE_BLOCK -> if (currentPrefix?.action == EditorToolbarAction.CODE_BLOCK) {
                clearPrefix(prepared.text, prepared.selection, bounds, currentPrefix)
            } else {
                EditorTextActionPolicy.codeBlock(prepared.text, prepared.selection)
            }
            EditorToolbarAction.ORDERED_LIST -> toggleOrderedList(
                prepared.text,
                prepared.selection,
                bounds,
                currentPrefix,
            )
            else -> EditorTextActionResult(prepared.text, prepared.selection)
        }
    }

    private fun toggleTask(
        text: String,
        selection: TextRange,
        bounds: LineBounds,
        currentPrefix: PrefixMatch?,
    ): EditorTextActionResult {
        val line = text.substring(bounds.start, bounds.end)
        val indentLength = leadingWhitespaceLength(line)
        val indent = line.substring(0, indentLength)
        val rest = if (currentPrefix?.action == EditorToolbarAction.TASK) {
            line.substring(currentPrefix.end - bounds.start).trimStart()
        } else {
            line.substring(indentLength).trimStart()
        }
        val checked = currentPrefix?.takeIf { it.action == EditorToolbarAction.TASK }?.checked == true
        val prefix = if (currentPrefix?.action == EditorToolbarAction.TASK && checked) {
            ""
        } else if (currentPrefix?.action == EditorToolbarAction.TASK) {
            "- [x] "
        } else {
            "- [ ] "
        }
        val newLine = indent + prefix + rest
        return rewriteLine(
            text = text,
            selection = selection,
            bounds = bounds,
            oldContentStart = if (currentPrefix?.action == EditorToolbarAction.TASK) {
                currentPrefix.end
            } else {
                bounds.start + indentLength
            },
            newLine = newLine,
            newContentStart = indentLength + prefix.length,
        )
    }

    private fun cycleHeading(
        text: String,
        selection: TextRange,
        bounds: LineBounds,
        currentPrefix: PrefixMatch?,
    ): EditorTextActionResult {
        val line = text.substring(bounds.start, bounds.end)
        val indentLength = leadingWhitespaceLength(line)
        val indent = line.substring(0, indentLength)
        val rest = if (currentPrefix?.action == EditorToolbarAction.HEADING) {
            line.substring(currentPrefix.end - bounds.start).trimStart()
        } else {
            line.substring(indentLength).trimStart()
        }
        val currentLevel = currentPrefix?.takeIf { it.action == EditorToolbarAction.HEADING }?.level
        val nextLevel = when {
            currentLevel == null -> 1
            currentLevel < 3 -> currentLevel + 1
            else -> 0
        }
        val prefix = if (nextLevel == 0) "" else "#".repeat(nextLevel) + " "
        return rewriteLine(
            text = text,
            selection = selection,
            bounds = bounds,
            oldContentStart = if (currentPrefix?.action == EditorToolbarAction.HEADING) {
                currentPrefix.end
            } else {
                bounds.start + indentLength
            },
            newLine = indent + prefix + rest,
            newContentStart = indentLength + prefix.length,
        )
    }

    private fun toggleSimplePrefix(
        text: String,
        selection: TextRange,
        bounds: LineBounds,
        currentPrefix: PrefixMatch?,
        action: EditorToolbarAction,
        prefix: String,
    ): EditorTextActionResult {
        val line = text.substring(bounds.start, bounds.end)
        val indentLength = leadingWhitespaceLength(line)
        val indent = line.substring(0, indentLength)
        val hasSamePrefix = currentPrefix?.action == action
        val rest = if (hasSamePrefix) {
            line.substring(currentPrefix!!.end - bounds.start).trimStart()
        } else {
            line.substring(indentLength).trimStart()
        }
        val appliedPrefix = if (hasSamePrefix) "" else prefix
        return rewriteLine(
            text = text,
            selection = selection,
            bounds = bounds,
            oldContentStart = if (hasSamePrefix) currentPrefix!!.end else bounds.start + indentLength,
            newLine = indent + appliedPrefix + rest,
            newContentStart = indentLength + appliedPrefix.length,
        )
    }

    private fun toggleOrderedList(
        text: String,
        selection: TextRange,
        bounds: LineBounds,
        currentPrefix: PrefixMatch?,
    ): EditorTextActionResult {
        val line = text.substring(bounds.start, bounds.end)
        val indentLength = leadingWhitespaceLength(line)
        val indent = line.substring(0, indentLength)
        val hasSamePrefix = currentPrefix?.action == EditorToolbarAction.ORDERED_LIST
        val rest = if (hasSamePrefix) {
            line.substring(currentPrefix!!.end - bounds.start).trimStart()
        } else {
            line.substring(indentLength).trimStart()
        }
        val appliedPrefix = if (hasSamePrefix) {
            ""
        } else {
            (previousNumber(text, bounds.start) ?: 1).toString() + ". "
        }
        return rewriteLine(
            text = text,
            selection = selection,
            bounds = bounds,
            oldContentStart = if (hasSamePrefix) currentPrefix!!.end else bounds.start + indentLength,
            newLine = indent + appliedPrefix + rest,
            newContentStart = indentLength + appliedPrefix.length,
        )
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

    private fun clearPrefix(
        text: String,
        selection: TextRange,
        bounds: LineBounds,
        prefix: PrefixMatch,
    ): EditorTextActionResult {
        val line = text.substring(bounds.start, bounds.end)
        val indentLength = prefix.start - bounds.start
        val indent = line.substring(0, indentLength)
        val rest = line.substring(prefix.end - bounds.start).trimStart()
        return rewriteLine(
            text = text,
            selection = selection,
            bounds = bounds,
            oldContentStart = prefix.end,
            newLine = indent + rest,
            newContentStart = indentLength,
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
                position <= bounds.start -> position
                position <= oldContentStart -> bounds.start + newContentStart
                position < bounds.end -> bounds.start + newContentStart +
                    (position - oldContentStart).coerceAtMost(oldContentLength)
                else -> position + delta
            }
            return mapped.coerceIn(0, output.length)
        }
        return EditorTextActionResult(output, TextRange(map(selection.start), map(selection.end)))
    }

    private fun prefixAt(text: String, bounds: LineBounds): PrefixMatch? {
        val line = text.substring(bounds.start, bounds.end)
        val indentLength = leadingWhitespaceLength(line)
        val content = line.substring(indentLength)
        fun match(
            action: EditorToolbarAction,
            regex: Regex,
            level: Int? = null,
            number: Long? = null,
            checked: Boolean = false,
        ): PrefixMatch? {
            val found = regex.find(content) ?: return null
            return PrefixMatch(
                action = action,
                start = bounds.start + indentLength,
                end = bounds.start + indentLength + found.value.length,
                level = level ?: found.groupValues.getOrNull(1)?.takeIf { action == EditorToolbarAction.HEADING }?.length,
                number = number ?: found.groupValues.getOrNull(1)?.toLongOrNull()
                    ?.takeIf { action == EditorToolbarAction.ORDERED_LIST },
                checked = checked || (action == EditorToolbarAction.TASK && found.groupValues.getOrNull(1)?.trim()?.isNotEmpty() == true),
            )
        }

        taskPrefix.find(content)?.let { found ->
            return PrefixMatch(
                action = EditorToolbarAction.TASK,
                start = bounds.start + indentLength,
                end = bounds.start + indentLength + found.value.length,
                checked = found.groupValues[1].trim().isNotEmpty(),
            )
        }
        match(EditorToolbarAction.LIST, listPrefix)?.let { return it }
        headingPrefix.find(content)?.let { found ->
            return PrefixMatch(
                action = EditorToolbarAction.HEADING,
                start = bounds.start + indentLength,
                end = bounds.start + indentLength + found.value.length,
                level = found.groupValues[1].length,
            )
        }
        match(EditorToolbarAction.QUOTE, quotePrefix)?.let { return it }
        match(EditorToolbarAction.CODE_BLOCK, codeBlockPrefix)?.let { return it }
        orderedListPrefix.find(content)?.let { found ->
            return PrefixMatch(
                action = EditorToolbarAction.ORDERED_LIST,
                start = bounds.start + indentLength,
                end = bounds.start + indentLength + found.value.length,
                number = found.groupValues[1].toLongOrNull(),
            )
        }
        return null
    }

    private fun lineBounds(text: String, position: Int): LineBounds {
        val cursor = position.coerceIn(0, text.length)
        val start = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val end = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        return LineBounds(start, end)
    }

    private fun lineStart(text: String, position: Int): Int =
        text.lastIndexOf('\n', (position - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }

    private fun leadingWhitespaceLength(line: String): Int =
        line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) line.length else it }
}
