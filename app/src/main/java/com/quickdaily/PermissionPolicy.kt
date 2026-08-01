package com.quickdaily

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class PermissionKind {
    RUNTIME,
    OVERLAY,
    MANAGE_FILES,
    ACCESSIBILITY,
    SYSTEM,
}

enum class PermissionStatus {
    GRANTED,
    NOT_GRANTED,
    NOT_REQUIRED,
    SYSTEM_MANAGED,
}

data class PermissionSpec(
    val id: String,
    val title: String,
    val description: String,
    val kind: PermissionKind,
    val androidPermission: String? = null,
    val minSdk: Int = 1,
    val maxSdk: Int = Int.MAX_VALUE,
)

/** Central list and state rules for the settings permission page. */
object PermissionPolicy {
    const val OVERLAY_ID = "system_alert_window"
    const val MANAGE_FILES_ID = "manage_external_storage"
    const val ACCESSIBILITY_ID = "quick_accessibility_service"
    const val INSTALL_SHORTCUT_ID = "install_shortcut"

    fun all(): List<PermissionSpec> = listOf(
        PermissionSpec(
            id = "internet",
            title = "网络访问",
            description = "用于检查更新和访问 GitHub API",
            kind = PermissionKind.SYSTEM,
            androidPermission = Manifest.permission.INTERNET,
        ),
        PermissionSpec(
            id = "network_state",
            title = "网络状态",
            description = "用于判断当前网络连接状态",
            kind = PermissionKind.SYSTEM,
            androidPermission = Manifest.permission.ACCESS_NETWORK_STATE,
        ),
        PermissionSpec(
            id = "camera",
            title = "相机",
            description = "用于在编辑器和悬浮窗中拍照",
            kind = PermissionKind.RUNTIME,
            androidPermission = Manifest.permission.CAMERA,
        ),
        PermissionSpec(
            id = "record_audio",
            title = "麦克风",
            description = "用于在编辑器和悬浮窗中录音",
            kind = PermissionKind.RUNTIME,
            androidPermission = Manifest.permission.RECORD_AUDIO,
        ),
        PermissionSpec(
            id = OVERLAY_ID,
            title = "显示悬浮窗",
            description = "用于显示速记悬浮窗",
            kind = PermissionKind.OVERLAY,
            androidPermission = Manifest.permission.SYSTEM_ALERT_WINDOW,
        ),
        PermissionSpec(
            id = "foreground_service",
            title = "前台服务",
            description = "用于保持悬浮窗在后台运行",
            kind = PermissionKind.SYSTEM,
            androidPermission = Manifest.permission.FOREGROUND_SERVICE,
        ),
        PermissionSpec(
            id = "foreground_service_special_use",
            title = "前台服务特殊用途",
            description = "用于 Android 14+ 的悬浮窗前台服务声明",
            kind = PermissionKind.SYSTEM,
            androidPermission = "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        ),
        PermissionSpec(
            id = "post_notifications",
            title = "通知",
            description = "用于保护速记悬浮窗进程不被系统拦截",
            kind = PermissionKind.RUNTIME,
            androidPermission = Manifest.permission.POST_NOTIFICATIONS,
            minSdk = Build.VERSION_CODES.TIRAMISU,
        ),
        PermissionSpec(
            id = "read_external_storage",
            title = "读取存储",
            description = "用于旧版 Android 读取日记和附件",
            kind = PermissionKind.RUNTIME,
            androidPermission = Manifest.permission.READ_EXTERNAL_STORAGE,
            maxSdk = Build.VERSION_CODES.S_V2,
        ),
        PermissionSpec(
            id = "write_external_storage",
            title = "写入存储",
            description = "用于 Android 10 及更早版本保存附件",
            kind = PermissionKind.RUNTIME,
            androidPermission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
            maxSdk = Build.VERSION_CODES.Q,
        ),
        PermissionSpec(
            id = MANAGE_FILES_ID,
            title = "所有文件访问",
            description = "用于md文件的读取与写入",
            kind = PermissionKind.MANAGE_FILES,
            androidPermission = Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            minSdk = Build.VERSION_CODES.R,
        ),
        PermissionSpec(
            id = INSTALL_SHORTCUT_ID,
            title = "创建桌面图标",
            description = "用于在app中快速创建小部件",
            kind = PermissionKind.SYSTEM,
            androidPermission = "com.android.launcher.permission.INSTALL_SHORTCUT",
        ),
        PermissionSpec(
            id = "uninstall_shortcut",
            title = "移除桌面快捷方式",
            description = "用于更新和移除旧版快捷方式",
            kind = PermissionKind.SYSTEM,
            androidPermission = "com.android.launcher.permission.UNINSTALL_SHORTCUT",
        ),
        PermissionSpec(
            id = "quick_settings_tile",
            title = "快捷设置磁贴绑定",
            description = "用于把速记入口注册到系统快捷设置面板",
            kind = PermissionKind.SYSTEM,
            androidPermission = "android.permission.BIND_QUICK_SETTINGS_TILE",
        ),
        PermissionSpec(
            id = "remote_views",
            title = "小部件 RemoteViews",
            description = "用于让系统桌面加载任务和日记小部件内容",
            kind = PermissionKind.SYSTEM,
            androidPermission = "android.permission.BIND_REMOTEVIEWS",
        ),
        PermissionSpec(
            id = ACCESSIBILITY_ID,
            title = "QuickDaily 无障碍服务",
            description = "用于下拉磁贴成功拉起悬浮窗",
            kind = PermissionKind.ACCESSIBILITY,
        ),
    )

    /** Permissions that can be granted directly by the user. */
    fun requestable(): List<PermissionSpec> = all().filter { it.kind != PermissionKind.SYSTEM }

    /** Permissions shown in the settings page, including the legacy shortcut permission. */
    fun visibleInSettings(): List<PermissionSpec> = all().filter {
        it.kind != PermissionKind.SYSTEM || it.id == INSTALL_SHORTCUT_ID
    }

    fun isApplicable(spec: PermissionSpec): Boolean =
        Build.VERSION.SDK_INT in spec.minSdk..spec.maxSdk

    fun status(context: Context, spec: PermissionSpec): PermissionStatus {
        if (!isApplicable(spec)) return PermissionStatus.NOT_REQUIRED
        return when (spec.kind) {
            PermissionKind.SYSTEM -> PermissionStatus.SYSTEM_MANAGED
            PermissionKind.OVERLAY -> if (Settings.canDrawOverlays(context)) {
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.NOT_GRANTED
            }
            PermissionKind.MANAGE_FILES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.NOT_GRANTED
            }
            PermissionKind.ACCESSIBILITY -> if (QuickAccessibilityService.isAvailable(context)) {
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.NOT_GRANTED
            }
            PermissionKind.RUNTIME -> if (
                spec.androidPermission != null &&
                ContextCompat.checkSelfPermission(context, spec.androidPermission) == PackageManager.PERMISSION_GRANTED
            ) {
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.NOT_GRANTED
            }
        }
    }
}
