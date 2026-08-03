package com.quickdaily

import com.quickdaily.ui.EditorCursorPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorCursorPolicyTest {
    @Test
    fun acceptsEmptyAndEndCursorForTheMatchingLayout() {
        assertTrue(EditorCursorPolicy.isSelectionValid(0, 0, 0))
        assertTrue(EditorCursorPolicy.isSelectionValid(0, 12, 12))
        assertTrue(EditorCursorPolicy.isSelectionValid(12, 12, 12))
    }

    @Test
    fun rejectsSelectionWhenTheLayoutTextIsStaleOrOffsetIsOutOfBounds() {
        assertFalse(EditorCursorPolicy.isSelectionValid(13, 13, 12))
        assertFalse(EditorCursorPolicy.isSelectionValid(0, 14, 12))
        assertFalse(EditorCursorPolicy.isSelectionValid(-1, 0, 12))
        assertFalse(EditorCursorPolicy.isSelectionValid(8, 7, 12))
    }
}
