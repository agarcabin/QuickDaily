package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    @Test
    fun existingOverlayDoesNotAcceptASecondWidgetTarget() {
        assertFalse(FloatingNotePolicy.shouldLoadNewRequest(overlayShowing = true))
        assertTrue(FloatingNotePolicy.shouldLoadNewRequest(overlayShowing = false))
    }

    @Test
    fun newRequestTargetWinsWhenOnlyOldTargetMetadataRemains() {
        assertEquals(
            "/new-page.md",
            FloatingNotePolicy.targetForRequest(false, "/old-page.md", "/new-page.md"),
        )
        assertEquals(
            "/old-page.md",
            FloatingNotePolicy.targetForRequest(true, "/old-page.md", "/new-page.md"),
        )
    }

    @Test
    fun launchGateAllowsOnlyOnePendingOverlayStart() {
        FloatingNoteLaunchGate.release()
        assertTrue(FloatingNoteLaunchGate.acquire())
        assertFalse(FloatingNoteLaunchGate.acquire())

        FloatingNoteLaunchGate.release()
        assertTrue(FloatingNoteLaunchGate.acquire())
        FloatingNoteLaunchGate.release()
    }

    @Test
    fun eachEntryRequestHasItsOwnTimingIdAndPreservesSource() {
        val widget = FloatingNoteRequest(FloatingNoteSource.WIDGET, returnToHomeAfterClose = false)
        val desktop = FloatingNoteRequest(FloatingNoteSource.DESKTOP_LAUNCHER, returnToHomeAfterClose = false)
        val sidebar = FloatingNoteRequest(FloatingNoteSource.SIDEBAR, returnToHomeAfterClose = false)

        assertNotEquals(widget.requestId, desktop.requestId)
        assertNotEquals(desktop.requestId, sidebar.requestId)
        assertEquals(FloatingNoteSource.WIDGET, widget.source)
        assertEquals(FloatingNoteSource.DESKTOP_LAUNCHER, desktop.source)
        assertEquals(FloatingNoteSource.SIDEBAR, sidebar.source)
    }

    @Test
    fun overlayUsesDedicatedImePolicyWhileActivityKeepsDefaultPolicy() {
        assertEquals(FloatingNoteImePolicy.ActivityDefault, FloatingNoteImePolicy.ActivityDefault)
        assertEquals(FloatingNoteImePolicy.OverlayInstant, FloatingNoteImePolicy.OverlayInstant)
        assertNotEquals(FloatingNoteImePolicy.ActivityDefault, FloatingNoteImePolicy.OverlayInstant)
    }

    @Test
    fun finalizingRecordingRejectsTheNextRecordingUntilTheJobCompletes() {
        assertTrue(FloatingNoteRecordingPolicy.canStart(FloatingNoteRecordingState.Idle))
        assertFalse(FloatingNoteRecordingPolicy.canStart(FloatingNoteRecordingState.Recording))
        assertFalse(FloatingNoteRecordingPolicy.canStart(FloatingNoteRecordingState.Finalizing))
        assertEquals(
            FloatingNoteRecordingState.Finalizing,
            FloatingNoteRecordingPolicy.afterStop(FloatingNoteRecordingState.Recording),
        )
        assertEquals(
            FloatingNoteRecordingState.Idle,
            FloatingNoteRecordingPolicy.afterFinalize(FloatingNoteRecordingState.Finalizing),
        )
    }
}
