package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContentLoaderTest {
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

        assertEquals(listOf(0, 1), tasks.map { it.lineIndex })
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
