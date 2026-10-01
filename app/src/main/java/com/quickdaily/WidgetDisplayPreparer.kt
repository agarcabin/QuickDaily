package com.quickdaily

import android.content.Context

internal data class PreparedReadWidgetItem(
    val source: ReadWidgetItem,
    val display: DisplayText,
    val hiddenRanges: List<DisplayRange>,
    val collapseLine: Boolean,
)

internal data class PreparedTaskWidgetItem(
    val source: TaskWidgetItem,
    val display: DisplayText,
    val hiddenRanges: List<DisplayRange>,
    val collapseLine: Boolean,
)

internal object WidgetDisplayPreparer {
    fun rules(context: Context): List<String> = HiddenTextConfig.read(
        context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE),
    )

    fun prepareRead(
        items: List<ReadWidgetItem>,
        rules: List<String>,
        imagePlaceholder: String = "[图片]",
    ): List<PreparedReadWidgetItem> {
        val displays = items.map { item -> readDisplay(item, imagePlaceholder) }
        val flow = items.mapIndexed { index, item ->
            HiddenFlowItem(
                display = displays[index],
                sourceText = item.rawLine,
                joinsPrevious = true,
                barrierBefore = item.type == "image",
                barrierAfter = item.type == "image",
            )
        }
        val hidden = HiddenTextPolicy.apply(flow, rules)
        val collapsed = HiddenTextPolicy.collapseExtraRows(flow, hidden)
        return items.indices.map { index ->
            PreparedReadWidgetItem(items[index], displays[index], hidden[index], collapsed[index])
        }
    }

    fun prepareTasks(
        items: List<TaskWidgetItem>,
        rules: List<String>,
    ): List<PreparedTaskWidgetItem> {
        val displays = items.map { item ->
            if (item.isDateHeader || item.isFileHeader) {
                DisplayText(item.text)
            } else {
                DisplayText(item.text.trim())
            }
        }
        val flow = items.mapIndexed { index, item ->
            if (item.isDateHeader || item.isFileHeader) {
                HiddenFlowItem(
                    // Headers delimit task groups but are not task text targets.
                    display = DisplayText(""),
                    sourceText = null,
                    joinsPrevious = false,
                    barrierBefore = true,
                    barrierAfter = true,
                )
            } else {
                val previous = items.getOrNull(index - 1)
                val continuous = previous != null &&
                    !previous.isDateHeader &&
                    !previous.isFileHeader &&
                    previous.sourcePath == item.sourcePath &&
                    previous.lineIndex >= 0 &&
                    item.lineIndex == previous.lineIndex + 1
                HiddenFlowItem(
                    display = displays[index],
                    sourceText = item.rawLine,
                    joinsPrevious = continuous,
                    barrierBefore = !continuous,
                )
            }
        }
        val hidden = HiddenTextPolicy.apply(flow, rules)
        val collapsed = HiddenTextPolicy.collapseExtraRows(flow, hidden)
        return items.indices.map { index ->
            PreparedTaskWidgetItem(items[index], displays[index], hidden[index], collapsed[index])
        }
    }

    private fun readDisplay(item: ReadWidgetItem, imagePlaceholder: String): DisplayText = when (item.type) {
        "task", "heading" -> InlineDisplayPolicy.render(item.text, InlineSurface.WIDGET)
        "quote" -> DisplayText(" ▍ ").plus(InlineDisplayPolicy.render(item.text, InlineSurface.WIDGET))
        "bullet" -> DisplayText("  •  ").plus(InlineDisplayPolicy.render(item.text, InlineSurface.WIDGET))
        "image" -> DisplayText(imagePlaceholder)
        "hr" -> DisplayText(" ─────────────────")
        "blank" -> DisplayText("")
        else -> if (item.renderInlineMarkdown) {
            InlineDisplayPolicy.render(item.text, InlineSurface.WIDGET)
        } else {
            DisplayText(item.text)
        }
    }

    private fun DisplayText.plus(other: DisplayText): DisplayText {
        val offset = text.length
        return DisplayText(
            text = text + other.text,
            styles = styles + other.styles.map { it.copy(start = it.start + offset, end = it.end + offset) },
        )
    }
}
