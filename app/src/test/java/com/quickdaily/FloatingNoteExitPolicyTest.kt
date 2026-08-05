package com.quickdaily

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingNoteExitPolicyTest {
    @Test
    fun closeAlwaysDiscardsButBackFollowsSetting() {
        assertTrue(FloatingNoteExitPolicy.shouldDiscard("close"))
        assertFalse(FloatingNoteExitPolicy.shouldSave("close", true, true))
        assertTrue(FloatingNoteExitPolicy.shouldSave("back", true, true))
        assertFalse(FloatingNoteExitPolicy.shouldSave("back", false, true))
        assertFalse(FloatingNoteExitPolicy.shouldSave("back", true, false))
    }
}
