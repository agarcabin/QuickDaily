package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAppearanceTest {
    @Test
    fun newWidgetDefaultOpacityIsSeventyFivePercent() {
        assertEquals(75, WidgetAppearance.DEFAULT_OPACITY_PERCENT)
    }
}
