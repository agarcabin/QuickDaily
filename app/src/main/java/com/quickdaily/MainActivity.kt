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
import androidx.lifecycle.lifecycleScope
import com.quickdaily.ui.EditorScreen
import com.quickdaily.ui.SettingsScreen
import com.quickdaily.ui.theme.QuickDailyTheme
import kotlinx.coroutines.launch

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

        // 处理分享意图（冷启动时走这里）
        handleShareIntent(intent)

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

        // 启动时自动检查更新
        if (appState.config.value.autoCheckUpdate) {
            lifecycleScope.launch {
                val result = com.quickdaily.util.UpdateChecker.checkUpdate(context = this@MainActivity)
                when (result) {
                    is com.quickdaily.util.UpdateResult.UpdateAvailable -> {
                        runOnUiThread {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "发现新版本 ${result.info.version}，请在设置中查看详情",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    com.quickdaily.util.UpdateResult.UpToDate -> {
                        // 静默，不打扰用户
                    }
                    is com.quickdaily.util.UpdateResult.Failed -> {
                        // 静默失败，不打扰用户（仅在用户主动检查时才显示错误）
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * singleTask 启动模式下，app 已在后台运行时再次被分享 Intent 拉起，
     * 系统会复用现有 Activity 实例并走 onNewIntent()，而不会走 onCreate()。
     * 必须在此处处理分享，否则有后台时分享内容会丢失。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        if (Intent.ACTION_SEND == intent.action) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null && sharedText.isNotBlank()) {
                saveSharedTextToDiary(sharedText)
            }
        }
    }

    private fun saveSharedTextToDiary(text: String) {
        val prefs = getSharedPreferences("QuickDaily", 0)
        val vaultPath = prefs.getString("vault_path", "") ?: ""
        if (vaultPath.isBlank()) {
            android.widget.Toast.makeText(this, "请先设置仓库路径", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
        val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
        val addTimestamp = prefs.getBoolean("add_timestamp", true)
        val anchor = (prefs.getString("anchor_text", "") ?: "").trim()
        val d = com.quickdaily.util.DateUtil.todayStr(dateFormat)
        val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/$d.md"
        
        val line = if (addTimestamp) "${com.quickdaily.util.DateUtil.nowTimeStr()} $text" else text
        val existing = com.quickdaily.util.FileUtil.read(path)
        val nc = if (anchor.isNotEmpty() && existing.contains(anchor)) {
            val idx = existing.indexOf(anchor) + anchor.length
            existing.substring(0, idx) + "\n" + line + existing.substring(idx)
        } else if (existing.isEmpty()) "$line\n"
        else if (existing.endsWith("\n")) "$existing$line\n"
        else "$existing\n$line\n"
        
        com.quickdaily.util.FileUtil.write(path, nc)
        com.quickdaily.QuickDailyWidget.updateAllWidgets(this)

        // 刷新编辑器内容，让用户立即看到新加入的分享内容
        if (::appState.isInitialized) {
            appState.reloadIfNewerOnDisk()
        }

        android.widget.Toast.makeText(this, "已保存分享内容到日记", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestManageStorage()
                return
            }
        } else {
            // Android 10 及以下仍需运行时申请存储权限
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
            // 仅当文件比内存新时才重读，避免覆盖用户未保存的编辑
            // （saveNow 异步执行，可能尚未完成；盲目 loadToday 会丢失编辑）
            appState.reloadIfNewerOnDisk()
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
