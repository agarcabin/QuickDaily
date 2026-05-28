package com.quickdaily

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.quickdaily.ui.EditorScreen
import com.quickdaily.ui.SettingsScreen
import com.quickdaily.ui.theme.QuickDailyTheme

class MainActivity : ComponentActivity() {

    private lateinit var appState: AppState
    var externalLaunching = false  // SAF/权限等外部 Activity 启动中

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 从系统设置返回后刷新状态
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appState = ViewModelProvider(this)[AppState::class.java]
        val firstLaunch = appState.config.value.vaultPath.isBlank()

        setContent {
            QuickDailyTheme {
                val navigator = remember { Navigator(firstLaunch) }

                when (navigator.screen) {
                    Screen.EDITOR -> EditorScreen(
                        appState = appState,
                        onSettingsClick = { navigator.screen = Screen.SETTINGS }
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        appState = appState,
                        onBack = { navigator.screen = Screen.EDITOR },
                        onExternalLaunch = { externalLaunching = true }
                    )
                }
            }
        }
        // 权限检查延迟到 UI 首帧之后，不阻塞冷启动
        window.decorView.post { checkPermissions() }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestManageStorage()
                return
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 1)
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), 0
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        externalLaunching = false
        if (::appState.isInitialized) {
            appState.loadToday()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::appState.isInitialized) {
            appState.saveNow()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // SAF 选择器/权限对话框等外部 Activity 启动时不自杀
        if (externalLaunching) return
        if (::appState.isInitialized) appState.saveNow()
        finishAffinity()
    }

    private fun requestManageStorage() {
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName")
        )
        manageStorageLauncher.launch(intent)
    }
}

// ── Simple Navigator ──────────────────────────────────────

enum class Screen { EDITOR, SETTINGS }

class Navigator(firstLaunch: Boolean) {
    var screen by androidx.compose.runtime.mutableStateOf(
        if (firstLaunch) Screen.SETTINGS else Screen.EDITOR
    )
}
