package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDisplayPreparerTest {
    @Test
    fun readWidgetUsesFinalMarkdownTextAndKeepsPlainModeLiteral() {
        val items = listOf(
            ReadWidgetItem(
                type = "quote",
                text = "**hello** #tag [[Page|Alias]]",
            ),
            ReadWidgetItem(
                type = "plain",
                text = "**literal** [[Page]]",
                renderInlineMarkdown = false,
            ),
        )

        val prepared = WidgetDisplayPreparer.prepareRead(items, listOf("hello"))

        assertEquals(" ▍ hello #tag [[Alias]]", prepared[0].display.text)
        assertEquals("**literal** [[Page]]", prepared[1].display.text)
        assertEquals(
            listOf(
                DisplayRange(3, 8),
            ),
            prepared[0].hiddenRanges,
        )
        assertTrue(!prepared[0].collapseLine)
        assertTrue(prepared[0].display.styles.any { it.kind == DisplayStyleKind.WIKILINK })
    }

    @Test
    fun readWidgetCanMatchAcrossOrdinaryLinesButImagesAreBarriers() {
        val visible = listOf(
            ReadWidgetItem("plain", "alpha"),
            ReadWidgetItem("plain", "beta"),
        )
        val blocked = listOf(
            ReadWidgetItem("plain", "alpha"),
            ReadWidgetItem("image"),
            ReadWidgetItem("plain", "beta"),
        )

        val visiblePrepared = WidgetDisplayPreparer.prepareRead(visible, listOf("alpha\nbeta"))
        val blockedPrepared = WidgetDisplayPreparer.prepareRead(blocked, listOf("alpha\nbeta"))

        assertEquals(listOf(DisplayRange(0, 5)), visiblePrepared[0].hiddenRanges)
        assertEquals(listOf(DisplayRange(0, 4)), visiblePrepared[1].hiddenRanges)
        assertTrue(blockedPrepared.all { it.hiddenRanges.isEmpty() })
    }

    @Test
    fun readWidgetCanMatchPastedMarkdownSourceAfterRenderTransforms() {
        val items = listOf(
            ReadWidgetItem(
                type = "heading",
                text = "最近修改",
                rawLine = "# 最近修改",
                lineSeparator = "\n",
            ),
            ReadWidgetItem(
                type = "plain",
                text = "key: value",
                rawLine = "  key: value",
                lineSeparator = "",
            ),
        )

        val prepared = WidgetDisplayPreparer.prepareRead(
            items,
            listOf("# 最近修改\n  key: value"),
        )

        assertEquals("最近修改", prepared[0].display.text)
        assertEquals("key: value", prepared[1].display.text)
        assertEquals(listOf(DisplayRange(0, 4)), prepared[0].hiddenRanges)
        assertEquals(listOf(DisplayRange(0, 10)), prepared[1].hiddenRanges)
        assertTrue(!prepared[0].collapseLine)
        assertTrue(prepared[1].collapseLine)
    }

    @Test
    fun taskWidgetOnlyJoinsConsecutiveRowsFromTheSameSourceAndPreservesPayload() {
        val first = task("alpha", "/vault/a.md", 4)
        val second = task("beta", "/vault/a.md", 5)
        val gap = task("gamma", "/vault/a.md", 7)
        val otherPath = task("delta", "/vault/b.md", 8)

        val prepared = WidgetDisplayPreparer.prepareTasks(
            listOf(first, second, gap, otherPath),
            listOf("alpha\nbeta"),
        )

        assertEquals(listOf(DisplayRange(0, 5)), prepared[0].hiddenRanges)
        assertEquals(listOf(DisplayRange(0, 4)), prepared[1].hiddenRanges)
        assertTrue(prepared[2].hiddenRanges.isEmpty())
        assertTrue(prepared[3].hiddenRanges.isEmpty())
        assertEquals(first, prepared[0].source)
        assertEquals("- [ ] beta", prepared[1].source.rawLine)
    }

    @Test
    fun taskHeadersAreBarriersAndAreNeverHidden() {
        val header = TaskWidgetTaskParser.fileHeader("a.md", true)
        val tasks = listOf(
            task("alpha", "/vault/a.md", 0),
            header,
            task("beta", "/vault/a.md", 1),
        )

        val prepared = WidgetDisplayPreparer.prepareTasks(tasks, listOf("alpha\nbeta"))

        assertTrue(prepared.all { it.hiddenRanges.isEmpty() })
        assertEquals("a", prepared[1].display.text)
    }

    @Test
    fun taskHeadersRemainVisibleEvenWhenTheirLabelMatches() {
        val header = TaskWidgetTaskParser.fileHeader("private.md", true)

        val prepared = WidgetDisplayPreparer.prepareTasks(listOf(header), listOf("private"))

        assertEquals("private", prepared.single().display.text)
        assertTrue(prepared.single().hiddenRanges.isEmpty())
    }

    private fun task(text: String, path: String, lineIndex: Int) = TaskWidgetItem(
        text = text,
        sourcePath = path,
        lineIndex = lineIndex,
        rawLine = "- [ ] $text",
        checked = false,
        indentLevel = 0,
        rootLineIndex = lineIndex,
        lineSeparator = "\n",
    )
}
