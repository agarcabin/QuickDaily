package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingNotePositionPolicyTest {
    @Test
    fun clampKeepsWindowInsideVisibleScreen() {
        assertEquals(
            FloatingNotePosition(0, 700),
            FloatingNotePositionPolicy.clamp(
                FloatingNotePosition(-20, 900),
                screenWidth = 1080,
                screenHeight = 900,
                windowWidth = 700,
                windowHeight = 200,
            ),
        )
    }

    @Test
    fun defaultPositionIsCenteredHorizontally() {
        assertEquals(
            FloatingNotePosition(190, 450),
            FloatingNotePositionPolicy.defaultPosition(1080, 1800, 700, 300),
        )
    }
}
