package com.quickdaily

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * 桌面快捷方式的中转 Activity。
 * 用户点击桌面快捷方式后，立即跳转到悬浮窗速记，自身不可见。
 */
class QuickShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(Intent(this, NoteEditActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        })

        finish()
    }
}
