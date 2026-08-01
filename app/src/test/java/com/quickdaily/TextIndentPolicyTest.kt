package com.quickdaily

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class TextIndentPolicyTest {
    @Test
    fun indentAddsTabToCurrentLineAndKeepsCursorRelative() {
        val result = TextIndentPolicy.indent("one", TextRange(1))
        assertEquals("\tone", result.text)
        assertEquals(TextRange(2), result.selection)
    }

    @Test
    fun indentAddsTabToEverySelectedLine() {
        val result = TextIndentPolicy.indent("one\ntwo\nthree", TextRange(1, 9))
        assertEquals("\tone\n\ttwo\n\tthree", result.text)
        assertEquals(TextRange(2, 12), result.selection)
    }

    @Test
    fun outdentRemovesTabOrUpToFourLeadingSpaces() {
        val result = TextIndentPolicy.outdent("\tone\n    two\nthree", TextRange(0, 18))
        assertEquals("one\ntwo\nthree", result.text)
        assertEquals(TextRange(0, 13), result.selection)
    }
}
