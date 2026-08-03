package com.quickdaily

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickTileLaunchPolicyTest {
    @Test
    fun pendingIntentPathStartsAtApi34() {
        assertFalse(QuickTileLaunchPolicy.usePendingIntent(33))
        assertTrue(QuickTileLaunchPolicy.usePendingIntent(34))
        assertTrue(QuickTileLaunchPolicy.usePendingIntent(35))
    }
}
