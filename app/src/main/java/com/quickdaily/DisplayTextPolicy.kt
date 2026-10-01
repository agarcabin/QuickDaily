package com.quickdaily

import com.quickdaily.util.ContentUtil

/** A style range in the final, user-visible text. Offsets are UTF-16 offsets. */
internal data class DisplayStyleRange(
    val start: Int,
    val end: Int,
    val kind: DisplayStyleKind,
)

internal enum class DisplayStyleKind {
    BOLD,
    ITALIC,
    STRIKETHROUGH,
    CODE,
    LINK,
    WIKILINK,
}

/** Text after the current surface's Markdown transformations, before platform spans. */
internal data class DisplayText(
    val text: String,
    val styles: List<DisplayStyleRange> = emptyList(),
)

internal data class DisplayRange(
    val start: Int,
    val end: Int,
)

/** A display item in a matching flow. Newlines between items are synthetic and never owned. */
internal data class HiddenFlowItem(
    val display: DisplayText,
    /** The source line before Markdown parsing, when this item came from a document. */
    val sourceText: String? = null,
    val joinsPrevious: Boolean = true,
    val barrierBefore: Boolean = false,
    val barrierAfter: Boolean = false,
)

data class TaskActionRef(
    /** Zero-based line index in the source text supplied to the renderer. */
    val lineIndex: Int,
    val rawLine: String,
    val lineSeparator: String,
)

internal data class SourceLineRecord(
    val index: Int,
    val rawLine: String,
    val separator: String,
)

/** Keeps raw line separators while still offering canonical LF text for matching. */
internal class SourceDocument private constructor(
    val source: String,
    val lines: List<SourceLineRecord>,
    val bodyStartLine: Int,
    val hasFrontmatter: Boolean,
) {
    fun bodyTextPreservingSeparators(): String =
        lines.drop(bodyStartLine.coerceIn(0, lines.size))
            .joinToString(separator = "") { it.rawLine + it.separator }

    fun replaceLine(lineIndex: Int, replacement: String): String {
        if (lineIndex !in lines.indices) return source
        return buildString(source.length + replacement.length - lines[lineIndex].rawLine.length) {
            lines.forEach { line ->
                append(if (line.index == lineIndex) replacement else line.rawLine)
                append(line.separator)
            }
        }
    }

    companion object {
        fun from(source: String): SourceDocument {
            val canonical = ContentUtil.canonicalizeLineEndings(source)
            val parsed = ContentUtil.parseFrontmatter(source)
            val bodyStartOffset = if (parsed.hasFrontmatter) {
                (canonical.length - parsed.body.length).coerceIn(0, canonical.length)
            } else {
                0
            }
            val prefix = canonical.substring(0, bodyStartOffset)
            val bodyStartLine = if (prefix.isEmpty()) {
                0
            } else {
                prefix.count { it == '\n' } + if (prefix.last() == '\n') 0 else 1
            }
            return SourceDocument(
                source = source,
                lines = splitLines(source),
                bodyStartLine = bodyStartLine,
                hasFrontmatter = parsed.hasFrontmatter,
            )
        }

        fun splitLines(source: String): List<SourceLineRecord> {
            val result = mutableListOf<SourceLineRecord>()
            var lineStart = 0
            var index = 0
            var lineIndex = 0
            while (index < source.length) {
                val separatorLength = when (source[index]) {
                    '\r' -> if (index + 1 < source.length && source[index + 1] == '\n') 2 else 1
                    '\n' -> 1
                    else -> 0
                }
                if (separatorLength == 0) {
                    index++
                    continue
                }
                val separator = source.substring(index, index + separatorLength)
                result += SourceLineRecord(
                    index = lineIndex++,
                    rawLine = source.substring(lineStart, index),
                    separator = separator,
                )
                index += separatorLength
                lineStart = index
            }
            result += SourceLineRecord(
                index = lineIndex,
                rawLine = source.substring(lineStart),
                separator = "",
            )
            return result
        }
    }
}

internal enum class InlineSurface {
    EDITOR,
    WIDGET,
}

internal data class WikilinkDisplayToken(
    val endExclusive: Int,
    val displayText: String,
    val valid: Boolean,
)

internal object WikilinkDisplayPolicy {
    fun tokenAt(text: String, start: Int): WikilinkDisplayToken? {
        if (!text.startsWith("[[", start)) return null
        if (start > 0 && text[start - 1] == '!') return null
        val close = text.indexOf("]]", start + 2)
        if (close < 0) return null
        val body = text.substring(start + 2, close)
        val pipe = body.indexOf('|')
        val page = if (pipe < 0) body else body.substring(0, pipe)
        val alias = pipe.takeIf { it >= 0 }?.let { body.substring(it + 1) }
        val valid = page.isNotBlank() && (alias == null || alias.isNotBlank())
        val raw = text.substring(start, close + 2)
        val display = if (valid && alias != null) "[[$alias]]" else raw
        return WikilinkDisplayToken(
            endExclusive = close + 2,
            displayText = display,
            valid = valid,
        )
    }
}

internal object InlineDisplayPolicy {
    fun render(raw: String, surface: InlineSurface): DisplayText {
        val builder = DisplayTextBuilder()
        appendRange(raw, surface, builder)
        return builder.build()
    }

    private fun appendRange(
        raw: String,
        surface: InlineSurface,
        output: DisplayTextBuilder,
    ) {
        var index = 0
        while (index < raw.length) {
            when {
                surface == InlineSurface.WIDGET && raw[index] == '`' -> {
                    val end = raw.indexOf('`', index + 1)
                    if (end > index + 1) {
                        output.append(raw.substring(index + 1, end), DisplayStyleKind.CODE)
                        index = end + 1
                    } else {
                        output.append(raw[index].toString())
                        index++
                    }
                }
                raw.startsWith("![[", index) -> {
                    val end = raw.indexOf("]]", index + 3)
                    if (end >= 0) {
                        output.append(raw.substring(index, end + 2))
                        index = end + 2
                    } else {
                        output.append(raw[index].toString())
                        index++
                    }
                }
                raw.startsWith("[[", index) -> {
                    val token = WikilinkDisplayPolicy.tokenAt(raw, index)
                    if (token != null) {
                        output.append(
                            token.displayText,
                            if (token.valid) DisplayStyleKind.WIKILINK else null,
                        )
                        index = token.endExclusive
                    } else {
                        output.append(raw[index].toString())
                        index++
                    }
                }
                raw.startsWith("**", index) -> {
                    index = appendDelimited(raw, index, "**", surface, DisplayStyleKind.BOLD, output)
                }
                surface == InlineSurface.WIDGET && raw.startsWith("__", index) -> {
                    index = appendDelimited(raw, index, "__", surface, DisplayStyleKind.BOLD, output)
                }
                raw.startsWith("~~", index) -> {
                    index = appendDelimited(raw, index, "~~", surface, DisplayStyleKind.STRIKETHROUGH, output)
                }
                raw[index] == '[' -> {
                    val link = ordinaryLinkAt(raw, index)
                    if (link != null) {
                        output.append(link.label, DisplayStyleKind.LINK)
                        index = link.endExclusive
                    } else {
                        output.append(raw[index].toString())
                        index++
                    }
                }
                (raw[index] == '*' && !raw.startsWith("**", index)) || raw[index] == '_' -> {
                    val marker = raw[index].toString()
                    val end = raw.indexOf(marker, index + 1)
                    if (end > index + 1) {
                        val inner = render(raw.substring(index + 1, end), surface)
                        output.append(inner, DisplayStyleKind.ITALIC)
                        index = end + 1
                    } else {
                        output.append(raw[index].toString())
                        index++
                    }
                }
                else -> {
                    output.append(raw[index].toString())
                    index++
                }
            }
        }
    }

    private fun appendDelimited(
        raw: String,
        start: Int,
        marker: String,
        surface: InlineSurface,
        style: DisplayStyleKind,
        output: DisplayTextBuilder,
    ): Int {
        val end = raw.indexOf(marker, start + marker.length)
        if (end > start + marker.length) {
            val inner = render(raw.substring(start + marker.length, end), surface)
            output.append(inner, style)
            return end + marker.length
        }
        output.append(raw[start].toString())
        return start + 1
    }

    private data class OrdinaryLink(
        val label: String,
        val endExclusive: Int,
    )

    private fun ordinaryLinkAt(raw: String, start: Int): OrdinaryLink? {
        val closeBracket = raw.indexOf("](", start + 1)
        if (closeBracket <= start) return null
        val closeParen = raw.indexOf(')', closeBracket + 2)
        if (closeParen <= closeBracket) return null
        return OrdinaryLink(
            label = raw.substring(start + 1, closeBracket),
            endExclusive = closeParen + 1,
        )
    }
}

private class DisplayTextBuilder {
    private val text = StringBuilder()
    private val styles = mutableListOf<DisplayStyleRange>()

    fun append(value: String, style: DisplayStyleKind? = null) {
        val start = text.length
        text.append(value)
        if (style != null && value.isNotEmpty()) {
            styles += DisplayStyleRange(start, text.length, style)
        }
    }

    fun append(value: DisplayText, extraStyle: DisplayStyleKind? = null) {
        val start = text.length
        text.append(value.text)
        value.styles.forEach { range ->
            styles += range.copy(start = range.start + start, end = range.end + start)
        }
        if (extraStyle != null && value.text.isNotEmpty()) {
            styles += DisplayStyleRange(start, text.length, extraStyle)
        }
    }

    fun build(): DisplayText = DisplayText(text.toString(), styles.toList())
}

internal object HiddenTextPolicy {
    fun normalizeRules(values: List<String>): List<String> = values
        .map(ContentUtil::canonicalizeLineEndings)
        .filter(String::isNotBlank)

    fun ranges(text: String, rules: List<String>): List<DisplayRange> {
        val result = mutableListOf<DisplayRange>()
        normalizeRules(rules).forEach { rule ->
            var start = 0
            while (start <= text.length - rule.length) {
                val match = text.indexOf(rule, start)
                if (match < 0) break
                result += DisplayRange(match, match + rule.length)
                start = match + 1
            }
        }
        return mergeRanges(result)
    }

    fun apply(flow: List<HiddenFlowItem>, rules: List<String>): List<List<DisplayRange>> {
        val normalizedRules = normalizeRules(rules)
        if (normalizedRules.isEmpty()) return List(flow.size) { emptyList() }
        val rangesByItem = List(flow.size) { mutableListOf<DisplayRange>() }
        val group = mutableListOf<Pair<Int, HiddenFlowItem>>()

        fun flush() {
            if (group.isEmpty()) return
            val virtual = StringBuilder()
            val owners = mutableListOf<Owner?>()
            group.forEachIndexed { position, (itemIndex, item) ->
                if (position > 0 && item.joinsPrevious) {
                    virtual.append('\n')
                    owners += null
                }
                item.display.text.forEachIndexed { offset, character ->
                    virtual.append(character)
                    owners += Owner(itemIndex, offset)
                }
            }
            normalizedRules.forEach { rule ->
                var start = 0
                while (start <= virtual.length - rule.length) {
                    val match = virtual.indexOf(rule, start)
                    if (match < 0) break
                    for (position in match until match + rule.length) {
                        owners.getOrNull(position)?.let { owner ->
                            rangesByItem[owner.itemIndex] += DisplayRange(owner.offset, owner.offset + 1)
                        }
                    }
                    start = match + 1
                }
            }

            // A rule is normally matched against the final visible text. When a user
            // pastes Markdown source into the setting, however, parsing may remove
            // syntax such as a heading marker or line indentation before display.
            // Match the original source as a fallback and hide the corresponding
            // display items without ever replacing the source text.
            val rawVirtual = StringBuilder()
            val rawOwners = mutableListOf<Owner?>()
            group.forEachIndexed { position, (itemIndex, item) ->
                if (position > 0 && item.joinsPrevious) {
                    rawVirtual.append('\n')
                    rawOwners += null
                }
                item.sourceText?.forEachIndexed { offset, character ->
                    rawVirtual.append(character)
                    rawOwners += Owner(itemIndex, offset)
                }
            }
            normalizedRules.forEach { rule ->
                var start = 0
                while (start <= rawVirtual.length - rule.length) {
                    val match = rawVirtual.indexOf(rule, start)
                    if (match < 0) break
                    val affectedItems = mutableSetOf<Int>()
                    for (position in match until match + rule.length) {
                        rawOwners.getOrNull(position)?.let { owner ->
                            affectedItems += owner.itemIndex
                        }
                    }
                    affectedItems.forEach { itemIndex ->
                        val displayLength = group
                            .firstOrNull { it.first == itemIndex }
                            ?.second
                            ?.display
                            ?.text
                            ?.length
                            ?: 0
                        if (displayLength > 0) {
                            rangesByItem[itemIndex] += DisplayRange(0, displayLength)
                        }
                    }
                    start = match + 1
                }
            }
            group.clear()
        }

        flow.forEachIndexed { index, item ->
            val previous = group.lastOrNull()?.second
            if (group.isNotEmpty() &&
                (!item.joinsPrevious || item.barrierBefore || previous?.barrierAfter == true)
            ) {
                flush()
            }
            group += index to item
        }
        flush()

        return rangesByItem.map { mergeRanges(it) }
    }

    /**
     * Keeps one blank line for a consecutive fully hidden run and removes the
     * remaining rows at the layout layer. Partial matches must keep their row.
     */
    fun collapseExtraRows(
        flow: List<HiddenFlowItem>,
        rangesByItem: List<List<DisplayRange>>,
    ): List<Boolean> {
        var hiddenRun = false
        return flow.indices.map { index ->
            val item = flow[index]
            if (index > 0 &&
                (!item.joinsPrevious || item.barrierBefore || flow[index - 1].barrierAfter)
            ) {
                hiddenRun = false
            }
            val fullyHidden = isFullyHidden(item.display, rangesByItem.getOrNull(index).orEmpty())
            val collapse = fullyHidden && hiddenRun
            hiddenRun = fullyHidden && !item.barrierAfter
            collapse
        }
    }

    private data class Owner(
        val itemIndex: Int,
        val offset: Int,
    )

    private fun isFullyHidden(display: DisplayText, ranges: List<DisplayRange>): Boolean {
        if (display.text.isEmpty()) return false
        var coveredUntil = 0
        ranges.sortedBy { it.start }.forEach { range ->
            if (range.start > coveredUntil) return false
            coveredUntil = maxOf(coveredUntil, range.end)
        }
        return coveredUntil >= display.text.length
    }

    private fun mergeRanges(ranges: List<DisplayRange>): List<DisplayRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges
            .filter { it.start < it.end }
            .sortedWith(compareBy<DisplayRange> { it.start }.thenBy { it.end })
        if (sorted.isEmpty()) return emptyList()
        val merged = mutableListOf<DisplayRange>()
        sorted.forEach { current ->
            val previous = merged.lastOrNull()
            if (previous == null || current.start > previous.end) {
                merged += current
            } else if (current.end > previous.end) {
                merged[merged.lastIndex] = previous.copy(end = current.end)
            }
        }
        return merged
    }
}
