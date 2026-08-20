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

    @Test
    fun dateGroupingAddsHeadersOnlyWhenNonTodayTasksExist() {
        val today = TaskWidgetItem(
            text = "今天",
            sourcePath = "/vault/today.md",
            date = "2026-08-20",
            lineIndex = 0,
            rawLine = "- [ ] 今天",
            checked = false,
            indentLevel = 0,
            rootLineIndex = 0,
        )
        val yesterday = today.copy(
            text = "昨天",
            sourcePath = "/vault/yesterday.md",
            date = "2026-08-19",
            rawLine = "- [ ] 昨天",
        )

        assertEquals(listOf(today), TaskWidgetDateGrouping.withHeaders(listOf(today), "2026-08-20"))

        val grouped = TaskWidgetDateGrouping.withHeaders(listOf(today, yesterday), "2026-08-20")
        assertEquals(listOf("8月20日，周四", "今天", "8月19日，周三", "昨天"), grouped.map { it.text })
        assertTrue(grouped[0].isDateHeader)
        assertTrue(grouped[0].isFirstDateHeader)
        assertTrue(grouped[2].isDateHeader)
        assertFalse(grouped[2].isFirstDateHeader)
    }

    @Test
    fun dateHeaderUsesMonthDayAndChineseWeekday() {
        assertEquals("8月24日，周一", TaskWidgetDateGrouping.labelFor("2026-08-24"))
    }
}
