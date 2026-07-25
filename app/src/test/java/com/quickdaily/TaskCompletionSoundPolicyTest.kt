package com.quickdaily

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCompletionSoundPolicyTest {
    @Test
    fun soundPlaysOnlyWhenEnabledAndSaveSucceeded() {
        assertTrue(TaskCompletionSoundPolicy.shouldPlay(enabled = true, saveSucceeded = true))
        assertFalse(TaskCompletionSoundPolicy.shouldPlay(enabled = false, saveSucceeded = true))
        assertFalse(TaskCompletionSoundPolicy.shouldPlay(enabled = true, saveSucceeded = false))
    }
}
