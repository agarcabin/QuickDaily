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
    fun changedDiskContentPromptsEvenWhenMtimeMovesBackwards() {
        val loaded = com.quickdaily.util.FileFingerprint(true, 4L, "old", 200L)
        val changed = loaded.copy(sha256 = "new", lastModified = 100L)

        assertTrue(
            EditorConflictPolicy.shouldPrompt(
                isDirty = true,
                observedMtime = 100L,
                lastLoadedMtime = 200L,
                ignoredExternalMtime = 0L,
                observedFingerprint = changed,
                lastLoadedFingerprint = loaded,
            ),
        )
    }

    @Test
    fun acknowledgedSameContentDoesNotPromptAfterMtimeOnlyChange() {
        val loaded = com.quickdaily.util.FileFingerprint(true, 4L, "same", 200L)
        val sameContent = loaded.copy(lastModified = 100L)

        assertFalse(
            EditorConflictPolicy.shouldPrompt(
                isDirty = true,
                observedMtime = 100L,
                lastLoadedMtime = 200L,
                ignoredExternalMtime = 0L,
                observedFingerprint = sameContent,
                lastLoadedFingerprint = loaded,
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
