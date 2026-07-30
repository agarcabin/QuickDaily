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
import com.quickdaily.util.ImageUtil
import com.quickdaily.util.DiaryAppendUtil
import com.quickdaily.BetaLogger
import com.quickdaily.TaskWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REQUEST_FLOATING_PERMISSION = "request_floating_permission"
        const val EXTRA_EDITOR_RELATIVE_PATH = "editor_relative_path"

        fun editorIntent(context: android.content.Context, targetRelativePath: String?): Intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_EDITOR_RELATIVE_PATH, targetRelativePath.orEmpty())
            }
    }

    private lateinit var appState: AppState
    var externalLaunching = false  // SAF/权限等外部 Activity 启动中
    private var awaitingFloatingPermission = false
    private var floatingPermissionPromptShown = false
    private var floatingPermissionRetried = false

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 从系统设置返回后刷新状态
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BetaLogger.init(this)

        BetaLogger.log("Lifecycle", "onCreate")
        appState = ViewModelProvider(this)[AppState::class.java]
        if (intent.hasExtra(EXTRA_EDITOR_RELATIVE_PATH)) {
            appState.loadEditorTarget(intent.getStringExtra(EXTRA_EDITOR_RELATIVE_PATH))
        }
        val firstLaunch = appState.config.value.vaultPath.isBlank()
        awaitingFloatingPermission = intent.getBooleanExtra(EXTRA_REQUEST_FLOATING_PERMISSION, false)

        // 标准桌面/侧边栏入口默认进入速记。首次安装、仓库未配置或存储不可用时，
        // 保留完整首页作为降级入口，避免速记打开后无法保存内容。
        if (shouldOpenQuickNote(intent) && launchQuickNoteFromLauncher()) {
            return
        }

        // 处理分享意图（冷启动时走这里）
        handleShareIntent(intent)

        setContent {
            QuickDailyTheme {
                val navigator = remember { Navigator(firstLaunch) }

                when (navigator.screen) {
                    Screen.EDITOR -> EditorScreen(
                        appState = appState,
                        onSettingsClick = { navigator.screen = Screen.SETTINGS },
                        onExternalLaunch = { externalLaunching = true }
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
        window.decorView.post {
            checkPermissions()
            maybeShowFloatingPermissionPrompt()
        }

        // 启动时自动检查更新
        if (appState.config.value.autoCheckUpdate) {
            lifecycleScope.launch {
                val result = com.quickdaily.util.UpdateChecker.checkUpdate(currentVersion = BuildConfig.VERSION_NAME, context = this@MainActivity)
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
        BetaLogger.log("Lifecycle", "onNewIntent: ${intent.action}")
        if (intent.hasExtra(EXTRA_EDITOR_RELATIVE_PATH)) {
            appState.loadEditorTarget(intent.getStringExtra(EXTRA_EDITOR_RELATIVE_PATH))
        }
        if (intent.getBooleanExtra(EXTRA_REQUEST_FLOATING_PERMISSION, false)) {
            awaitingFloatingPermission = true
            floatingPermissionPromptShown = false
            floatingPermissionRetried = false
        }
        if (shouldOpenQuickNote(intent) && launchQuickNoteFromLauncher()) {
            return
        }
        handleShareIntent(intent)
    }

        private fun handleShareIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                // 文本分享
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (sharedText != null && sharedText.isNotBlank()) {
                    saveSharedTextToDiary(sharedText)
                    return
                }
                // 图片分享（单张）
                if (intent.type?.startsWith("image/") == true) {
                    val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (imageUri != null) {
                        saveSharedImagesToDiary(listOf(imageUri))
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (intent.type?.startsWith("image/") == true) {
                    val imageUris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    if (imageUris != null && imageUris.isNotEmpty()) {
                        saveSharedImagesToDiary(imageUris)
                    }
                }
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
        val timestampFormat = prefs.getString("timestamp_format", "list_time") ?: "list_time"
        val addAnchorIfMissing = prefs.getBoolean("add_anchor_if_missing", false)
        val timestampOrder = prefs.getString("timestamp_order", "below") ?: "below"
        val anchor = (prefs.getString("anchor_text", "") ?: "").trim()
        val d = com.quickdaily.util.DateUtil.todayStr(dateFormat)
        val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/$d.md"
        
        val line = when (timestampFormat) {
            "none" -> text
            "time_only" -> "${com.quickdaily.util.DateUtil.nowTimeStr()} $text"
            "time_only_seconds" -> "${com.quickdaily.util.DateUtil.nowTimeSecondsStr()} $text"
            "list" -> "- $text"
            "ordered" -> "1. $text"
            "list_time" -> "- ${com.quickdaily.util.DateUtil.nowTimeStr()} $text"
            "list_time_seconds" -> "- ${com.quickdaily.util.DateUtil.nowTimeSecondsStr()} $text"
           else -> text
       }

        var existing = com.quickdaily.util.FileUtil.read(path)

        // parse frontmatter, work on body only
        var parsed = com.quickdaily.util.ContentUtil.parseFrontmatter(existing)
        var body = if (parsed.hasFrontmatter) parsed.body else existing

        // 今日文件不存在或为空时，从模板加载
        if (existing.isEmpty() || (parsed.hasFrontmatter && parsed.body.isBlank())) {
            val tplPathPref = prefs.getString("template_path", "") ?: ""
            if (tplPathPref.isNotBlank()) {
                val tplPath = if (tplPathPref.startsWith("/")) tplPathPref
                else "${vaultPath.trimEnd('/')}/${tplPathPref}"
                val tplContent = com.quickdaily.util.FileUtil.readOrNull(tplPath)
                if (tplContent != null && tplContent.isNotEmpty()) {
                    existing = tplContent
                }
            }
        }
        // Re-parse body after potential template loading
        if (body.isEmpty() && existing.isNotEmpty()) {
            val reParsed = com.quickdaily.util.ContentUtil.parseFrontmatter(existing)
            body = if (reParsed.hasFrontmatter) reParsed.body else existing
        }

        if (anchor.isNotEmpty() && !body.contains(anchor) && addAnchorIfMissing) {
                val newBody = if (body.isNotEmpty() && !body.endsWith("\n")) {
                    body + "\n$anchor\n"
                } else {
                    body + "$anchor\n"
                }
                if (parsed.hasFrontmatter) {
                    existing = com.quickdaily.util.ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, newBody)
                } else {
                    existing = newBody
                }
            }
            val workingContent = if (parsed.hasFrontmatter) body else existing
            val nc = if (anchor.isNotEmpty() && workingContent.contains(anchor) && timestampOrder == "above") {
                val idx = workingContent.indexOf(anchor) + anchor.length
                val newBody = workingContent.substring(0, idx) + "\n" + line + workingContent.substring(idx)
                if (parsed.hasFrontmatter) {
                    com.quickdaily.util.ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, newBody)
                } else {
                    newBody
                }
            } else if (workingContent.isEmpty()) {
                if (parsed.hasFrontmatter) {
                    com.quickdaily.util.ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, "$line\n")
                } else {
                    "$line\n"
                }
            } else if (workingContent.endsWith("\n")) {
                if (parsed.hasFrontmatter) {
                    com.quickdaily.util.ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, workingContent + "$line\n")
                } else {
                    workingContent + "$line\n"
                }
            } else {
                if (parsed.hasFrontmatter) {
                    com.quickdaily.util.ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, workingContent + "\n$line\n")
                } else {
                    workingContent + "\n$line\n"
                }
            }
            com.quickdaily.util.FileUtil.write(path, nc)
        WidgetRefreshHelper.refreshAll(this)

        // 刷新编辑器内容，让用户立即看到新加入的分享内容
        if (::appState.isInitialized) {
            appState.reloadIfNewerOnDisk()
        }

        android.widget.Toast.makeText(this, "已保存分享内容到日记", android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 保存分享的图片到日记。
     * 将图片复制到 vault，然后在日记中插入 Markdown 图片引用。
     */
    private fun saveSharedImagesToDiary(uris: List<Uri>) {
        val prefs = getSharedPreferences("QuickDaily", 0)
        val vaultPath = prefs.getString("vault_path", "") ?: ""
        if (vaultPath.isBlank()) {
            android.widget.Toast.makeText(this, "请先设置仓库路径", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val storagePath = prefs.getString("image_storage_path", "") ?: ""
        val namingFormat = prefs.getString("image_naming_format", "timestamp_ext") ?: "timestamp_ext"
        val linkFormat = prefs.getString("image_link_format", "described") ?: "described"
        val customNamingFormat = prefs.getString("image_custom_naming_format", "") ?: ""
        val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
        val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
        val textAnchor = (prefs.getString("anchor_text", "") ?: "").trim()
        val addAnchorIfMissing = prefs.getBoolean("add_anchor_if_missing", false)
        val timestampOrder = prefs.getString("timestamp_order", "below") ?: "below"
        val d = com.quickdaily.util.DateUtil.todayStr(dateFormat)
        val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/$d.md"

        // 处理图片
        val links = ImageUtil.processImages(this, uris, vaultPath, storagePath, namingFormat, linkFormat, customNamingFormat)
        if (links.isEmpty()) {
            android.widget.Toast.makeText(this, "复制图片失败", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // 将图片引用插入日记（带 frontmatter 保护）
        var existing = com.quickdaily.util.FileUtil.read(path)
        var parsed = com.quickdaily.util.ContentUtil.parseFrontmatter(existing)
        var body = if (parsed.hasFrontmatter) parsed.body else existing

        val anchor = textAnchor
        var workingContent = body
        if (anchor.isNotEmpty() && !workingContent.contains(anchor) && addAnchorIfMissing) {
            workingContent = if (workingContent.isNotEmpty() && !workingContent.endsWith("\n")) {
                workingContent + "\n$anchor\n"
            } else {
                workingContent + "$anchor\n"
            }
        }

        val newBody = if (timestampOrder == "below") {
            DiaryAppendUtil.appendAtAnchorSectionEnd(workingContent, anchor, links)
        } else if (anchor.isNotEmpty() && workingContent.contains(anchor)) {
            val idx = workingContent.indexOf(anchor) + anchor.length
            workingContent.substring(0, idx) + "\n" + links.joinToString("\n") + workingContent.substring(idx)
        } else if (workingContent.isEmpty()) {
            links.joinToString("\n") + "\n"
        } else if (workingContent.endsWith("\n")) {
            "$workingContent${links.joinToString("\n")}\n"
        } else {
            "$workingContent\n${links.joinToString("\n")}\n"
        }

        val saveContent = if (parsed.hasFrontmatter) {
            com.quickdaily.util.ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, newBody)
        } else {
            newBody
        }
        com.quickdaily.util.FileUtil.write(path, saveContent)
        WidgetRefreshHelper.refreshAll(this)

        android.widget.Toast.makeText(this, "已保存 ${links.size} 张图片到日记", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun shouldOpenQuickNote(intent: Intent?): Boolean {
        return QuickLaunchPolicy.shouldOpenQuickNote(
            action = intent?.action,
            categories = intent?.categories,
            vaultPath = appState.config.value.vaultPath,
            hasStorageAccess = hasStorageAccess()
        )
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

    private fun launchQuickNoteFromLauncher(): Boolean {
        return try {
            val systemSidebarSupport = FloatingNoteEntryPolicy.isSystemSidebarSupportEnabled(this)
            if (!systemSidebarSupport) {
                BetaLogger.log("FloatingNote/Launch", "main launcher legacy activity path")
                FloatingNoteEntryPolicy.launchLegacyEditor(this)
                finishAndRemoveTask()
                return true
            }

            val overlayAllowed = Settings.canDrawOverlays(this)
            val request = FloatingNoteRequest(
                source = FloatingNoteSource.DESKTOP_LAUNCHER,
                returnToHomeAfterClose = false
            )
            if (!QuickLaunchPolicy.shouldUseSystemOverlay(systemSidebarSupport, overlayAllowed) ||
                !FloatingNoteControllerProvider.forContext(this).showOrFocus(request)
            ) {
                awaitingFloatingPermission = true
                maybeShowFloatingPermissionPrompt()
                return false
            }
            BetaLogger.log("FloatingNote/Launch", "main launcher overlay requested")
            // 直接从桌面/侧边栏速录后，关闭速录应回到原来的系统界面，
            // 而不是回到一个被动留在后台的首页任务。
            finishAndRemoveTask()
            true
        } catch (e: Exception) {
            BetaLogger.log("Launch", "launcher -> NoteEditActivity failed=${e.javaClass.simpleName}")
            false
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!hasStorageAccess()) {
                requestManageStorage()
                return
            }
        } else {
            // Android 10 及以下仍需运行时申请存储权限
            if (!hasStorageAccess()) {
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
        BetaLogger.log("Lifecycle", "onResume")
        com.quickdaily.QuickDailyReadWidget.scheduleMidnightRefresh(this)
        com.quickdaily.TaskWidget.scheduleMidnightRefresh(this)
        externalLaunching = false
       if (::appState.isInitialized) {
           // 仅当文件比内存新时才重读，避免覆盖用户未保存的编辑
           // （saveNow 异步执行，可能尚未完成；盲目 loadToday 会丢失编辑）
           appState.reloadIfNewerOnDisk()
            // 刷新所有小部件确保最新内容
            WidgetRefreshHelper.refreshAll(this)
       }
        if (awaitingFloatingPermission) {
            if (android.provider.Settings.canDrawOverlays(this) && !floatingPermissionRetried) {
                floatingPermissionRetried = true
                awaitingFloatingPermission = false
                FloatingNoteControllerProvider.forContext(this).showOrFocus(
                    FloatingNoteRequest(
                        source = FloatingNoteSource.HOME_AUTH_FLOW,
                        returnToHomeAfterClose = true
                    )
                )
            } else {
                maybeShowFloatingPermissionPrompt()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        BetaLogger.log("Lifecycle", "onPause")
       if (::appState.isInitialized) {
            WidgetRefreshHelper.refreshAll(this)
       }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // OEM launchers also call this when an in-app Activity (such as the cropper)
        // starts. Ending the task here makes a successful crop look like a crash.
        BetaLogger.log("Lifecycle", "onUserLeaveHint externalLaunching=$externalLaunching; saving without finishing task")
        if (::appState.isInitialized) appState.saveNow()
    }

    private fun requestManageStorage() {
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName")
        )
        manageStorageLauncher.launch(intent)
    }

    private fun maybeShowFloatingPermissionPrompt() {
        if (!awaitingFloatingPermission || floatingPermissionPromptShown) return
        if (!hasStorageAccess()) return
        if (android.provider.Settings.canDrawOverlays(this)) {
            return
        }
        floatingPermissionPromptShown = true
        android.app.AlertDialog.Builder(this)
            .setTitle("需要悬浮窗权限")
            .setMessage("侧边栏速记需要显示在当前应用上方，请允许 QuickDaily 显示悬浮窗。")
            .setPositiveButton("去授权") { _, _ ->
                externalLaunching = true
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ))
            }
            .setNegativeButton("暂不") { _, _ -> awaitingFloatingPermission = false }
            .setOnDismissListener { BetaLogger.log("FloatingNote/Permission", "prompt dismissed") }
            .show()
    }
}

// ── Simple Navigator ──────────────────────────────────────

enum class Screen { EDITOR, SETTINGS }

class Navigator(firstLaunch: Boolean) {
    var screen by androidx.compose.runtime.mutableStateOf(
        if (firstLaunch) Screen.SETTINGS else Screen.EDITOR
    )
}
