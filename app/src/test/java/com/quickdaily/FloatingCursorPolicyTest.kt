package com.quickdaily

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingCursorPolicyTest {
    @Test
    fun darkBackgroundUsesWhiteCursor() {
        assertEquals(Color.White, FloatingCursorPolicy.colorFor(Color(0.1f, 0.1f, 0.1f)))
    }

    @Test
    fun lightBackgroundUsesBlackCursor() {
        assertEquals(Color.Black, FloatingCursorPolicy.colorFor(Color(0.9f, 0.9f, 0.9f)))
    }

    @Test
    fun thresholdBoundaryUsesBlackCursor() {
        val gray = 0.7353569f
        assertEquals(Color.Black, FloatingCursorPolicy.colorFor(Color(gray, gray, gray)))
    }

    @Test
    fun darkBackgroundBrightnessDefaultsToFortyPercentAndSystemShowsControl() {
        assertEquals(40, com.quickdaily.ui.theme.QuickDailyThemePreferences.DEFAULT_DARK_BACKGROUND_BRIGHTNESS)
        assertTrue(com.quickdaily.ui.theme.shouldShowDarkBackgroundBrightness(com.quickdaily.ui.theme.QuickDailyNightMode.DARK))
        assertTrue(com.quickdaily.ui.theme.shouldShowDarkBackgroundBrightness(com.quickdaily.ui.theme.QuickDailyNightMode.SYSTEM))
        assertFalse(com.quickdaily.ui.theme.shouldShowDarkBackgroundBrightness(com.quickdaily.ui.theme.QuickDailyNightMode.LIGHT))
    }
}
