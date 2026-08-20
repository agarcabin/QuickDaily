package com.quickdaily.ui

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quickdaily.AppState
import com.quickdaily.DiaryConfig
import com.quickdaily.OnboardingPolicy
import com.quickdaily.OnboardingStore
import com.quickdaily.PermissionKind
import com.quickdaily.PermissionPolicy
import com.quickdaily.PermissionSpec
import com.quickdaily.PermissionStatus
import com.quickdaily.QuickDailyReadWidget
import com.quickdaily.QuickNoteWidget
import com.quickdaily.ShortcutPinResultReceiver
import com.quickdaily.TaskWidget
import com.quickdaily.WidgetIconCatalog
import com.quickdaily.util.UriUtil
import com.quickdaily.ui.theme.rememberQuickDailyMotionPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun OnboardingScreen(
    appState: AppState,
    onFinished: () -> Unit,
    onExternalLaunch: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val config by appState.config.collectAsStateWithLifecycle()
    val initialPage = remember { OnboardingPolicy.clampPage(OnboardingStore.page(context)) }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { OnboardingPolicy.PAGE_COUNT })
    val page = pagerState.currentPage
    var refreshKey by remember { mutableIntStateOf(0) }
    var detectionMessage by rememberSaveable { mutableStateOf("") }
    var skipDialogOpen by rememberSaveable { mutableStateOf(false) }
    val allFilesAccessGranted = remember(context, refreshKey) {
        onboardingStoragePermissionGranted(context)
    }
    val canAdvance = OnboardingPolicy.canAdvance(
        page = page,
        vaultConfigured = config.vaultPath.isNotBlank(),
        allFilesAccessGranted = allFilesAccessGranted,
    )
    val motionPolicy = rememberQuickDailyMotionPolicy()

    suspend fun detectObsidian(path: String) {
        if (path.isBlank()) return
        detectionMessage = "正在读取 Obsidian 配置…"
        val daily = appState.loadObsidianConfig(path)
        val obsidianApp = appState.loadObsidianAppConfig(path)
        val current = appState.config.value
        val updated = if (daily != null) {
            current.copy(
                vaultPath = path,
                diaryFolder = daily.diaryFolder,
                dateFormat = daily.dateFormat,
                templatePath = daily.templatePath,
                imageStoragePath = obsidianApp?.attachmentFolderPath
                    ?.let { if (it == "/") "" else it.trimStart('/') }
                    ?: current.imageStoragePath,
                imageLinkFormat = if (obsidianApp?.useMarkdownLinks == true) "described" else current.imageLinkFormat,
            )
        } else {
            current.copy(vaultPath = path)
        }
        appState.saveConfig(updated)
        detectionMessage = if (daily != null) {
            "已读取日记、模板和附件配置"
        } else if (PermissionPolicy.status(
                context,
                PermissionPolicy.all().first { it.id == PermissionPolicy.MANAGE_FILES_ID },
            ) == PermissionStatus.NOT_GRANTED
        ) {
            "仓库已保存；授予文件权限后将自动重试读取配置"
        } else {
            "未检测到日记配置，将使用当前默认值"
        }
    }

    val vaultPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val path = UriUtil.treeUriToPath(uri)
        if (path == null) {
            detectionMessage = "无法识别所选目录，请重新选择 Vault 根目录"
        } else {
            appState.saveConfig(appState.config.value.copy(vaultPath = path))
            scope.launch { detectObsidian(path) }
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshKey++
    }
    val legacyStorageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshKey++
    }

    LaunchedEffect(pagerState.currentPage) {
        OnboardingStore.setPage(context, pagerState.currentPage)
    }

    DisposableEffect(lifecycleOwner, page) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
                val vault = appState.config.value.vaultPath
                if (page == 2 && vault.isNotBlank()) scope.launch { detectObsidian(vault) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun goTo(next: Int) {
        val target = OnboardingPolicy.clampPage(next)
        if (target > page && !canAdvance) return
        scope.launch {
            if (motionPolicy.reducedMotion) pagerState.scrollToPage(target)
            else pagerState.animateScrollToPage(target)
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { skipDialogOpen = true },
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("跳过引导")
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (!canAdvance && page == 1) {
                        Text(
                            "请先选择库目录后继续",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    } else if (!canAdvance && page == 2) {
                        Text(
                            "请先授予所有文件访问权限后继续",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(OnboardingPolicy.PAGE_COUNT) { indicatorPage ->
                            val selectedPage = indicatorPage == page
                            Spacer(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(if (selectedPage) 10.dp else 8.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(
                                        if (selectedPage) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    )
                                    .semantics {
                                        contentDescription = "第 ${indicatorPage + 1} 页，共 ${OnboardingPolicy.PAGE_COUNT} 页"
                                        selected = selectedPage
                                    },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (page > 0) {
                            OutlinedButton(
                                onClick = { goTo(page - 1) },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Text("上一步") }
                        }
                        Button(
                            onClick = {
                                if (page == OnboardingPolicy.PAGE_COUNT - 1) {
                                    OnboardingStore.complete(context)
                                    onFinished()
                                } else {
                                    goTo(page + 1)
                                }
                            },
                            enabled = canAdvance,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text(if (page == OnboardingPolicy.PAGE_COUNT - 1) "开始使用" else "下一步") }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .pointerInput(page, canAdvance) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                totalDrag += dragAmount
                                change.consume()
                            },
                            onDragEnd = {
                                if (abs(totalDrag) < 56f) return@detectHorizontalDragGestures
                                val target = if (totalDrag < 0f) page + 1 else page - 1
                                if (target !in 0 until OnboardingPolicy.PAGE_COUNT) return@detectHorizontalDragGestures
                                if (target > page && !canAdvance) return@detectHorizontalDragGestures
                                goTo(target)
                            },
                            onDragCancel = { totalDrag = 0f },
                        )
                    },
            ) { pagerPage ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (pagerPage) {
                        0 -> WelcomePage()
                        1 -> VaultPage(
                            config = config,
                            message = detectionMessage,
                            onPickVault = { onExternalLaunch(); vaultPicker.launch(null) },
                        )
                        2 -> PermissionPage(
                            context = context,
                            refreshKey = refreshKey,
                            onExternalLaunch = onExternalLaunch,
                            onNotificationRequest = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onLegacyStorageRequest = {
                                legacyStorageLauncher.launch(
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                )
                            },
                        )
                        else -> WidgetPage(context, refreshKey)
                    }
                }
            }
        }
    }
    if (skipDialogOpen) {
        OnboardingSkipConfirmationDialog(
            onDismiss = { skipDialogOpen = false },
            onConfirm = {
                skipDialogOpen = false
                OnboardingStore.skip(context)
                onFinished()
            },
        )
    }
}

private fun onboardingStoragePermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val spec = PermissionPolicy.all().first { it.id == PermissionPolicy.MANAGE_FILES_ID }
        return PermissionPolicy.status(context, spec) == PermissionStatus.GRANTED
    }
    return PermissionPolicy.all()
        .filter { it.id == "read_external_storage" || it.id == "write_external_storage" }
        .filter(PermissionPolicy::isApplicable)
        .all { PermissionPolicy.status(context, it) == PermissionStatus.GRANTED }
}

@Composable
private fun WelcomePage() {
    OnboardingHeader(Icons.Default.Speed, "欢迎使用 QuickDaily", "Obsidian 的外置小部件与速录悬浮窗")
    FeatureCard("本地优先", "内容直接写入 Obsidian Vault，不需要任何联网权限，安全性有保障。")
    FeatureCard("速度优先", "冷启动最快约 300ms，保存后立即结束进程，最大程度保证速度和轻量化。")
    FeatureCard(
        "录入优先",
        "小部件、下拉磁贴和系统侧边启动器都可以直接拉起录入悬浮窗；文字、文档、图片等文件也可以直接分享至 QD 保存。",
    )
}

@Composable
private fun VaultPage(config: DiaryConfig, message: String, onPickVault: () -> Unit) {
    OnboardingHeader(Icons.Default.FolderOpen, "连接 Obsidian 仓库", "选择 Vault 根目录即可开始")
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("当前仓库", style = MaterialTheme.typography.titleMedium)
            Text(
                config.vaultPath.ifBlank { "尚未选择" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onPickVault, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (config.vaultPath.isBlank()) "选择仓库" else "重新选择")
            }
            if (message.isNotBlank()) {
                Text(
                    message,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    Text(
        "QuickDaily 会尝试读取 Obsidian 配置，读取不到时仍可使用默认值。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PermissionPage(
    context: Context,
    refreshKey: Int,
    onExternalLaunch: () -> Unit,
    onNotificationRequest: () -> Unit,
    onLegacyStorageRequest: () -> Unit,
) {
    OnboardingHeader(Icons.Default.Security, "授予权限", "完成核心权限后继续，其他权限可稍后开启")
    val ids = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(PermissionPolicy.MANAGE_FILES_ID)
        } else {
            add("read_external_storage")
            add("write_external_storage")
        }
        add(PermissionPolicy.OVERLAY_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add("post_notifications")
        add(PermissionPolicy.ACCESSIBILITY_ID)
    }
    ids.mapNotNull { id -> PermissionPolicy.all().firstOrNull { it.id == id } }
        .filter(PermissionPolicy::isApplicable)
        .forEach { spec ->
            val status = remember(spec.id, refreshKey) { PermissionPolicy.status(context, spec) }
            val importance = when (spec.id) {
                PermissionPolicy.MANAGE_FILES_ID, "read_external_storage", "write_external_storage" -> "核心"
                PermissionPolicy.ACCESSIBILITY_ID -> "可选"
                else -> "推荐"
            }
            PermissionCard(spec, status, importance) {
                when (spec.kind) {
                    PermissionKind.RUNTIME -> onNotificationRequest()
                    PermissionKind.MANAGE_FILES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        onExternalLaunch()
                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        })
                    } else onLegacyStorageRequest()
                    PermissionKind.OVERLAY -> {
                        onExternalLaunch()
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        })
                    }
                    PermissionKind.ACCESSIBILITY -> {
                        onExternalLaunch()
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    PermissionKind.SYSTEM -> Unit
                }
            }
        }
}

@Composable
private fun PermissionCard(
    spec: PermissionSpec,
    status: PermissionStatus,
    importance: String,
    onRequest: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("${spec.title} · $importance") },
            supportingContent = {
                Column {
                    Text(spec.description)
                    Text(
                        if (status == PermissionStatus.GRANTED) "已授权" else "未授权",
                        color = if (status == PermissionStatus.GRANTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            },
            trailingContent = {
                TextButton(
                    onClick = onRequest,
                    enabled = status == PermissionStatus.NOT_GRANTED,
                    modifier = Modifier.height(48.dp),
                ) { Text(if (status == PermissionStatus.GRANTED) "已完成" else "去授权") }
            },
        )
    }
}

@Composable
private fun WidgetPage(context: Context, refreshKey: Int) {
    OnboardingHeader(Icons.Default.Widgets, "添加小部件", "把速记入口放到桌面，随时打开 QuickDaily")
    WidgetPinCard(
        context = context,
        provider = QuickNoteWidget::class.java,
        title = "快速录入小部件",
        description = "在桌面快速打开速记",
        requestCode = 105,
        icon = painterResource(WidgetIconCatalog.quickEntry),
        refreshKey = refreshKey,
    )
    WidgetPinCard(
        context = context,
        provider = TaskWidget::class.java,
        title = "任务小部件",
        description = "在桌面查看、添加和勾选任务",
        requestCode = 103,
        icon = painterResource(WidgetIconCatalog.task),
        refreshKey = refreshKey,
    )
    WidgetPinCard(
        context = context,
        provider = QuickDailyReadWidget::class.java,
        title = "便签小部件",
        description = "在桌面查看今日日记内容",
        requestCode = 101,
        icon = painterResource(WidgetIconCatalog.note),
        refreshKey = refreshKey,
    )
}

@Composable
private fun WidgetPinCard(
    context: Context,
    provider: Class<*>,
    title: String,
    description: String,
    requestCode: Int,
    icon: Painter,
    refreshKey: Int,
) {
    val manager = remember { AppWidgetManager.getInstance(context) }
    val component = remember(provider) { ComponentName(context, provider) }
    val added = remember(refreshKey, component) { manager.getAppWidgetIds(component).isNotEmpty() }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painter = icon, contentDescription = null) },
            headlineContent = { Text(title) },
            supportingContent = { Text(if (added) "$description\n已添加到桌面" else description) },
            trailingContent = {
                TextButton(
                    enabled = !added,
                    onClick = {
                        if (manager.isRequestPinAppWidgetSupported) {
                            val callback = PendingIntent.getBroadcast(
                                context,
                                requestCode,
                                Intent(context, ShortcutPinResultReceiver::class.java)
                                    .setAction(ShortcutPinResultReceiver.ACTION_PIN_SUCCEEDED),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            )
                            manager.requestPinAppWidget(component, null, callback)
                        } else {
                            android.widget.Toast.makeText(context, "请长按桌面，从小部件列表手动添加", android.widget.Toast.LENGTH_LONG).show()
                            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                        }
                    },
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(if (added) Icons.Default.CheckCircle else Icons.Default.Shortcut, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text(if (added) "已添加" else "添加")
                }
            },
        )
    }
}

@Composable
private fun OnboardingHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeatureCard(title: String, body: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
