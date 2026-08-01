package com.quickdaily

import android.content.Intent

/**
 * Pure launch routing rules shared by the launcher dispatcher and unit tests.
 * Explicit app entries such as sharing and widgets must continue opening their
 * existing destinations.
 */
internal object QuickLaunchPolicy {
    fun shouldOpenQuickNote(
        action: String?,
        categories: Set<String>?,
        vaultPath: String,
        hasStorageAccess: Boolean,
        homeEntryMode: String = HomeEntryMode.OVERLAY.key,
    ): Boolean {
        return action == Intent.ACTION_MAIN &&
            categories?.contains(Intent.CATEGORY_LAUNCHER) == true &&
            vaultPath.isNotBlank() &&
            hasStorageAccess &&
            HomeEntryMode.fromKey(homeEntryMode) == HomeEntryMode.OVERLAY
    }

    fun shouldUseSystemOverlay(
        systemSidebarSupport: Boolean,
        overlayAllowed: Boolean
    ): Boolean = systemSidebarSupport && overlayAllowed
}
