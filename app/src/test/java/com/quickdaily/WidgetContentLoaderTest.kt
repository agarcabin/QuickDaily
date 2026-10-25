package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContentLoaderTest {
    @Test
    fun orderedReadItemsKeepTheirNumbersThroughDisplayPreparation() {
        val body = "1. First\n2. **Second**\n10. Tenth\n- Bullet"
        val items = WidgetContentLoader.parseReadLines(body, body, "/vault/today.md", 0)
        val prepared = WidgetDisplayPreparer.prepareRead(items, emptyList())

        assertEquals(listOf("ordered", "ordered", "ordered", "bullet"), items.map { it.type })
        assertEquals(listOf("1. First", "2. Second", "10. Tenth", "  •  Bullet"), prepared.map { it.display.text })
        assertTrue(prepared[1].display.styles.any { it.kind == DisplayStyleKind.BOLD && it.start == 3 && it.end == 9 })
        assertEquals(listOf(0, 1, 2, 3), items.map { it.lineIndex })
        assertEquals("2. **Second**", items[1].rawLine)
    }

    @Test
    fun orderedReadItemsRequireWhitespaceAfterTheNumber() {
        val body = "1.5 is a decimal\n2026.10.25\n3.no space\n3.\tTabbed item"
        val items = WidgetContentLoader.parseReadLines(body, body, "/vault/today.md", 0)
        val prepared = WidgetDisplayPreparer.prepareRead(items, emptyList())

        assertEquals(listOf("plain", "plain", "plain", "ordered"), items.map { it.type })
        assertEquals(body.lines(), prepared.map { it.display.text })
    }

    @Test
    fun orderedReadItemsRespectHiddenTextAndPlainMode() {
        val body = "3. private public"
        val items = WidgetContentLoader.parseReadLines(body, body, "/vault/today.md", 0)
        val prepared = WidgetDisplayPreparer.prepareRead(items, listOf("private"))
        val plain = WidgetContentLoader.parsePlainReadLines("3. **literal**")

        assertEquals("3. private public", prepared.single().display.text)
        // Existing source-text matching hides the entire matching source row.
        assertEquals(listOf(DisplayRange(0, body.length)), prepared.single().hiddenRanges)
        assertEquals(body, prepared.single().source.rawLine)
        assertEquals("3. **literal**", WidgetDisplayPreparer.prepareRead(plain, emptyList()).single().display.text)
    }

    @Test
    fun markdownReadItemsKeepNestedTasksAndShowCompletedTasks() {
        val body = listOf(
            "# Today",
            "- [ ] Parent",
            "\t- [ ] Child",
            "- [x] Completed",
            "    - [ ] Hidden child",
        ).joinToString("\n")

        val items = WidgetContentLoader.parseReadLines(
            displayContent = body,
            taskBody = body,
            sourcePath = "/vault/2026-07-30.md",
            bodyLineOffset = 0,
        )
        val tasks = items.filter { it.type == "task" }

        assertEquals(listOf("Parent", "Child", "Completed", "Hidden child"), tasks.map { it.text })
        assertEquals(listOf(0, 1, 0, 1), tasks.map { it.indentLevel })
        assertEquals(listOf(1, 2, 3, 4), tasks.map { it.lineIndex })
        assertEquals("\t- [ ] Child", tasks[1].rawLine)
        assertEquals("/vault/2026-07-30.md", tasks[1].sourcePath)
        assertTrue(tasks[2].checked)
        assertTrue(items.any { it.type == "heading" && it.text == "Today" })
    }

    @Test
    fun markdownReadItemsMapBodyLinesWhenFrontmatterIsDisplayed() {
        val body = "- [ ] Parent\n  - [ ] Child"
        val display = "---\ntitle: Today\n---\n$body"

        val tasks = WidgetContentLoader.parseReadLines(
            displayContent = display,
            taskBody = body,
            sourcePath = "/vault/2026-07-30.md",
            bodyLineOffset = 3,
        ).filter { it.type == "task" }

        assertEquals(listOf(3, 4), tasks.map { it.lineIndex })
        assertEquals(listOf("- [ ] Parent", "  - [ ] Child"), tasks.map { it.rawLine })
    }

    @Test
    fun plainReadItemsKeepMarkdownAsLiteralText() {
        val items = WidgetContentLoader.parsePlainReadLines(
            "- [ ] **literal task**\n  - [x] child"
        )

        assertEquals(listOf("plain", "plain"), items.map { it.type })
        assertEquals(listOf("- [ ] **literal task**", "- [x] child"), items.map { it.text })
        assertTrue(items.all { !it.renderInlineMarkdown && it.lineIndex == -1 })
    }
}
