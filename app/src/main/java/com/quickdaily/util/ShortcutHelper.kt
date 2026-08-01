package com.quickdaily.util

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import com.quickdaily.QuickShortcutActivity
import com.quickdaily.R
import com.quickdaily.ShortcutPinResultReceiver
import java.io.File

object ShortcutHelper {

    private const val SHORTCUT_ID_PREFIX = "quick_note_shortcut"

    /**
     * 请求将快捷方式添加到桌面。
     */
    fun pinShortcutToDesktop(context: Context): Boolean {
        // Modern launchers, including current HyperOS versions, no longer honor
        // INSTALL_SHORTCUT reliably. Use the official pin flow on API 26+ and
        // keep the broadcast only as the compatibility fallback.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return pinShortcutLegacy(context).also { if (it) showRequestSentToast(context) }
        }

        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!shortcutManager.isRequestPinShortcutSupported) {
            return pinShortcutLegacy(context).also { if (it) showRequestSentToast(context) }
        }

        // 清理所有旧的 quick_note 快捷方式（动态 + 固定），避免重复创建失败
       try {
           // 也清理可能残存的动态快捷方式
            val dynamic = shortcutManager.dynamicShortcuts
            val dynamicOldIds = dynamic.filter { it.id.startsWith(SHORTCUT_ID_PREFIX) }.map { it.id }
            if (dynamicOldIds.isNotEmpty()) {
                shortcutManager.removeDynamicShortcuts(dynamicOldIds)
            }
        } catch (_: Exception) {}

        // 使用时间戳生成唯一 ID，确保每次都能创建新快捷方式
        val shortcutId = "${SHORTCUT_ID_PREFIX}_${System.currentTimeMillis()}"

        val shortcut = ShortcutInfo.Builder(context, shortcutId)
            .setShortLabel("速记")
            .setLongLabel("QuickDaily 速记")
            .setIcon(loadShortcutIcon(context))
            .setIntent(Intent(context, QuickShortcutActivity::class.java).apply {
                action = Intent.ACTION_VIEW
            })
            .build()

        return try {
            val callback = android.app.PendingIntent.getBroadcast(
                context,
                (System.currentTimeMillis() and 0x7fffffff).toInt(),
                Intent(context, ShortcutPinResultReceiver::class.java)
                    .setAction(ShortcutPinResultReceiver.ACTION_PIN_SUCCEEDED),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            shortcutManager.requestPinShortcut(shortcut, callback.intentSender)
        } catch (e: Exception) {
            e.printStackTrace()
            pinShortcutLegacy(context).also { if (it) showRequestSentToast(context) }
        }
    }

    private fun showRequestSentToast(context: Context) {
        android.widget.Toast.makeText(context.applicationContext, "已发送创建请求，请在桌面确认", android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 当自定义图片变更时，更新所有已存在的桌面快捷方式图标。
     * 注意：固定到桌面的快捷方式不能直接更新图标，
     * 此方法更新动态快捷方式，并通知用户重新添加固定快捷方式。
     */
    fun updateAllShortcuts(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

        try {
            val newIcon = loadShortcutIcon(context)
            // 更新动态快捷方式
            val dynamic = shortcutManager.dynamicShortcuts.filter {
                it.id.startsWith(SHORTCUT_ID_PREFIX)
            }
            if (dynamic.isNotEmpty()) {
                val updated = dynamic.map { si ->
                    ShortcutInfo.Builder(context, si.id)
                        .setShortLabel(si.shortLabel ?: "速记")
                        .setLongLabel(si.longLabel ?: "QuickDaily 速记")
                        .setIcon(newIcon)
                        .setIntent(si.intent ?: Intent(context, QuickShortcutActivity::class.java))
                        .build()
                }
                shortcutManager.updateShortcuts(updated)
            }
        } catch (_: Exception) {}
    }

    /**
     * Android 7.x 兼容：通过广播创建快捷方式。
     */
    private fun pinShortcutLegacy(context: Context): Boolean {
        return try {
            val addIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_NAME, "QuickDaily 速记")
                putExtra(Intent.EXTRA_SHORTCUT_INTENT,
                    Intent(context, QuickShortcutActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
               putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_shortcut_add))
               putExtra("duplicate", false)
            }
            context.sendBroadcast(addIntent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查是否有创建桌面快捷方式的能力。
     * 用于 UI 层判断是否需要引导用户授权。
     */
    fun canCreateShortcut(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sm = context.getSystemService(ShortcutManager::class.java)
            return sm != null && sm.isRequestPinShortcutSupported
        }
        // 旧版本：有 INSTALL_SHORTCUT 权限声明即可
        return true
    }

    /**
     * 加载快捷方式图标：优先使用用户自定义图片，否则用 App 默认图标。
     */
    private fun loadShortcutIcon(context: Context): Icon {
        val imageFile = com.quickdaily.WidgetImageFileResolver.resolve(context)
        if (imageFile != null) {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            if (bitmap != null) {
                val square = centerCropSquare(bitmap)
                // 自适应图标：内层 108dp，外层 72dp 安全区
                val density = context.resources.displayMetrics.density
                val size = (108 * density).toInt()
                val scaled = Bitmap.createScaledBitmap(square, size, size, true)
                return Icon.createWithAdaptiveBitmap(scaled)
            }
        }
        return Icon.createWithResource(context, R.drawable.ic_shortcut_add)
    }

    private fun centerCropSquare(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val x = (src.width - size) / 2
        val y = (src.height - size) / 2
        return Bitmap.createBitmap(src, x, y, size, size)
    }
}
