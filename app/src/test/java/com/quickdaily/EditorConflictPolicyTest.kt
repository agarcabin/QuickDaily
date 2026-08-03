package com.quickdaily

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorConflictPolicyTest {
    @Test
    fun newerDiskVersionPromptsOnlyWhenLocalContentIsDirty() {
        assertTrue(
            EditorConflictPolicy.shouldPrompt(
                isDirty = true,
                observedMtime = 11L,
                lastLoadedMtime = 10L,
                ignoredExternalMtime = 0L,
            ),
        )
        assertFalse(
            EditorConflictPolicy.shouldPrompt(
                isDirty = false,
                observedMtime = 11L,
                lastLoadedMtime = 10L,
                ignoredExternalMtime = 0L,
            ),
        )
    }

    @Test
    fun acknowledgedExternalVersionDoesNotPromptAgain() {
        assertFalse(
            EditorConflictPolicy.shouldPrompt(
                isDirty = true,
                observedMtime = 11L,
                lastLoadedMtime = 10L,
                ignoredExternalMtime = 11L,
            ),
        )
        assertTrue(
            EditorConflictPolicy.shouldPrompt(
                isDirty = true,
                observedMtime = 12L,
                lastLoadedMtime = 10L,
                ignoredExternalMtime = 11L,
            ),
        )
    }

    @Test
    fun failedOrStaleSaveKeepsDirtyState() {
        assertFalse(EditorConflictPolicy.canClearDirty(false, savedVersion = 1L, currentVersion = 1L))
        assertFalse(EditorConflictPolicy.canClearDirty(true, savedVersion = 1L, currentVersion = 2L))
        assertTrue(EditorConflictPolicy.canClearDirty(true, savedVersion = 2L, currentVersion = 2L))
    }
}
