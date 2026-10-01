package com.quickdaily.markdown

import org.junit.Assert.assertEquals
import org.junit.Test
import com.quickdaily.TaskActionRef

class MdRendererTest {
    @Test
    fun nestedTasksKeepTheirOriginalTaskIndexAndIndentLevel() {
        val lines = parseLines(
            """
            |- [ ] Parent
            |  - [x] Child
            |      - [ ] Grandchild
            |- [ ] Other
            """.trimMargin()
        )
        val tasks = lines.filterIsInstance<MdLine.Task>()

        assertEquals(listOf(0, 1, 2, 0), tasks.map { it.indentLevel })
        assertEquals(listOf(0, 1, 2, 3), tasks.map { it.index })
        assertEquals(listOf("Parent", "Child", "Grandchild", "Other"), tasks.map { it.text })
    }

    @Test
    fun actionRefToggleKeepsFrontmatterAndOriginalLineSeparators() {
        val source = "---\r\ntitle: Today\r\n---\r\n- [ ] task\r\nnext\r\n- [ ] second"

        val result = toggleTaskCheck(
            source,
            TaskActionRef(3, "- [ ] task", "\r\n"),
        )

        assertEquals(
            "---\r\ntitle: Today\r\n---\r\n- [x] task\r\nnext\r\n- [ ] second",
            result,
        )
    }

    @Test
    fun actionRefToggleRejectsAStaleRawLine() {
        val source = "- [ ] current\r\nnext"

        assertEquals(
            source,
            toggleTaskCheck(source, TaskActionRef(0, "- [ ] old", "\r\n")),
        )
    }
}
