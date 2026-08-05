package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherIconPolicyTest {
    @Test
    fun monetPreferenceNeverChangesFixedIcon() {
        assertEquals(LauncherIconMode.BLUE, LauncherIconPolicy.mode(true, 33))
        assertEquals(LauncherIconMode.BLUE, LauncherIconPolicy.mode(false, 33))
        assertEquals(LauncherIconMode.BLUE, LauncherIconPolicy.mode(true, 32))
    }
}
