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

    @Test
    fun capturedPositionWinsWhenReturningFromFullscreen() {
        assertEquals(
            FloatingNotePosition(240, 520),
            FloatingNotePositionPolicy.resolve(
                captured = FloatingNotePosition(240, 520),
                persisted = FloatingNotePosition(0, 0),
                screenWidth = 1080,
                screenHeight = 1800,
                windowWidth = 700,
                windowHeight = 300,
            ),
        )
    }

    @Test
    fun capturedPositionIsClampedToTheCurrentScreen() {
        assertEquals(
            FloatingNotePosition(380, 1500),
            FloatingNotePositionPolicy.resolve(
                captured = FloatingNotePosition(900, 1700),
                persisted = FloatingNotePosition(0, 0),
                screenWidth = 1080,
                screenHeight = 1800,
                windowWidth = 700,
                windowHeight = 300,
            ),
        )
    }
}
