package com.quickdaily

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorTextActionPolicyTest {
    @Test
    fun cutCurrentLineCopiesWithoutNewlineAndRemovesLine() {
        val result = EditorTextActionPolicy.cutLine("one\ntwo\nthree", TextRange(5))
        assertEquals("one\nthree", result.text)
        assertEquals("two", result.clipboardText)
        assertEquals(TextRange(4), result.selection)
    }

    @Test
    fun cutFinalLineDoesNotLeaveTrailingBlankLine() {
        val result = EditorTextActionPolicy.cutLine("one\ntwo", TextRange(6))
        assertEquals("one", result.text)
        assertEquals("two", result.clipboardText)
        assertEquals(TextRange(3), result.selection)
    }

    @Test
    fun selectedLinesMoveAsOneBlockWithoutMovingChildrenSpecially() {
        val result = EditorTextActionPolicy.moveLineDown("one\ntwo\nthree", TextRange(0, 3))
        assertEquals("two\none\nthree", result.text)
        assertEquals(TextRange(4, 7), result.selection)
    }

    @Test
    fun movingFirstLineUpAndLastLineDownIsNoOp() {
        assertEquals(
            "one\ntwo",
            EditorTextActionPolicy.moveLineUp("one\ntwo", TextRange(1)).text,
        )
        assertEquals(
            "one\ntwo",
            EditorTextActionPolicy.moveLineDown("one\ntwo", TextRange(6)).text,
        )
    }

    @Test
    fun insertReplacesSelectionAndPlacesCursorAfterInsertion() {
        val result = EditorTextActionPolicy.insert("abc", TextRange(1, 2), "[[")
        assertEquals("a[[c", result.text)
        assertEquals(TextRange(3), result.selection)
    }
}
