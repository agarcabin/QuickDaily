package com.quickdaily

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {
    @Test
    fun requestableOnlyContainsUserGrantablePermissions() {
        val requestable = PermissionPolicy.requestable()

        assertTrue(requestable.isNotEmpty())
        assertTrue(requestable.all { it.kind != PermissionKind.SYSTEM })
        assertTrue(requestable.any { it.id == "camera" })
        assertTrue(requestable.any { it.id == PermissionPolicy.OVERLAY_ID })
        assertFalse(requestable.any { it.id == "internet" })
        assertFalse(requestable.any { it.id == "remote_views" })
        assertFalse(requestable.any { it.id == "quick_settings_tile" })
    }

    @Test
    fun settingsPermissionsIncludeDesktopIconPermission() {
        assertTrue(PermissionPolicy.visibleInSettings().any { it.id == PermissionPolicy.INSTALL_SHORTCUT_ID })
    }
}
