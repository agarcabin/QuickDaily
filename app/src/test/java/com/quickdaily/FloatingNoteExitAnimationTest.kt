package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingNoteExitAnimationTest {
    @Test
    fun onlyWidgetSourceWithVisibleBoundsIsEligible() {
        val current = FloatingNoteScreenBounds(100, 100, 900, 500)
        val target = FloatingNoteScreenBounds(20, 40, 180, 120)

        assertTrue(
            FloatingNoteExitAnimationPolicy.isEligible(
                FloatingNoteSource.WIDGET,
                target,
                current,
                screenWidth = 1080,
                screenHeight = 2400,
            ),
        )
        assertFalse(
            FloatingNoteExitAnimationPolicy.isEligible(
                FloatingNoteSource.SHORTCUT,
                target,
                current,
                screenWidth = 1080,
                screenHeight = 2400,
            ),
        )
        assertFalse(
            FloatingNoteExitAnimationPolicy.isEligible(
                FloatingNoteSource.WIDGET,
                null,
                current,
                screenWidth = 1080,
                screenHeight = 2400,
            ),
        )
    }

    @Test
    fun transformEndsAtWidgetCenterAndSize() {
        val current = FloatingNoteScreenBounds(100, 200, 900, 600)
        val target = FloatingNoteScreenBounds(20, 40, 180, 120)

        val transform = FloatingNoteExitAnimationPolicy.transform(current, target, 1f)

        assertEquals(0.2f, transform.scaleX, 0.0001f)
        assertEquals(0.2f, transform.scaleY, 0.0001f)
        assertEquals(-400f, transform.translationX, 0.0001f)
        assertEquals(-320f, transform.translationY, 0.0001f)
        assertTrue(transform.alpha < 1f)
    }
}
