package com.quickdaily

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity

/**
 * 桌面快捷方式的中转 Activity。
 * 用户点击桌面快捷方式后，立即跳转到悬浮窗速记，自身不可见。
 */
class QuickShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val overlayStarted = FloatingNoteEntryPolicy.isSystemSidebarSupportEnabled(this) &&
            Settings.canDrawOverlays(this) &&
            FloatingNoteControllerProvider.forContext(this).showOrFocus(
                FloatingNoteRequest(
                    source = FloatingNoteSource.SIDEBAR,
                    returnToHomeAfterClose = false
                )
            )
        if (!overlayStarted) FloatingNoteEntryPolicy.launchLegacyEditor(this)

        finish()
    }
}
