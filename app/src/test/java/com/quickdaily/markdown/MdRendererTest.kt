package com.quickdaily.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
