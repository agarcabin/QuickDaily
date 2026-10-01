package com.quickdaily

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.StringRes
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
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
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
            titleRes = R.string.qd_permission_internet_title,
            descriptionRes = R.string.qd_permission_internet_description,
            kind = PermissionKind.SYSTEM,
            androidPermission = Manifest.permission.INTERNET,
        ),
        PermissionSpec(
            id = "network_state",
            titleRes = R.string.qd_permission_network_state_title,
            descriptionRes = R.string.qd_permission_network_state_description,
            kind = PermissionKind.SYSTEM,
            androidPermission = Manifest.permission.ACCESS_NETWORK_STATE,
        ),
        PermissionSpec(
            id = "camera",
            titleRes = R.string.qd_permission_camera_title,
            descriptionRes = R.string.qd_permission_camera_description,
            kind = PermissionKind.RUNTIME,
            androidPermission = Manifest.permission.CAMERA,
        ),
        PermissionSpec(
            id = "record_audio",
            titleRes = R.string.qd_permission_record_audio_title,
            descriptionRes = R.string.qd_permission_record_audio_description,
            kind = PermissionKind.RUNTIME,
            androidPermission = Manifest.permission.RECORD_AUDIO,
        ),
        PermissionSpec(
            id = OVERLAY_ID,
            titleRes = R.string.qd_permission_overlay_title,
            descriptionRes = R.string.qd_permission_overlay_description,
            kind = PermissionKind.OVERLAY,
            androidPermission = Manifest.permission.SYSTEM_ALERT_WINDOW,
        ),
        PermissionSpec(
            id = "foreground_service",
            titleRes = R.string.qd_permission_foreground_service_title,
            descriptionRes = R.string.qd_permission_foreground_service_description,
            kind = PermissionKind.SYSTEM,
            androidPermission = Manifest.permission.FOREGROUND_SERVICE,
        ),
        PermissionSpec(
            id = "foreground_service_special_use",
            titleRes = R.string.qd_permission_foreground_special_use_title,
            descriptionRes = R.string.qd_permission_foreground_special_use_description,
            kind = PermissionKind.SYSTEM,
            androidPermission = "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        ),
        PermissionSpec(
            id = "post_notifications",
            titleRes = R.string.qd_permission_notifications_title,
            descriptionRes = R.string.qd_permission_notifications_description,
            kind = PermissionKind.RUNTIME,
            androidPermission = Manifest.permission.POST_NOTIFICATIONS,
            minSdk = Build.VERSION_CODES.TIRAMISU,
        ),
        PermissionSpec(
            id = "read_external_storage",
            titleRes = R.string.qd_permission_read_storage_title,
            descriptionRes = R.string.qd_permission_read_storage_description,
            kind = PermissionKind.RUNTIME,
            androidPermission = Manifest.permission.READ_EXTERNAL_STORAGE,
            maxSdk = Build.VERSION_CODES.S_V2,
        ),
        PermissionSpec(
            id = "write_external_storage",
            titleRes = R.string.qd_permission_write_storage_title,
            descriptionRes = R.string.qd_permission_write_storage_description,
            kind = PermissionKind.RUNTIME,
            androidPermission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
            maxSdk = Build.VERSION_CODES.Q,
        ),
        PermissionSpec(
            id = MANAGE_FILES_ID,
            titleRes = R.string.qd_permission_manage_files_title,
            descriptionRes = R.string.qd_permission_manage_files_description,
            kind = PermissionKind.MANAGE_FILES,
            androidPermission = Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            minSdk = Build.VERSION_CODES.R,
        ),
        PermissionSpec(
            id = INSTALL_SHORTCUT_ID,
            titleRes = R.string.qd_permission_install_shortcut_title,
            descriptionRes = R.string.qd_permission_install_shortcut_description,
            kind = PermissionKind.SYSTEM,
            androidPermission = "com.android.launcher.permission.INSTALL_SHORTCUT",
        ),
        PermissionSpec(
            id = "uninstall_shortcut",
            titleRes = R.string.qd_permission_uninstall_shortcut_title,
            descriptionRes = R.string.qd_permission_uninstall_shortcut_description,
            kind = PermissionKind.SYSTEM,
            androidPermission = "com.android.launcher.permission.UNINSTALL_SHORTCUT",
        ),
        PermissionSpec(
            id = "quick_settings_tile",
            titleRes = R.string.qd_permission_quick_settings_title,
            descriptionRes = R.string.qd_permission_quick_settings_description,
            kind = PermissionKind.SYSTEM,
            androidPermission = "android.permission.BIND_QUICK_SETTINGS_TILE",
        ),
        PermissionSpec(
            id = "remote_views",
            titleRes = R.string.qd_permission_remote_views_title,
            descriptionRes = R.string.qd_permission_remote_views_description,
            kind = PermissionKind.SYSTEM,
            androidPermission = "android.permission.BIND_REMOTEVIEWS",
        ),
        PermissionSpec(
            id = ACCESSIBILITY_ID,
            titleRes = R.string.qd_permission_accessibility_title,
            descriptionRes = R.string.qd_permission_accessibility_description,
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
