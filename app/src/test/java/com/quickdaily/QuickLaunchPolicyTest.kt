package com.quickdaily

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickLaunchPolicyTest {
    private val launcherAction = "android.intent.action.MAIN"
    private val launcherCategory = "android.intent.category.LAUNCHER"

    @Test
    fun configuredLauncherWithStorageAccessOpensQuickNote() {
        assertTrue(
            QuickLaunchPolicy.shouldOpenQuickNote(
                action = launcherAction,
                categories = setOf(launcherCategory),
                vaultPath = "C:/Vault",
                hasStorageAccess = true,
                homeEntryMode = HomeEntryMode.OVERLAY.key,
            )
        )
    }    @Test
    fun nonLauncherIntentKeepsExistingDestination() {
        assertFalse(
            QuickLaunchPolicy.shouldOpenQuickNote(
                action = "android.intent.action.SEND",
                categories = setOf(launcherCategory),
                vaultPath = "C:/Vault",
                hasStorageAccess = true
            )
        )
    }

    @Test
    fun launcherWithoutConfiguredVaultFallsBackToHome() {
        assertFalse(
            QuickLaunchPolicy.shouldOpenQuickNote(
                action = launcherAction,
                categories = setOf(launcherCategory),
                vaultPath = "",
                hasStorageAccess = true
            )
        )
    }

    @Test
    fun launcherWithoutStorageAccessFallsBackToHome() {
        assertFalse(
            QuickLaunchPolicy.shouldOpenQuickNote(
                action = launcherAction,
                categories = setOf(launcherCategory),
                vaultPath = "C:/Vault",
                hasStorageAccess = false
            )
        )
    }

    @Test
    fun editorHomeModeKeepsLauncherOnFullEditorPage() {
        assertFalse(
            QuickLaunchPolicy.shouldOpenQuickNote(
                action = launcherAction,
                categories = setOf(launcherCategory),
                vaultPath = "C:/Vault",
                hasStorageAccess = true,
                homeEntryMode = HomeEntryMode.EDITOR.key,
            )
        )
    }

    @Test
    fun configuredLauncherWithFullscreenModeOpensFullscreenEditor() {
        assertTrue(
            QuickLaunchPolicy.shouldOpenFullScreen(
                action = launcherAction,
                categories = setOf(launcherCategory),
                vaultPath = "C:/Vault",
                hasStorageAccess = true,
                homeEntryMode = HomeEntryMode.FULLSCREEN.key,
            )
        )
        assertFalse(
            QuickLaunchPolicy.shouldOpenFullScreen(
                action = launcherAction,
                categories = setOf(launcherCategory),
                vaultPath = "C:/Vault",
                hasStorageAccess = true,
                homeEntryMode = HomeEntryMode.EDITOR.key,
            )
        )
    }

    @Test
    fun defaultLauncherModeIsOverlay() {
        assertTrue(
            QuickLaunchPolicy.shouldOpenQuickNote(
                action = launcherAction,
                categories = setOf(launcherCategory),
                vaultPath = "C:/Vault",
                hasStorageAccess = true,
            )
        )
    }

    @Test
    fun systemSidebarSupportUsesOverlayOnlyWhenPermissionIsGranted() {
        assertTrue(QuickLaunchPolicy.shouldUseSystemOverlay(true, true))
        assertFalse(QuickLaunchPolicy.shouldUseSystemOverlay(true, false))
        assertFalse(QuickLaunchPolicy.shouldUseSystemOverlay(false, true))
    }
}
