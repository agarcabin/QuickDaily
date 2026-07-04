package com.quickdaily

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class QuickTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onTileAdded() {
        super.onTileAdded()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        if (QuickAccessibilityService.isRunning()) {
            // 无障碍服务已开启：模拟返回键收起通知面板，延迟后启动悬浮窗
            val collapsed = QuickAccessibilityService.collapseStatusBar {
                launchNoteEditor()
            }
            if (!collapsed) {
                // 兜底：直接启动
                launchNoteEditor()
            }
        } else if (QuickAccessibilityService.isEnabled(this)) {
            // 系统已启用但服务尚未连接，等待连接后重试
            handler.postDelayed({
                if (QuickAccessibilityService.collapseStatusBar { launchNoteEditor() }) {
                    // 成功
                } else {
                    launchNoteEditor()
                }
            }, 500L)
        } else {
            // 无障碍服务未开启：提示用户并跳转设置页
            Toast.makeText(
                this,
                "请先开启 QuickDaily 无障碍服务，磁贴才能正常收起通知面板",
                Toast.LENGTH_LONG
            ).show()

            // 跳转无障碍设置
            try {
                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivityAndCollapse(intent)
            } catch (_: Exception) {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun launchNoteEditor() {
        val intent = Intent(this, NoteEditActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }
}
