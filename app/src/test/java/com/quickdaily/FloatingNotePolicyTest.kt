package com.quickdaily

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingNotePolicyTest {
    @Test
    fun prefillAppliesOnlyToAnEmptyDraft() {
        assertTrue(FloatingNotePolicy.shouldApplyPrefill("", 0, 0, "来自入口的速记"))
        assertFalse(FloatingNotePolicy.shouldApplyPrefill("已有草稿", 0, 0, "新的预填充"))
        assertFalse(FloatingNotePolicy.shouldApplyPrefill("", 1, 0, "新的预填充"))
        assertFalse(FloatingNotePolicy.shouldApplyPrefill("", 0, 1, "新的预填充"))
        assertFalse(FloatingNotePolicy.shouldApplyPrefill("", 0, 0, "   "))
    }

    @Test
    fun persistedCloseDestinationWinsOverTheNextLaunchRequest() {
        assertTrue(FloatingNotePolicy.effectiveReturnHome(true, false))
        assertFalse(FloatingNotePolicy.effectiveReturnHome(false, true))
        assertTrue(FloatingNotePolicy.effectiveReturnHome(null, true))
        assertFalse(FloatingNotePolicy.effectiveReturnHome(null, false))
    }
}
