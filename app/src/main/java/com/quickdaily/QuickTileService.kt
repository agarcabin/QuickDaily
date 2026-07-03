package com.quickdaily

import android.service.quicksettings.TileService
import android.content.Intent

class QuickTileService : TileService() {

    override fun onClick() {
        super.onClick()
        // 启动悬浮窗 Activity
        val intent = Intent(this, NoteEditActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }
}
