package com.quickdaily

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class TextIndentPolicyTest {
    @Test
    fun indentAddsFourSpacesToCurrentLineAndKeepsCursorRelative() {
        val result = TextIndentPolicy.indent("one", TextRange(1))
        assertEquals("    one", result.text)
        assertEquals(TextRange(5), result.selection)
    }

    @Test
    fun indentAddsFourSpacesToEverySelectedLine() {
        val result = TextIndentPolicy.indent("one\ntwo\nthree", TextRange(1, 9))
        assertEquals("    one\n    two\n    three", result.text)
        assertEquals(TextRange(5, 21), result.selection)
    }

    @Test
    fun outdentRemovesTabOrUpToFourLeadingSpaces() {
        val result = TextIndentPolicy.outdent("\tone\n    two\nthree", TextRange(0, 18))
        assertEquals("one\ntwo\nthree", result.text)
        assertEquals(TextRange(0, 13), result.selection)
    }
}
