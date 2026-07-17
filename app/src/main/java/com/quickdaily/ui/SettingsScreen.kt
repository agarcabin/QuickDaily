package com.quickdaily.ui
import android.content.Intent
import android.app.Activity
import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.graphics.toArgb
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import java.io.File
import java.io.FileOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.quickdaily.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quickdaily.AppState
import com.quickdaily.BuildConfig
import com.quickdaily.DiaryConfig
import com.quickdaily.QuickNoteWidget
import com.quickdaily.TaskWidget
import com.quickdaily.util.DateUtil
import com.quickdaily.util.ShortcutHelper
import com.quickdaily.util.UriUtil
import kotlinx.coroutines.launch
import com.quickdaily.ui.theme.LocalAppDimensions


import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Storage
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

private data class TimestampOption(val key: String, val label: String)
private val timestampOptions = listOf(
    TimestampOption("none", "无时间戳"),
    TimestampOption("time_only", "仅时间"),
    TimestampOption("time_only_seconds", "时间（含秒）"),
    TimestampOption("list", "无序列表"),
    TimestampOption("ordered", "有序列表"),
    TimestampOption("list_time", "列表+时间"),
    TimestampOption("list_time_seconds", "列表+时间（秒）"),
)

private data class NamingOption(val key: String, val label: String)
private val namingOptions = listOf(
    NamingOption("original", "原名（image.jpg）"),
    NamingOption("timestamp_original", "时间戳+原名（2026-07-17_120820_image.jpg）"),
    NamingOption("custom", "自定义名称"),
)

private val linkOptions = listOf(
    "described" to "Markdown：![image_name](路径)",
    "obsidian_wikilink" to "Obsidian：![[image_name]]",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    appState: AppState = viewModel(),
    onBack: () -> Unit,
    onExternalLaunch: () -> Unit = {}
) {
    val context = LocalContext.current
    val navBarColorS = MaterialTheme.colorScheme.surface.toArgb()
    SideEffect {
        try {
            val window = (context as? Activity)?.window ?: return@SideEffect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            window.navigationBarColor = navBarColorS
        } catch (_: Exception) { }
    }

    val config by appState.config.collectAsState()
    val todayPath by appState.todayPath.collectAsState()

    // ── Local edit state ──
    var vaultPath by remember { mutableStateOf(config.vaultPath) }
    var diaryFolder by remember { mutableStateOf(config.diaryFolder) }
    var dateFormat by remember { mutableStateOf(config.dateFormat) }
    var templatePath by remember { mutableStateOf(config.templatePath) }
    var anchorText by remember { mutableStateOf(config.anchorText) }
    var imageStoragePath by remember { mutableStateOf(config.imageStoragePath) }

    var obsidianDetected by remember { mutableStateOf(false) }
    var obsidianMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var widgetImageUri by remember { mutableStateOf(config.widgetImageUri) }

    // ── Update check state ──
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.quickdaily.util.ReleaseInfo?>(null) }
    var updateStatus by remember { mutableStateOf("") }
    var updateErrors by remember { mutableStateOf<List<com.quickdaily.util.SourceError>>(emptyList()) }
    var isLatest by remember { mutableStateOf(false) }

    // ── Tab state ──
    val tabs = listOf("路径配置", "编辑设置", "小部件", "其他")
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    // ── Picker launchers ──
    val vaultPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
            val path = UriUtil.treeUriToPath(it)
            if (path != null) {
                vaultPath = path
                scope.launch {
                    val obsCfg = appState.loadObsidianConfig(path)
                    val appCfg = appState.loadObsidianAppConfig(path)
                    if (obsCfg != null) {
                        diaryFolder = obsCfg.diaryFolder
                        dateFormat = obsCfg.dateFormat
                        templatePath = obsCfg.templatePath
                        obsidianDetected = true
                        obsidianMsg = "已读取 Obsidian 配置"
                        if (appCfg != null) {
                            imageStoragePath = appCfg.attachmentFolderPath.let { if (it == "/") "" else it.trimStart('/') }
                        }
                        appState.saveConfig(DiaryConfig(
                            vaultPath = path.trim(),
                            diaryFolder = obsCfg.diaryFolder.trim().ifBlank { "Daily" },
                            dateFormat = obsCfg.dateFormat.trim().ifBlank { "YYYY-MM-DD" },
                            templatePath = obsCfg.templatePath.trim(),
                            anchorText = anchorText,
                            timestampFormat = config.timestampFormat,
                            addAnchorIfMissing = config.addAnchorIfMissing,
                            timestampOrder = config.timestampOrder,
                            enterToSave = config.enterToSave,
                            widgetImageUri = config.widgetImageUri,
                            autoCheckUpdate = config.autoCheckUpdate,
                            filterFrontmatter = config.filterFrontmatter,
                            imageStoragePath = imageStoragePath.trim(),
                            imageNamingFormat = config.imageNamingFormat,
                            imageLinkFormat = if (appCfg?.useMarkdownLinks == true) "described" else config.imageLinkFormat,
                            tagAutocomplete = config.tagAutocomplete,
                        ))
                    } else {
                        obsidianDetected = false
                        obsidianMsg = "未找到 .obsidian/daily-notes.json"
                    }
                }
            }
        }
    }

    val imageStoragePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
            val path = UriUtil.treeUriToPath(it)
            if (path != null) {
                imageStoragePath = if (vaultPath.isNotBlank() && path.startsWith(vaultPath)) {
                    path.removePrefix(vaultPath).trimStart('/')
                } else {
                    path
                }
            }
        }
    }

    val templatePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val path = UriUtil.documentUriToPath(it)
            if (path != null) {
                templatePath = if (vaultPath.isNotBlank() && path.startsWith(vaultPath)) {
                    path.removePrefix(vaultPath).trimStart('/')
                } else {
                    path
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { srcUri ->
            try {
                val destFile = File(context.filesDir, "widget_image.jpg")
                val inputStream = context.contentResolver.openInputStream(srcUri)
                val outputStream = FileOutputStream(destFile)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                val savedPath = destFile.absolutePath
                widgetImageUri = savedPath
                appState.saveConfig(config.copy(widgetImageUri = "file://"))
                QuickNoteWidget.updateAllWidgets(context)
                ShortcutHelper.updateAllShortcuts(context)
            } catch (e: Exception) {
                android.util.Log.e("QuickDaily", "保存小部件图片失败: ")
            }
        }
    }

    // ── Helper ──
    fun buildConfig(): DiaryConfig = DiaryConfig(
        vaultPath = vaultPath.trim(),
        diaryFolder = diaryFolder.trim().ifBlank { "Daily" },
        dateFormat = dateFormat.trim().ifBlank { "YYYY-MM-DD" },
        templatePath = templatePath.trim(),
        anchorText = anchorText,
        timestampFormat = config.timestampFormat,
        addAnchorIfMissing = config.addAnchorIfMissing,
        timestampOrder = config.timestampOrder,
        enterToSave = config.enterToSave,
        widgetImageUri = config.widgetImageUri,
        autoCheckUpdate = config.autoCheckUpdate,
        filterFrontmatter = config.filterFrontmatter,
        imageStoragePath = imageStoragePath.trim(),
        imageNamingFormat = config.imageNamingFormat,
        imageLinkFormat = config.imageLinkFormat,
        imageCustomNamingFormat = config.imageCustomNamingFormat,
        tagAutocomplete = config.tagAutocomplete,
        loggingEnabled = config.loggingEnabled
    )

    fun saveFull() {
        appState.saveConfig(buildConfig())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { saveFull(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { saveFull(); onBack() }) {
                        Icon(Icons.Default.Check, "保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> DiaryStorageTab(
                        vaultPath = vaultPath,
                        diaryFolder = diaryFolder,
                        dateFormat = dateFormat,
                        templatePath = templatePath,
                        imageStoragePath = imageStoragePath,
                        todayPath = todayPath,
                        obsidianDetected = obsidianDetected,
                        obsidianMsg = obsidianMsg,
                        onVaultPathChange = { vaultPath = it },
                        onDiaryFolderChange = { diaryFolder = it },
                        onDateFormatChange = { dateFormat = it },
                        onTemplatePathChange = { templatePath = it },
                        onImageStoragePathChange = { imageStoragePath = it },
                        config = config,
                        onConfigChange = { newCfg -> appState.saveConfig(newCfg) },
                        onReadObsidianConfig = {
                            scope.launch {
                                val obsCfg = appState.loadObsidianConfig(vaultPath)
                                val appCfg = appState.loadObsidianAppConfig(vaultPath)
                                if (obsCfg != null) {
                                    diaryFolder = obsCfg.diaryFolder
                                    dateFormat = obsCfg.dateFormat
                                    templatePath = obsCfg.templatePath
                                    obsidianDetected = true
                                    obsidianMsg = "已读取 Obsidian 配置"
                                    if (appCfg != null) {
                                        imageStoragePath = appCfg.attachmentFolderPath.let { if (it == "/") "" else it.trimStart('/') }
                                        appState.saveConfig(config.copy(
                                            imageLinkFormat = if (appCfg.useMarkdownLinks) "described" else "obsidian_wikilink"
                                        ))
                                    }
                                } else {
                                    obsidianDetected = false
                                    obsidianMsg = "未找到 .obsidian/daily-notes.json"
                                }
                            }
                        },
                        onPickVault = { onExternalLaunch(); vaultPicker.launch(null) },
                        onPickTemplate = { onExternalLaunch(); templatePicker.launch(arrayOf("text/markdown", "text/plain", "*/*")) },
                        onPickImageStorage = { onExternalLaunch(); imageStoragePicker.launch(null) },
                        onSave = { saveFull(); onBack() },
                        vaultEnabled = vaultPath.isNotBlank()
                    )
                    1 -> EditorSettingsTab(
                        config = config,
                        anchorText = anchorText,
                        onAnchorTextChange = { anchorText = it },
                        onConfigChange = { newCfg -> appState.saveConfig(newCfg) },
                        onSave = { saveFull() }
                    )
                    2 -> WidgetsTab(
                        widgetImageUri = widgetImageUri,
                        context = context,
                        onPickImage = { onExternalLaunch(); imagePicker.launch("image/*") },
                        onSave = { saveFull() }
                    )
                    3 -> OtherTab(
                        config = config,
                        isCheckingUpdate = isCheckingUpdate,
                        updateInfo = updateInfo,
                        updateStatus = updateStatus,
                        updateErrors = updateErrors,
                        isLatest = isLatest,
                        context = context,
                        onConfigChange = { newCfg -> appState.saveConfig(newCfg) },
                        onCheckUpdate = {
                            isCheckingUpdate = true
                            updateInfo = null
                            updateErrors = emptyList()
                            isLatest = false
                            updateStatus = "正在检查更新..."
                            scope.launch {
                                val result = com.quickdaily.util.UpdateChecker.checkUpdate(
                                    currentVersion = BuildConfig.VERSION_NAME, context = context
                                ) { progress -> updateStatus = progress }
                                when (result) {
                                    is com.quickdaily.util.UpdateResult.UpdateAvailable -> {
                                        updateInfo = result.info; updateStatus = ""
                                    }
                                    com.quickdaily.util.UpdateResult.UpToDate -> {
                                        isLatest = true; updateStatus = ""
                                    }
                                    is com.quickdaily.util.UpdateResult.Failed -> {
                                        updateErrors = result.errors; updateStatus = ""
                                    }
                                    else -> { updateStatus = "" }
                                }
                                isCheckingUpdate = false
                            }
                        }
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
// Tab 1: Diary Storage
// ══════════════════════════════════════════════════════════

@Composable
private fun DiaryStorageTab(
    vaultPath: String,
    diaryFolder: String,
    dateFormat: String,
    templatePath: String,
    imageStoragePath: String,
    todayPath: String,
    obsidianDetected: Boolean,
    obsidianMsg: String,
    onVaultPathChange: (String) -> Unit,
    onDiaryFolderChange: (String) -> Unit,
    onDateFormatChange: (String) -> Unit,
    onTemplatePathChange: (String) -> Unit,
    onImageStoragePathChange: (String) -> Unit,
    config: DiaryConfig,
    onConfigChange: (DiaryConfig) -> Unit,
    onReadObsidianConfig: () -> Unit,
    onPickVault: () -> Unit,
    onPickTemplate: () -> Unit,
    onPickImageStorage: () -> Unit,
    onSave: () -> Unit,
    vaultEnabled: Boolean,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("仓库配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = vaultPath,
                    onValueChange = onVaultPathChange,
                    label = { Text("Obsidian 仓库路径") },
                    placeholder = { Text("/storage/emulated/0/Documents/Vault") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Storage, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        IconButton(onClick = onPickVault) {
                            Icon(Icons.Default.FolderOpen, "选择文件夹")
                        }
                    }
                )

                FilledTonalButton(onClick = onReadObsidianConfig, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("从 Obsidian 读取配置")
                }
                if (obsidianMsg.isNotEmpty()) {
                    Text(obsidianMsg,
                        color = if (obsidianDetected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text("日记文件配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = diaryFolder,
                    onValueChange = onDiaryFolderChange,
                    label = { Text("日记文件夹") },
                    placeholder = { Text("Daily") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = dateFormat,
                    onValueChange = onDateFormatChange,
                    label = { Text("日记文件名格式") },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = templatePath,
                    onValueChange = onTemplatePathChange,
                    label = { Text("日记模板路径") },
                    placeholder = { Text("Templates/daily.md（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Description, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        IconButton(onClick = onPickTemplate) {
                            Icon(Icons.Default.FileOpen, "选择文件")
                        }
                    }
                )
            }
        }


        Text("附件配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownSetting(
                    label = "图片命名格式",
                    selectedKey = config.imageNamingFormat,
                    options = namingOptions.map { it.key to it.label },
                    onSelect = { onConfigChange(config.copy(imageNamingFormat = it)) }
                )
                if (config.imageNamingFormat == "custom") {
                    OutlinedTextField(
                        value = config.imageCustomNamingFormat,
                        onValueChange = { onConfigChange(config.copy(imageCustomNamingFormat = it)) },
                        label = { Text("自定义命名格式") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { onConfigChange(config.copy(imageCustomNamingFormat = "yyyy-MM-dd_HHmmss_{filename}{ext}")) }) {
                                Icon(Icons.Default.Refresh, "重置为默认")
                            }
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "可用占位符（点击可复制）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    val tokens = listOf(
                        "{filename}" to "原文件名（不含扩展名）",
                        "{ext}" to "扩展名（如 .jpg、.mp3）",
                        "yyyy" to "年份（4位）",
                        "MM" to "月份（2位）",
                        "dd" to "日（2位）",
                        "HH" to "小时（24小时制）",
                        "mm" to "分钟",
                        "ss" to "秒钟"
                    )
                    Column {
                        tokens.forEach { (token, desc) ->
                            Text(
                                text = "$token - $desc",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(token, token))
                                            android.widget.Toast.makeText(context, "已复制 $token", android.widget.Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) { }
                                    }
                            )
                        }
                    }
                }
                DropdownSetting(
                    label = "图片链接格式",
                    selectedKey = config.imageLinkFormat,
                    options = linkOptions,
                    onSelect = { onConfigChange(config.copy(imageLinkFormat = it)) }
                )
                OutlinedTextField(
                    value = imageStoragePath,
                    onValueChange = onImageStoragePathChange,
                    label = { Text("附件储存目录") },
                    placeholder = { Text("assets/images（相对仓库路径）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        IconButton(onClick = onPickImageStorage) {
                            Icon(Icons.Default.FolderOpen, "选择文件夹")
                        }
                    }
                )
                val exampleName = when (config.imageNamingFormat) {
                    "original" -> "image.jpg"
                    "timestamp_original" -> com.quickdaily.util.DateUtil.nowTimeStr() + "_image.jpg"
                    "custom" -> { val f = config.imageCustomNamingFormat.ifEmpty { "image.jpg" }; f.replace("{filename}", "image").replace("{ext}", ".jpg") }
                    else -> "image.jpg"
                }
                Text(
                    text = "附件储存路径示例：{vaultPath}/{attachmentDir}/$exampleName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = vaultEnabled) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("保存并返回")
        }
    }
}

// ══════════════════════════════════════════════════════════
// Tab 2: Editor Settings
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSettingsTab(
    config: DiaryConfig,
    anchorText: String,
    onAnchorTextChange: (String) -> Unit,
    onConfigChange: (DiaryConfig) -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("时间戳设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownSetting(
                    label = "时间戳格式",
                    selectedKey = config.timestampFormat,
                    options = timestampOptions.map { it.key to it.label },
                    onSelect = { onConfigChange(config.copy(timestampFormat = it)) }
                )

                Text("时间戳插入顺序", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = config.timestampOrder == "above",
                        onClick = { onConfigChange(config.copy(timestampOrder = "above")) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("锚点上方") }
                    SegmentedButton(
                        selected = config.timestampOrder == "below",
                        onClick = { onConfigChange(config.copy(timestampOrder = "below")) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("锚点下方") }
                }

                OutlinedTextField(
                    value = anchorText,
                    onValueChange = onAnchorTextChange,
                    label = { Text("锚点文本") },
                    placeholder = { Text("## 今日速记") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 1,
                    trailingIcon = {
                        IconButton(onClick = { onAnchorTextChange("## 今日速记") }) {
                            Icon(Icons.Default.Refresh, "重置为默认")
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("无锚点时自动添加", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = config.addAnchorIfMissing, onCheckedChange = {
                        onConfigChange(config.copy(addAnchorIfMissing = it))
                    })
                }

                if (config.addAnchorIfMissing || config.timestampFormat != "none") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "时间戳示例：",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(2.dp))
                    val previewText = remember(config.timestampFormat, config.addAnchorIfMissing, anchorText) {
                        val now = com.quickdaily.util.DateUtil.nowTimeStr()
                        val nowSec = com.quickdaily.util.DateUtil.nowTimeSecondsStr()
                        buildString {
                            if (config.addAnchorIfMissing && anchorText.isNotBlank()) {
                                appendLine(anchorText)
                            }
                            when (config.timestampFormat) {
                                "none" -> append("- 这是一段文本")
                                "time_only" -> append(" 这是一段文本")
                                "time_only_seconds" -> append(" 这是一段文本")
                                "list" -> append("- 这是一段文本")
                                "ordered" -> append("1. 这是一段文本")
                                "list_time" -> append("-  这是一段文本")
                                "list_time_seconds" -> append("-  这是一段文本")
                                else -> append("- 这是一段文本")
                            }
                        }
                    }
                    Text(
                        previewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Text("编辑器设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 0.dp)) {
                ListItem(
                    headlineContent = { Text("回车触发保存") },
                    supportingContent = { Text("在悬浮窗中按回车键即触发保存。开启后悬浮窗无法多行输入。") },
                    trailingContent = {
                        Switch(checked = config.enterToSave, onCheckedChange = {
                            onConfigChange(config.copy(enterToSave = it))
                        })
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("过滤 Frontmatter") },
                    supportingContent = { Text("编辑时隐藏日记文件头部元数据") },
                    trailingContent = {
                        Switch(checked = config.filterFrontmatter, onCheckedChange = {
                            onConfigChange(config.copy(filterFrontmatter = it))
                        })
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("标签自动补全") },
                    supportingContent = { Text("输入#时，自动补全已有标签。开启后会影响启动速度，酌情选择。") },
                    trailingContent = {
                        Switch(checked = config.tagAutocomplete, onCheckedChange = {
                            onConfigChange(config.copy(tagAutocomplete = it))
                        })
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("保存并返回")
        }
    }
}

// ══════════════════════════════════════════════════════════
// Tab 3: Widgets & Shortcuts
// ══════════════════════════════════════════════════════════

@Composable
private fun WidgetsTab(
    widgetImageUri: String,
    context: android.content.Context,
    onPickImage: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("小部件与快捷方式", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("小部件自定义图标", style = MaterialTheme.typography.titleSmall)
                Text("选择一张图片作为快速添加小部件和桌面图标的图标",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = 2.dp
                    ) {
                        if (widgetImageUri.isNotEmpty()) {
                            val bitmap = remember(widgetImageUri) {
                                try {
                                    val path = widgetImageUri.removePrefix("file://")
                                    BitmapFactory.decodeFile(path)
                                } catch (_: Exception) { null }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "当前图标",
                                    modifier = Modifier.fillMaxSize().padding(4.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Widgets, null, Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                                }
                            }
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Widgets, null, Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                            }
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    FilledTonalButton(onClick = onPickImage) {
                        Icon(Icons.Default.AddAPhoto, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (widgetImageUri.isNotEmpty()) "更换图片" else "选择图片")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("添加快捷方式", style = MaterialTheme.typography.titleSmall)

                FilledTonalButton(
                    onClick = { ShortcutHelper.pinShortcutToDesktop(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Shortcut, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("快速添加（桌面图标）")
                }

                FilledTonalButton(
                    onClick = {
                        try {
                            val mgr = android.appwidget.AppWidgetManager.getInstance(context)
                            val comp = android.content.ComponentName(context, QuickNoteWidget::class.java)
                            if (mgr.isRequestPinAppWidgetSupported) {
                                val cb = android.app.PendingIntent.getBroadcast(context, 2, Intent(), android.app.PendingIntent.FLAG_IMMUTABLE)
                                mgr.requestPinAppWidget(comp, null, cb)
                            } else {
                                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                            }
                        } catch (_: Exception) {
                            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Widgets, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("快速添加（小部件）")
                }

                FilledTonalButton(
                    onClick = {
                        try {
                            val mgr = android.appwidget.AppWidgetManager.getInstance(context)
                            val comp = android.content.ComponentName(context, TaskWidget::class.java)
                            if (mgr.isRequestPinAppWidgetSupported) {
                                val cb = android.app.PendingIntent.getBroadcast(context, 3, Intent(), android.app.PendingIntent.FLAG_IMMUTABLE)
                                mgr.requestPinAppWidget(comp, null, cb)
                            } else {
                                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                            }
                        } catch (_: Exception) {
                            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.EditNote, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("今日任务（小部件）")
                }
            }
        }

        Text(
            "自定义图标同时应用于快速添加的小部件和桌面图标。如点击无反应，请给予本APP创建桌面图标的权限。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("保存并返回")
        }
    }
}

// ══════════════════════════════════════════════════════════
// Tab 4: Other (Updates, Accessibility, About)
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OtherTab(
    config: DiaryConfig,
    isCheckingUpdate: Boolean,
    updateInfo: com.quickdaily.util.ReleaseInfo?,
    updateStatus: String,
    updateErrors: List<com.quickdaily.util.SourceError>,
    isLatest: Boolean,
    context: android.content.Context,
    onConfigChange: (DiaryConfig) -> Unit,
    onCheckUpdate: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("更新设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 0.dp)) {
                ListItem(
                    headlineContent = { Text("启动时自动检查更新") },
                    supportingContent = { Text("每次启动应用时自动检测 GitHub 最新版本") },
                    trailingContent = {
                        Switch(checked = config.autoCheckUpdate, onCheckedChange = {
                            onConfigChange(config.copy(autoCheckUpdate = it))
                        })
                    }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCheckUpdate, modifier = Modifier.fillMaxWidth(), enabled = !isCheckingUpdate) {
                    Icon(Icons.Default.Update, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isCheckingUpdate) updateStatus.ifEmpty { "检查中..." } else "检查更新")
                }
                if (isCheckingUpdate && updateStatus.isNotEmpty()) {
                    Text(updateStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (updateInfo != null) {
                    Text("发现新版本：", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(updateInfo.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Button(onClick = { com.quickdaily.util.UpdateChecker.openReleasePage(context, updateInfo.releaseUrl) }, modifier = Modifier.fillMaxWidth()) {
                        Text("前往下载")
                    }
                }
                if (isLatest) {
                    Text("当前已是最新版本（）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (updateErrors.isNotEmpty()) {
                    Text("检查更新失败，已尝试  个镜像源：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    updateErrors.forEach { err ->
                        Text("? ：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        Text("辅助服务", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val accReady = com.quickdaily.QuickAccessibilityService.isAvailable(context)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AccessibilityNew, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("速记辅助服务", style = MaterialTheme.typography.bodyMedium)
                        Text(if (accReady) "已开启" else "未开启", style = MaterialTheme.typography.bodySmall, color = if (accReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                }
                Text("通知面板快捷磁贴点击后，需要此服务模拟返回键收起通知面板。如未开启，点击磁贴后会弹出提示并跳转设置页。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                FilledTonalButton(
                    onClick = { try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) } catch (_: Exception) { } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (accReady) "前往无障碍设置" else "开启速记辅助服务")
                }
            }
        }

        Text("日志", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 0.dp)) {
                ListItem(
                    headlineContent = { Text("记录日志") },
                    supportingContent = { Text("日志保存到 Document/QuickDaily_log_日期.txt") },
                    trailingContent = {
                        Switch(checked = config.loggingEnabled, onCheckedChange = {
                            onConfigChange(config.copy(loggingEnabled = it))
                            com.quickdaily.BetaLogger.configure(context, it, true)
                        })
                    }
                )
            }
        }

        if (config.loggingEnabled) {
            Button(onClick = { com.quickdaily.BetaLogger.shareLog(context) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.BugReport, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("分享 Beta 日志")
            }
        }

        Text("关于", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("QuickDaily ", style = MaterialTheme.typography.titleMedium)

                Text("更新内容：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text("1.5:\\n" +
                    "? 新增 首页/悬浮窗 底部工具栏 \\n" +
                    "? 新增 首页 阅读视图图片显示 \\n" +
                    "? 新增 对 Templater 插件日期格式支持 \\n" +
                    "? 新增 安卓小部件添加页面 预览图 \\n" +
                    "? 调整 快速添加（桌面图标）的默认图标样式 \\n" +
                    "? 修复 悬浮窗 任务切换格式错误 \\n" +
                    "? 修复 悬浮窗 空任务异常触发保存 \\n" +
                    "? 修复 小部件 今日任务刷新异常 \\n\\n" +
                    "1.4:\\n" +
                    "? 新增 Frontmatter 过滤 \\n" +
                    "? 新增 WW 等日期格式支持 \\n" +
                    "? 新增 悬浮窗增加图片录入功能（可批量导入）\\n" +
                    "? 新增 悬浮窗增加任务录入功能（双击切换任务状态） \\n" +
                    "? 新增 《今日任务》桌面小部件 \\n\\n" +
                    "1.3:\\n" +
                    "? 新增 7种时间戳格式设置，可适配Thino/Knomo \\n" +
                    "? 新增 时间戳文本插入顺序\\n" +
                    "? 新增 无锚点时自动添加锚点文本\\n" +
                    "? 修复 清空日记内容后无法重新加载模板\\n" +
                    "? 修复 每日首次录入内容时略过日记模板\\n\\n" +
                    "1.2:\\n" +
                    "? 新增 快速添加（桌面图标）\\n" +
                    "? 修复 磁贴点击后收回状态栏\\n\\n" +
                    "1.1:\\n" +
                    "? 新增 小部件时间戳，回车保存\\n" +
                    "? 新增 小部件自定义图片\\n" +
                    "? 新增 状态栏快捷磁贴\\n" +
                    "? 新增 文本分享至本应用\\n" +
                    "? 新增 检测更新\\n\\n" +
                    "1.0:\\n" +
                    "? 正式发布！APP 更名为 QuickDaily\\n" +
                    "? 开源发布到 GitHub",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

                val coolapkA = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)) { append("酷安 @附近的人") }
                }
                ClickableText(text = coolapkA, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coolapk.com/u/400522"))) })

                val githubA = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)) { append("GitHub @agarcabin") }
                }
                ClickableText(text = githubA, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/agarcabin/QuickDaily"))) })

                val qqA = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)) { append("QQ群：1050092886") }
                }
                ClickableText(text = qqA, onClick = {
                    val qi = Intent(Intent.ACTION_VIEW, Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&uin=1050092886&card_type=group&source=qrcode"))
                    try { context.startActivity(qi) }
                    catch (_: Exception) {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/cgi-bin/qm/qr?from=app&p=android&jump_from=webapi&k=20251120"))) }
                        catch (_: Exception) { android.widget.Toast.makeText(context, "请安装 QQ 或手动搜索群号 1050092886", android.widget.Toast.LENGTH_LONG).show() }
                    }
                })
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("如果您喜欢 QuickDaily，可以扫码支持：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.size(240.dp).combinedClickable(
                        onClick = {},
                        onLongClick = {
                            try {
                                val bm = BitmapFactory.decodeResource(context.resources, R.drawable.qr_donate)
                                val fn = "QuickDaily_donate_.jpg"
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    val vals = ContentValues().apply {
                                        put(MediaStore.Images.Media.DISPLAY_NAME, fn)
                                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                                        put(MediaStore.Images.Media.IS_PENDING, 1)
                                    }
                                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, vals)
                                    uri?.let {
                                        context.contentResolver.openOutputStream(it)?.use { out -> bm.compress(Bitmap.CompressFormat.JPEG, 95, out) }
                                        vals.clear(); vals.put(MediaStore.Images.Media.IS_PENDING, 0)
                                        context.contentResolver.update(it, vals, null, null)
                                    }
                                } else {
                                    @Suppress("DEPRECATION")
                                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES); dir.mkdirs()
                                    val file = File(dir, fn); FileOutputStream(file).use { out -> bm.compress(Bitmap.CompressFormat.JPEG, 95, out) }
                                    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
                                }
                                android.widget.Toast.makeText(context, "已保存至相册，谢谢！", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) { android.widget.Toast.makeText(context, "保存失败: ", android.widget.Toast.LENGTH_SHORT).show() }
                        }
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painter = painterResource(id = R.drawable.qr_donate), contentDescription = "赞赏码", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                Text("长按图片保存到相册", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ══════════════════════════════════════════════════════════
// Shared: ExposedDropdownMenu setting
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(
    label: String,
    selectedKey: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedKey }?.second ?: selectedKey

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (key, display) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = { onSelect(key); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}


