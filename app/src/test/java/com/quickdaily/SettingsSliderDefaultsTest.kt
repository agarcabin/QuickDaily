package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSliderDefaultsTest {
    @Test
    fun widgetColorDefaultsMatchTheDarkWidgetAppearance() {
        assertEquals(0xFF202124L, SettingsSliderDefaults.DEFAULT_WIDGET_BACKGROUND_COLOR)
        assertEquals(32, SettingsSliderDefaults.DEFAULT_RED)
        assertEquals(33, SettingsSliderDefaults.DEFAULT_GREEN)
        assertEquals(36, SettingsSliderDefaults.DEFAULT_BLUE)
    }

    @Test
    fun channelResetChangesOnlyTheRequestedChannel() {
        val color = 0xFFABCDEFL
        assertEquals(0xFF20CDEFL, SettingsSliderDefaults.resetRed(color))
        assertEquals(0xFFAB21EFL, SettingsSliderDefaults.resetGreen(color))
        assertEquals(0xFFABCD24L, SettingsSliderDefaults.resetBlue(color))
    }

    @Test
    fun channelValuesAreClampedToAnArgbChannel() {
        assertEquals(0xFFFF2124L, SettingsSliderDefaults.withRed(0xFF202124L, 300))
        assertEquals(0xFF20FF24L, SettingsSliderDefaults.withGreen(0xFF202124L, 300))
        assertEquals(0xFF2021FFL, SettingsSliderDefaults.withBlue(0xFF202124L, 300))
    }
}
