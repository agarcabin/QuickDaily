package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskWidgetTaskParserTest {
    @Test
    fun showCompletedOptInKeepsCheckedChildrenVisibleAndIndented() {
        val body = listOf(
            "- [ ] 出差",
            "\t- [ ] 订票",
            "        - [x] 已确认酒店",
            "- [ ] 普通任务",
            "- [x] 已完成父任务",
            "    - [ ] 不应显示",
        ).joinToString("\n")
        val items = TaskWidgetTaskParser.parseVisible(
            body,
            "/vault/2026-07-30.md",
            "2026-07-30",
            showCompleted = true,
        )

        assertEquals(6, items.size)
        assertEquals(listOf(0, 1, 2, 0, 0, 1), items.map { it.indentLevel })
        assertTrue(items[2].checked)
        assertEquals("2026-07-30", items[0].date)
    }

    @Test
    fun completedTasksAndTheirTreesAreHiddenByDefault() {
        val body = """
            - [ ] Parent
              - [x] Completed child
                - [ ] Hidden grandchild
            - [x] Completed parent
              - [ ] Hidden child
            - [ ] Other
        """.trimIndent()

        val items = TaskWidgetTaskParser.parseVisible(body, "/vault/page.md")

        assertEquals(listOf("Parent", "Other"), items.map { it.text })
    }

    @Test
    fun completedRootHidesItsWholeNestedTree() {
        val items = TaskWidgetTaskParser.parseVisible(
            """- [x] Parent
                |  - [ ] Child
                |- [ ] Other
            """.trimMargin(),
            "/vault/page.md",
        )

        assertEquals(listOf("Other"), items.map { it.text })
    }

    @Test
    fun togglePreservesIndentationAndSupportsUndo() {
        val checked = TaskWidgetTaskParser.toggleLine("\t- [ ] Child")
        val unchecked = TaskWidgetTaskParser.toggleLine(checked!!)

        assertEquals("\t- [x] Child", checked)
        assertEquals("\t- [ ] Child", unchecked)
    }

    @Test
    fun malformedLineIsNotToggled() {
        assertFalse(TaskWidgetTaskParser.toggleLine("  - regular bullet") != null)
    }
}
