package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSizePolicyTest {
    @Test
    fun launcherOptionsDriveCompactLayout() {
        val size = WidgetSizePolicy.fromDp(140, 90)

        assertTrue(size.isTiny)
        assertTrue(size.isShort)
        assertEquals(1, size.readMaxLines)
        assertEquals(1, size.taskMaxLines)
    }

    @Test
    fun largerWidgetKeepsReadableLineLimits() {
        val size = WidgetSizePolicy.fromDp(320, 220)

        assertEquals(3, size.readMaxLines)
        assertEquals(2, size.taskMaxLines)
    }
}
