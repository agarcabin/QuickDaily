package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingCoachVisualPolicyTest {
    @Test
    fun dashCycleUsesTheFullOnPlusOffPeriod() {
        val period = FloatingCoachDashPolicy.periodPx(
            dashOnPx = FloatingCoachDashPolicy.DASH_ON_DP,
            dashOffPx = FloatingCoachDashPolicy.DASH_OFF_DP,
        )

        assertEquals(20f, period)
        assertEquals(0f, period % period)
    }
}
