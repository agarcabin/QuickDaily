package com.quickdaily

import android.app.ActivityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingNoteObsidianLaunchPolicyTest {
    @Test
    fun launchIsDisabledByDefault() {
        assertFalse(FloatingNoteObsidianLaunchPolicy.DEFAULT_ENABLED)
    }

    @Test
    fun foregroundProcessDoesNotCountAsBackground() {
        assertFalse(
            FloatingNoteObsidianLaunchPolicy.isBackgroundProcessImportance(
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
            ),
        )
    }

    @Test
    fun nonForegroundProcessCountsAsBackground() {
        assertTrue(
            FloatingNoteObsidianLaunchPolicy.isBackgroundProcessImportance(
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND,
            ),
        )
    }

    @Test
    fun vaultNameSupportsAndroidAndWindowsStyleSeparators() {
        assertEquals("MyVault", FloatingNoteObsidianLaunchPolicy.vaultName("/storage/emulated/0/MyVault/"))
        assertEquals("MyVault", FloatingNoteObsidianLaunchPolicy.vaultName("C:\\Notes\\MyVault\\"))
    }

    @Test
    fun savedRelativePathUsesSelectedPage() {
        assertEquals(
            "Projects/QuickDaily.md",
            FloatingNoteObsidianLaunchPolicy.savedRelativePath(
                targetRelativePath = "  Projects/QuickDaily.md  ",
                diaryFolder = "Daily",
                today = "2026-08-11",
            ),
        )
    }

    @Test
    fun savedRelativePathFallsBackToTodaysDiary() {
        assertEquals(
            "Daily/2026-08-11.md",
            FloatingNoteObsidianLaunchPolicy.savedRelativePath(
                targetRelativePath = null,
                diaryFolder = "Daily/",
                today = "2026-08-11",
            ),
        )
    }
}
