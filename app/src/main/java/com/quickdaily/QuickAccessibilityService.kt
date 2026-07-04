package com.quickdaily

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * 速记辅助无障碍服务。
 *
 * 唯一用途：TileService 点击后调用 performGlobalAction(GLOBAL_ACTION_BACK)
 * 模拟返回键，强制收起国产 ROM（MIUI/HyperOS 等）不会自动收起的通知面板。
 *
 * 不监听任何界面内容，canRetrieveWindowContent=false。
 */
class QuickAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: QuickAccessibilityService? = null

        private val handler = Handler(Looper.getMainLooper())

        /** 无障碍服务实例是否已连接（进程内实时状态） */
        fun isRunning(): Boolean = instance != null

        /**
         * 检查本应用的无障碍服务是否在系统设置中被启用。
         * 使用 Settings.Secure 字符串匹配，兼容所有 Android 版本和国产 ROM。
         */
        fun isEnabled(context: Context): Boolean {
            // 方法 1：检查 Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            // 这个值是 "pkg/svc1:pkg/svc2" 格式的字符串
            try {
                val expected = "${context.packageName}/${QuickAccessibilityService::class.java.name}"
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false

                if (!TextUtils.isEmpty(enabledServices)) {
                    val colonSplitter = TextUtils.SimpleStringSplitter(':')
                    colonSplitter.setString(enabledServices)
                    while (colonSplitter.hasNext()) {
                        val component = colonSplitter.next()
                        if (component.equals(expected, ignoreCase = true)) {
                            return true
                        }
                    }
                }
            } catch (_: Exception) {
            }

            // 方法 2：通过 AccessibilityManager 查询（备用）
            try {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                        as? AccessibilityManager ?: return false
                val enabled = am.getEnabledAccessibilityServiceList(
                    android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
                )
                return enabled.any {
                    it.resolveInfo?.serviceInfo?.packageName == context.packageName
                }
            } catch (_: Exception) {
                return false
            }
        }

        /**
         * 综合判断服务是否可用：已连接 OR 系统已启用。
         * 即使静态 instance 为 null（进程刚重启），只要系统已启用，服务很快会重连。
         */
        fun isAvailable(context: Context): Boolean {
            return isRunning() || isEnabled(context)
        }

        /**
         * 收起通知面板：模拟返回键。
         * 通知面板打开时，返回键会将其收起（Android 标准行为）。
         *
         * @param onCollapsed 收起后的回调（延迟 300ms 确保动画结束）
         * @return true=已发送收起指令，false=服务未连接
         */
        fun collapseStatusBar(onCollapsed: (() -> Unit)? = null): Boolean {
            val svc = instance ?: return false
            return try {
                val ok = svc.performGlobalAction(GLOBAL_ACTION_BACK)
                if (ok && onCollapsed != null) {
                    handler.postDelayed({ onCollapsed() }, 300L)
                }
                ok
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不处理任何事件，仅用 performGlobalAction
    }

    override fun onInterrupt() {
        // 无操作
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
}
