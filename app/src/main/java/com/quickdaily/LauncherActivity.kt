package com.quickdaily

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

/**
 * Transparent dispatcher used only by the launcher icon.
 * Keeping the normal MainActivity theme out of this path prevents a white
 * starting window from flashing before the transparent quick-note editor.
 */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BetaLogger.init(this)
        BetaLogger.log("FloatingNote/Launch", "launcher onCreate action=${intent.action} categories=${intent.categories}")

        if (shouldOpenQuickNote()) {
            val systemSidebarSupport = FloatingNoteEntryPolicy.isSystemSidebarSupportEnabled(this)
            if (!systemSidebarSupport) {
                BetaLogger.log("FloatingNote/Launch", "launcher legacy activity path")
                FloatingNoteEntryPolicy.launchLegacyEditor(this, FloatingNoteSource.DESKTOP_LAUNCHER)
                finish()
                overridePendingTransition(0, 0)
                return
            }

            val overlayAllowed = Settings.canDrawOverlays(this)
            BetaLogger.log("FloatingNote/Permission", "overlay_allowed=$overlayAllowed")
            val request = FloatingNoteRequest(
                source = FloatingNoteSource.DESKTOP_LAUNCHER,
                returnToHomeAfterClose = false
            )
            FloatingNoteTiming.begin(request.requestId, request.source)
            val overlayStarted = QuickLaunchPolicy.shouldUseSystemOverlay(systemSidebarSupport, overlayAllowed) &&
                FloatingNoteControllerProvider.forContext(this).showOrFocus(request)
            if (overlayStarted) {
                BetaLogger.log("FloatingNote/Launch", "launcher overlay requested")
            } else {
                openHomeForOverlayPermission()
            }
        } else if (shouldOpenFullScreen()) {
            BetaLogger.log("FloatingNote/Launch", "launcher route=fullscreen")
            val baseTitle = FloatingNoteTargetStore.titleFor(this, null)
            val title = if (baseTitle.endsWith("速录")) baseTitle else "$baseTitle 速录"
            startActivity(
                NoteEditActivity.fullScreenIntent(
                    context = this,
                    source = FloatingNoteSource.DESKTOP_LAUNCHER,
                    targetRelativePath = null,
                    title = title,
                )
            )
        } else {
            BetaLogger.log("FloatingNote/Launch", "launcher route=editor_fallback")
            // First launch or unavailable storage keeps the existing full-home fallback.
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            })
        }

        finish()
        overridePendingTransition(0, 0)
    }

    private fun openHomeForOverlayPermission() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_REQUEST_FLOATING_PERMISSION, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }

    private fun shouldOpenFullScreen(): Boolean {
        val prefs = getSharedPreferences("QuickDaily", 0)
        val vaultPath = prefs.getString("vault_path", "").orEmpty()
        return QuickLaunchPolicy.shouldOpenFullScreen(
            action = intent.action,
            categories = intent.categories,
            vaultPath = vaultPath,
            hasStorageAccess = hasStorageAccess(),
            homeEntryMode = prefs.getString("home_entry_mode", HomeEntryMode.EDITOR.key)
                ?: HomeEntryMode.EDITOR.key,
        )
    }

    private fun shouldOpenQuickNote(): Boolean {
        val vaultPath = getSharedPreferences("QuickDaily", 0)
            .getString("vault_path", "")
            .orEmpty()
        val result = QuickLaunchPolicy.shouldOpenQuickNote(
            action = intent.action,
            categories = intent.categories,
            vaultPath = vaultPath,
            hasStorageAccess = hasStorageAccess(),
            homeEntryMode = getSharedPreferences("QuickDaily", 0)
                .getString("home_entry_mode", HomeEntryMode.EDITOR.key)
                ?: HomeEntryMode.EDITOR.key,
        )
        BetaLogger.log(
            "FloatingNote/Launch",
            "route result=$result vault_configured=${vaultPath.isNotBlank()} storage=${hasStorageAccess()}"
        )
        return result
    }

    private fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
