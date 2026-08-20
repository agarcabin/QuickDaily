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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.quickdaily.ui.EditorScreen
import com.quickdaily.ui.OnboardingScreen
import com.quickdaily.ui.SettingsScreen
import com.quickdaily.ui.theme.QuickDailyTheme
import com.quickdaily.ui.theme.rememberQuickDailyMotionPolicy
import com.quickdaily.util.ImageUtil
import com.quickdaily.util.DiaryAppendUtil
import com.quickdaily.BetaLogger
import com.quickdaily.TaskWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        OnboardingStore.initialize(this)
        if (intent.hasExtra(EXTRA_EDITOR_RELATIVE_PATH)) {
            appState.loadEditorTarget(intent.getStringExtra(EXTRA_EDITOR_RELATIVE_PATH))
        }
        val firstLaunch = appState.config.value.vaultPath.isBlank()
        val showOnboarding = OnboardingStore.shouldShow(this)
        awaitingFloatingPermission = intent.getBooleanExtra(EXTRA_REQUEST_FLOATING_PERMISSION, false)

        // 标准桌面/侧边栏入口默认进入速记。首次安装、仓库未配置或存储不可用时，
        // 保留完整首页作为降级入口，避免速记打开后无法保存内容。
        if (shouldOpenQuickNote(intent) && launchQuickNoteFromLauncher()) {
            return
        }

        // 处理分享意图（冷启动时走这里）
        if (showOnboarding && (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE)) {
            android.widget.Toast.makeText(this, "完成仓库与权限设置后请重新分享", android.widget.Toast.LENGTH_LONG).show()
        } else {
            handleShareIntent(intent)
        }

        setContent {
            QuickDailyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navigator = remember { Navigator(showOnboarding, firstLaunch) }
                    val motionPolicy = rememberQuickDailyMotionPolicy()
                    val reducedMotion = motionPolicy.reducedMotion
                    val firstLaunchEntrance = remember(firstLaunch) {
                        MutableTransitionState(!firstLaunch).apply { targetState = true }
                    }
                    AnimatedVisibility(
                        modifier = Modifier.fillMaxSize(),
                        visibleState = firstLaunchEntrance,
                        enter = if (reducedMotion) EnterTransition.None else {
                            slideInHorizontally(
                                animationSpec = motionPolicy.spatialSpec(),
                                initialOffsetX = { it },
                            ) + fadeIn(animationSpec = motionPolicy.effectSpec())
                        },
                        label = "settingsInitialEntrance",
                    ) {
                        AnimatedContent(
                            modifier = Modifier.fillMaxSize(),
                        targetState = navigator.screen,
                        contentKey = { it },
                        transitionSpec = {
                            if (reducedMotion) {
                                ContentTransform(EnterTransition.None, ExitTransition.None)
                            } else if (targetState == Screen.SETTINGS) {
                                (slideIntoContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                    animationSpec = motionPolicy.spatialSpec(),
                                ) + fadeIn(animationSpec = motionPolicy.effectSpec())) togetherWith
                                    (slideOutOfContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                        animationSpec = motionPolicy.spatialSpec(),
                                    ) + fadeOut(animationSpec = motionPolicy.effectSpec()))
                            } else {
                                (slideIntoContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                    animationSpec = motionPolicy.spatialSpec(),
                                ) + fadeIn(animationSpec = motionPolicy.effectSpec())) togetherWith
                                    (slideOutOfContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                        animationSpec = motionPolicy.spatialSpec(),
                                    ) + fadeOut(animationSpec = motionPolicy.effectSpec()))
                            }.using(SizeTransform(clip = false))
                        },
                        label = "settingsNavigation",
                        ) { screen ->
                            when (screen) {
                                Screen.ONBOARDING -> OnboardingScreen(
                                    appState = appState,
                                    onFinished = {
                                        navigator.screen = Screen.EDITOR
                                    },
                                    onExternalLaunch = { externalLaunching = true },
                                )
                                Screen.EDITOR -> EditorScreen(
                                    appState = appState,
                                    onSettingsClick = { navigator.screen = Screen.SETTINGS },
                                    onExternalLaunch = { externalLaunching = true }
                                )
                                Screen.SETTINGS -> SettingsScreen(
                                    appState = appState,
                                    onBack = { navigator.screen = Screen.EDITOR },
                                    onExternalLaunch = { externalLaunching = true },
                                    onRestartOnboarding = {
                                        SponsorReadState.resetAll(this@MainActivity)
                                        OnboardingStore.restart(this@MainActivity)
                                        navigator.screen = Screen.ONBOARDING
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        // 权限检查延迟到 UI 首帧之后，不阻塞冷启动
        window.decorView.post {
            if (!showOnboarding) {
                checkPermissions()
                maybeShowFloatingPermissionPrompt()
            }
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
        if (OnboardingStore.shouldShow(this) && (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE)) {
            android.widget.Toast.makeText(this, "完成仓库与权限设置后请重新分享", android.widget.Toast.LENGTH_LONG).show()
        } else {
            handleShareIntent(intent)
        }
    }

    private fun handleShareIntent(intent: Intent) {
        val payload = SharedPayloadParser.parse(this, intent) ?: return
        when {
            payload.documents.isNotEmpty() -> saveSharedDocumentsToDiary(payload.text, payload.documents)
            payload.images.isNotEmpty() -> {
                payload.text?.let(::saveSharedTextToDiary)
                saveSharedImagesToDiary(payload.images)
            }
            payload.text != null -> saveSharedTextToDiary(payload.text)
        }
    }

    private fun saveSharedDocumentsToDiary(text: String?, uris: List<Uri>) {
        val prefs = getSharedPreferences("QuickDaily", 0)
        val vaultPath = prefs.getString("vault_path", "").orEmpty()
        if (vaultPath.isBlank() || !hasStorageAccess()) {
            android.widget.Toast.makeText(this, "完成仓库与权限设置后请重新分享", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                SharedDocumentImporter.import(
                    context = this@MainActivity,
                    uris = uris,
                    vaultPath = vaultPath,
                    storagePath = prefs.getString("image_storage_path", "").orEmpty(),
                )
            }
            if (result.links.isEmpty()) {
                android.widget.Toast.makeText(this@MainActivity, "文件保存失败，日记未修改", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            appendSharedBlockToDiary(text, result.links)
            android.widget.Toast.makeText(
                this@MainActivity,
                "已保存 ${result.links.size}/${result.total} 个文件",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun appendSharedBlockToDiary(text: String?, links: List<String>) {
        val prefs = getSharedPreferences("QuickDaily", 0)
        val vaultPath = prefs.getString("vault_path", "").orEmpty()
        val diaryFolder = prefs.getString("diary_folder", "Daily").orEmpty()
        val dateFormat = prefs.getString("date_format", "YYYY-MM-DD").orEmpty()
        val timestampFormat = prefs.getString("timestamp_format", "list_time").orEmpty()
        val anchor = prefs.getString("anchor_text", "").orEmpty().trim()
        val addAnchorIfMissing = prefs.getBoolean("add_anchor_if_missing", false)
        val timestampOrder = prefs.getString("timestamp_order", "below").orEmpty()
        val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/${com.quickdaily.util.DateUtil.todayStr(dateFormat)}.md"
        val lines = buildList {
            text?.takeIf(String::isNotBlank)?.let { value ->
                add(when (timestampFormat) {
                    "none" -> value
                    "time_only" -> "${com.quickdaily.util.DateUtil.nowTimeStr()} $value"
                    "time_only_seconds" -> "${com.quickdaily.util.DateUtil.nowTimeSecondsStr()} $value"
                    "list" -> "- $value"
                    "ordered" -> "1. $value"
                    "list_time" -> "- ${com.quickdaily.util.DateUtil.nowTimeStr()} $value"
                    "list_time_seconds" -> "- ${com.quickdaily.util.DateUtil.nowTimeSecondsStr()} $value"
                    "date_time" -> "${com.quickdaily.util.DateUtil.nowDateTimeChineseStr()} $value"
                    "list_date_time" -> "- ${com.quickdaily.util.DateUtil.nowDateTimeChineseStr()} $value"
                    else -> value
                })
            }
            addAll(links)
        }
        val mutationGuard = com.quickdaily.util.FileUtil.acquirePathMutation(path)
        try {
            var existing = com.quickdaily.util.FileUtil.read(path)
            if (existing.isBlank()) {
                val template = prefs.getString("template_path", "").orEmpty()
                if (template.isNotBlank()) {
                    val templatePath = if (template.startsWith('/')) template else "${vaultPath.trimEnd('/')}/$template"
                    existing = com.quickdaily.util.FileUtil.readOrNull(templatePath).orEmpty()
                }
            }
            val parsed = com.quickdaily.util.ContentUtil.parseFrontmatter(existing)
            var body = if (parsed.hasFrontmatter) parsed.body else existing
            if (anchor.isNotEmpty() && !body.contains(anchor) && addAnchorIfMissing) {
                body = if (body.isEmpty() || body.endsWith('\n')) "$body$anchor\n" else "$body\n$anchor\n"
            }
            val newBody = if (timestampOrder == "below") {
                DiaryAppendUtil.appendAtAnchorSectionEnd(body, anchor, lines)
            } else if (anchor.isNotEmpty() && body.contains(anchor)) {
                val index = body.indexOf(anchor) + anchor.length
                body.substring(0, index) + "\n" + lines.joinToString("\n") + body.substring(index)
            } else if (body.isEmpty()) {
                lines.joinToString("\n") + "\n"
            } else {
                body.trimEnd() + "\n" + lines.joinToString("\n") + "\n"
            }
            com.quickdaily.util.FileUtil.write(
                path,
                if (parsed.hasFrontmatter) com.quickdaily.util.ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, newBody) else newBody,
            )
        } finally {
            mutationGuard.close()
        }
        WidgetRefreshHelper.refreshAll(this)
        if (::appState.isInitialized) appState.reloadIfNewerOnDisk()
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

        val mutationGuard = com.quickdaily.util.FileUtil.acquirePathMutation(path)
        try {
        val line = when (timestampFormat) {
            "none" -> text
            "time_only" -> "${com.quickdaily.util.DateUtil.nowTimeStr()} $text"
            "time_only_seconds" -> "${com.quickdaily.util.DateUtil.nowTimeSecondsStr()} $text"
            "list" -> "- $text"
            "ordered" -> "1. $text"
            "list_time" -> "- ${com.quickdaily.util.DateUtil.nowTimeStr()} $text"
            "list_time_seconds" -> "- ${com.quickdaily.util.DateUtil.nowTimeSecondsStr()} $text"
            "date_time" -> "${com.quickdaily.util.DateUtil.nowDateTimeChineseStr()} $text"
            "list_date_time" -> "- ${com.quickdaily.util.DateUtil.nowDateTimeChineseStr()} $text"
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
        } finally {
            mutationGuard.close()
        }
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
        val mutationGuard = com.quickdaily.util.FileUtil.acquirePathMutation(path)
        try {
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
        } finally {
            mutationGuard.close()
        }
    }

    private fun shouldOpenQuickNote(intent: Intent?): Boolean {
        return QuickLaunchPolicy.shouldOpenQuickNote(
            action = intent?.action,
            categories = intent?.categories,
            vaultPath = appState.config.value.vaultPath,
            hasStorageAccess = hasStorageAccess(),
            homeEntryMode = appState.config.value.homeEntryMode,
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
                FloatingNoteEntryPolicy.launchLegacyEditor(this, FloatingNoteSource.DESKTOP_LAUNCHER)
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
                        source = FloatingNoteSource.DESKTOP_LAUNCHER,
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
    }

    override fun onStop() {
        super.onStop()
        if (EditorLifecycleRefreshPolicy.shouldRefreshOn(Lifecycle.Event.ON_STOP) && ::appState.isInitialized) {
            BetaLogger.log("Lifecycle", "onStop editor_refresh_wait_for_save")
            appState.saveNow {
                BetaLogger.log("Lifecycle", "onStop editor_refresh_after_save")
                WidgetRefreshHelper.refreshAll(applicationContext)
            }
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

enum class Screen { ONBOARDING, EDITOR, SETTINGS }

class Navigator(showOnboarding: Boolean, firstLaunch: Boolean) {
    var screen by androidx.compose.runtime.mutableStateOf(
        when {
            showOnboarding -> Screen.ONBOARDING
            firstLaunch -> Screen.SETTINGS
            else -> Screen.EDITOR
        }
    )
}
