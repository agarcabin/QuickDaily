package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAppearanceTest {
    @Test
    fun newWidgetDefaultOpacityIsSeventyFivePercent() {
        assertEquals(75, WidgetAppearance.DEFAULT_OPACITY_PERCENT)
    }

    @Test
    fun newWidgetsDefaultToDarkAndExistingSystemStyleIsPreserved() {
        assertEquals("dark", WidgetAppearance.DEFAULT_STYLE)
        assertEquals("dark", WidgetAppearance.resolveStyle(null))
        assertEquals("system", WidgetAppearance.resolveStyle("system"))
    }

    @Test
    fun darkWidgetUsesThemeContainerWhenLightThemePrimaryLacksContrast() {
        val primary = 0xFF006A60.toInt()
        val primaryContainer = 0xFF80F0DC.toInt()

        assertEquals(
            primaryContainer,
            WidgetAppearance.selectIconAccent(
                primary = primary,
                primaryContainer = primaryContainer,
                base = 0xFF202124.toInt(),
                lightBackground = false,
            ),
        )
    }

    @Test
    fun lightWidgetKeepsThemePrimaryWhenItHasContrast() {
        val primary = 0xFF006A60.toInt()
        val primaryContainer = 0xFF80F0DC.toInt()

        assertEquals(
            primary,
            WidgetAppearance.selectIconAccent(
                primary = primary,
                primaryContainer = primaryContainer,
                base = 0xFFFFFFFF.toInt(),
                lightBackground = true,
            ),
        )
    }
}
