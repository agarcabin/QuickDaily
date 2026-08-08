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
    fun explicitEntryTitleWinsOverPersistedDraftTitle() {
        assertEquals(
            "2026-08-08 速记",
            FloatingNotePolicy.displayTitleForRequest(
                hasPersistedDraft = true,
                persistedTitle = "今日日记",
                requestedTitle = "2026-08-08 速记",
                fallbackTitle = "fallback",
            ),
        )
    }

    @Test
    fun missingEntryTitleFallsBackToDraftThenTargetTitle() {
        assertEquals(
            "项目 速记",
            FloatingNotePolicy.displayTitleForRequest(
                hasPersistedDraft = true,
                persistedTitle = "项目 速记",
                requestedTitle = "  ",
                fallbackTitle = "fallback",
            ),
        )
        assertEquals(
            "2026-08-08 速记",
            FloatingNotePolicy.displayTitleForRequest(
                hasPersistedDraft = false,
                persistedTitle = null,
                requestedTitle = null,
                fallbackTitle = "2026-08-08 速记",
            ),
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
    fun timingDeltasNeverGoNegativeWhenCallbackClockIsStale() {
        assertEquals(0L, FloatingNoteTiming.elapsedMs(90L, 100L))
        assertEquals(25L, FloatingNoteTiming.elapsedMs(125L, 100L))
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

    @Test
    fun imeDisplayOperationsAreMutuallyExclusivePerWindowRequest() {
        val primary = FloatingNoteImeRequestTracker()
        assertTrue(primary.acceptPrimary())
        assertFalse(primary.acceptPrimary())
        assertFalse(primary.acceptFallback())
        assertTrue(primary.hasAcceptedDisplayOperation())

        val fallback = FloatingNoteImeRequestTracker()
        assertTrue(fallback.acceptFallback())
        assertFalse(fallback.acceptFallback())
        assertFalse(fallback.acceptPrimary())
    }

    @Test
    fun keepDraftSettingDefaultsToDisabled() {
        assertFalse(FloatingNoteEntryPolicy.DEFAULT_KEEP_DRAFT_ON_CLOSE)
    }

    @Test
    fun saveOnCloseDefaultsToEnabled() {
        assertTrue(FloatingNoteEntryPolicy.DEFAULT_SAVE_ON_CLOSE)
    }

    @Test
    fun targetMemoryIsIndependentPerEntry() {
        assertNotEquals(
            FloatingNoteTargetMemory.keyFor(FloatingNoteSource.SIDEBAR),
            FloatingNoteTargetMemory.keyFor(FloatingNoteSource.WIDGET),
        )
        assertNotEquals(
            FloatingNoteTargetMemory.keyFor(FloatingNoteSource.DESKTOP_LAUNCHER),
            FloatingNoteTargetMemory.keyFor(FloatingNoteSource.TILE),
        )
    }

    @Test
    fun fixedTargetRequestDoesNotUseRememberedTarget() {
        assertEquals("/fixed.md", FloatingNotePolicy.targetForRequest(false, null, "/fixed.md", "/remembered.md", rememberTarget = false))
        assertEquals("/remembered.md", FloatingNotePolicy.targetForRequest(false, null, null, "/remembered.md", rememberTarget = true))
    }

    @Test
    fun floatingOpacityDefaultsTo97Percent() {
        assertEquals(97, FloatingNoteAppearance.DEFAULT_OPACITY_PERCENT)
    }


    @Test
    fun editorLifecycleRefreshOnlyRunsWhenTheActivityStops() {
        assertTrue(EditorLifecycleRefreshPolicy.shouldRefreshOn(androidx.lifecycle.Lifecycle.Event.ON_STOP))
        assertFalse(EditorLifecycleRefreshPolicy.shouldRefreshOn(androidx.lifecycle.Lifecycle.Event.ON_PAUSE))
    }

    @Test
    fun draftTargetKeysUseTheDefaultDiaryOrNormalizedPagePath() {
        assertEquals("default-diary", FloatingNoteDraftTargetPolicy.keyForPath(null, ""))
        assertEquals("Daily/today.md", FloatingNoteDraftTargetPolicy.keyForPath("Daily\\today.md", ""))
        assertNotEquals(
            FloatingNoteDraftTargetPolicy.keyForPath("Daily/a.md", ""),
            FloatingNoteDraftTargetPolicy.keyForPath("Daily/b.md", ""),
        )
    }
}
