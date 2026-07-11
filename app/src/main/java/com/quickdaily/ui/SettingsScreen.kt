package com.quickdaily.ui

import android.content.Intent
import android.content.ContentValues
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
import com.quickdaily.QuickDailyWidget
import com.quickdaily.QuickNoteWidget
import com.quickdaily.TaskWidget
import com.quickdaily.util.DateUtil
import com.quickdaily.util.ShortcutHelper
import com.quickdaily.util.UriUtil
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    appState: AppState = viewModel(),
    onBack: () -> Unit,
    onExternalLaunch: () -> Unit = {}
) {
    var showQRFull by remember { mutableStateOf(false) }
    val qrScope = rememberCoroutineScope()

    val context = LocalContext.current
    val config by appState.config.collectAsState()
    val todayPath by appState.todayPath.collectAsState()

    var vaultPath by remember { mutableStateOf(config.vaultPath) }
    var diaryFolder by remember { mutableStateOf(config.diaryFolder) }
    var dateFormat by remember { mutableStateOf(config.dateFormat) }
    var templatePath by remember { mutableStateOf(config.templatePath) }
    var anchorText by remember { mutableStateOf(config.anchorText) }
    var imageStoragePath by remember { mutableStateOf(config.imageStoragePath) }

    var obsidianDetected by remember { mutableStateOf(false) }
    var obsidianMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // 小部件图片 URI（最终保存到 config 的值）
    var widgetImageUri by remember { mutableStateOf(config.widgetImageUri) }

    // ── SAF 文件夹选择器（vault） ──
    val vaultPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { /* 权限持久化失败不崩溃 */ }
            val path = UriUtil.treeUriToPath(it)
            if (path != null) {
                vaultPath = path
                // 选择仓库后自动触发读取 Obsidian 配置
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
                            anchorText = anchorText.trim(),
                            timestampFormat = config.timestampFormat,
                            addAnchorIfMissing = config.addAnchorIfMissing,
                            timestampOrder = config.timestampOrder,
                            enterToSave = config.enterToSave,
                            widgetImageUri = config.widgetImageUri,
                            autoCheckUpdate = config.autoCheckUpdate,
                        filterFrontmatter = config.filterFrontmatter,
                        imageStoragePath = imageStoragePath.trim(),
                        imageNamingFormat = config.imageNamingFormat,
                        imageLinkFormat = config.imageLinkFormat
                        ))
                    } else {
                        obsidianDetected = false
                        obsidianMsg = "未找到 .obsidian/daily-notes.json"
                    }
                }
            }
        }
    }

    // ── SAF 文件选择器（模板） ──
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

    // ── 图片选择器：选图后复制到私有目录，不做系统裁剪 ──
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { srcUri ->
            try {
                // 复制到 app 私有目录，避免 URI 权限失效
                val destFile = File(context.filesDir, "widget_image.jpg")
                val inputStream = context.contentResolver.openInputStream(srcUri)
                val outputStream = FileOutputStream(destFile)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                // 保存 file:// URI（App 私有目录，RemoteViews 无法直接访问，
                // 所以在 QuickNoteWidget 里用 contentResolver 读取）
                // 改为保存相对路径，让 QuickNoteWidget 从 filesDir 读取
                val savedPath = destFile.absolutePath
                widgetImageUri = savedPath
                // 同时保存到 SharedPreferences（用特殊前缀标识是私有文件）
                appState.saveConfig(config.copy(widgetImageUri = "file://$savedPath"))
                QuickNoteWidget.updateAllWidgets(context)
                // 同时更新所有已存在的桌面快捷方式图标
                ShortcutHelper.updateAllShortcuts(context)
            } catch (e: Exception) {
                android.util.Log.e("QuickDaily", "保存小部件图片失败: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (vaultPath.isNotBlank()) {
                            appState.saveConfig(DiaryConfig(
                                vaultPath = vaultPath.trim(),
                                diaryFolder = diaryFolder.trim().ifBlank { "Daily" },
                                dateFormat = dateFormat.trim().ifBlank { "YYYY-MM-DD" },
                                templatePath = templatePath.trim(),
                                anchorText = anchorText.trim(),
                                timestampFormat = config.timestampFormat,
                                addAnchorIfMissing = config.addAnchorIfMissing,
                                timestampOrder = config.timestampOrder,
                                enterToSave = config.enterToSave,
                                widgetImageUri = config.widgetImageUri,
                                autoCheckUpdate = config.autoCheckUpdate,
                        filterFrontmatter = config.filterFrontmatter,
                        imageStoragePath = imageStoragePath.trim(),
                        imageNamingFormat = config.imageNamingFormat,
                        imageLinkFormat = config.imageLinkFormat
                            ))
                        }
                        onBack()
                    }) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {

            // ══════════════════════════════════════
            // 1. 日记存储
            // ══════════════════════════════════════
            Text("日记存储", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            // Vault 路径
            Text("Obsidian 仓库路径", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = vaultPath,
                    onValueChange = { vaultPath = it },
                    label = { Text("/storage/emulated/0/Documents/Vault") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { onExternalLaunch(); vaultPicker.launch(null) }, modifier = Modifier.height(56.dp)) {
                    Icon(Icons.Default.FolderOpen, "选择文件夹", Modifier.size(18.dp))
                }
            }

            // 检测 Obsidian 配置（下方无分界线）
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
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
                        }
                    } else {
                        obsidianDetected = false
                        obsidianMsg = "未找到 .obsidian/daily-notes.json"
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("从 Obsidian 读取配置")
            }
            if (obsidianMsg.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(obsidianMsg,
                    color = if (obsidianDetected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            // 日记文件夹
            Spacer(Modifier.height(12.dp))
            Text("日记文件夹", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(value = diaryFolder, onValueChange = { diaryFolder = it },
                label = { Text("Daily") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Spacer(Modifier.height(12.dp))

            // 日期格式
            Text("日记文件名格式", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(value = dateFormat, onValueChange = { dateFormat = it },
                label = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Spacer(Modifier.height(12.dp))

            // 模板路径
            Text("日记模板路径", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = templatePath, onValueChange = { templatePath = it },
                    label = { Text("Templates/daily.md（可选）") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { onExternalLaunch(); templatePicker.launch(arrayOf("text/markdown", "text/plain", "*/*")) },
                    modifier = Modifier.height(56.dp)) {
                    Icon(Icons.Default.FileOpen, "选择文件", Modifier.size(18.dp))
                }
            }

            // 今天的日记路径（在日记存储最下方）
            Spacer(Modifier.height(12.dp))
            Text("今天的日记路径", style = MaterialTheme.typography.titleSmall)
            Text(todayPath.ifEmpty { "(输入 vault 路径后自动计算)" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp))

            // ── 保存并返回（在日记存储最下方）──
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                appState.saveConfig(DiaryConfig(
                    vaultPath = vaultPath.trim(),
                    diaryFolder = diaryFolder.trim().ifBlank { "Daily" },
                    dateFormat = dateFormat.trim().ifBlank { "YYYY-MM-DD" },
                    templatePath = templatePath.trim(),
                    anchorText = anchorText.trim(),
                    timestampFormat = config.timestampFormat,
                    addAnchorIfMissing = config.addAnchorIfMissing,
                    timestampOrder = config.timestampOrder,
                    enterToSave = config.enterToSave,
                    widgetImageUri = config.widgetImageUri,
                    autoCheckUpdate = config.autoCheckUpdate,
                        filterFrontmatter = config.filterFrontmatter,
                        imageStoragePath = imageStoragePath.trim(),
                        imageNamingFormat = config.imageNamingFormat,
                        imageLinkFormat = config.imageLinkFormat
                ))
                onBack()
            }, modifier = Modifier.fillMaxWidth(), enabled = vaultPath.isNotBlank()) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("保存并返回")
            }

            // ══════════════════════════════════════
            // 分界线 + 2. 速记设置
            // ══════════════════════════════════════
            HorizontalDivider(Modifier.padding(top = 20.dp, bottom = 12.dp))

            Text("速记设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            // 速记锚点文本
            Text("速记锚点文本", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(value = anchorText, onValueChange = { anchorText = it },
                label = { Text("例：## 今日速记") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
           Text("速记内容插入到该文本之后；为空则添加到末尾",
               style = MaterialTheme.typography.bodySmall,
               color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
               modifier = Modifier.padding(top = 2.dp))

           Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("无锚点文本时先添加锚点文本", style = MaterialTheme.typography.bodyMedium)
                    Text("开启后若日记中找不到锚点文本，先自动添加锚点文本再插入速记内容",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(checked = config.addAnchorIfMissing,
                    onCheckedChange = { appState.saveConfig(config.copy(addAnchorIfMissing = it)) })
            }

            Spacer(Modifier.height(16.dp))

            // 时间戳格式
            Text("时间戳格式", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            var timestampFormatExpanded by remember { mutableStateOf(false) }
            val timestampFormatLabels = mapOf(
                "none" to "无格式（仅速记内容）",
                "time_only" to "仅时间（HH:mm）",
                "time_only_seconds" to "仅时间（HH:mm:ss）",
                "list" to "无序列表",
                "ordered" to "有序列表",
                "list_time" to "无序列表+时间（HH:mm）",
                "list_time_seconds" to "无序列表+时间（HH:mm:ss）适配Thino/Knomo"
            )
            ExposedDropdownMenuBox(
                expanded = timestampFormatExpanded,
                onExpandedChange = { timestampFormatExpanded = !timestampFormatExpanded }
            ) {
                OutlinedTextField(
                    value = timestampFormatLabels[config.timestampFormat] ?: "无序列表+时间（HH:mm）",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timestampFormatExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = timestampFormatExpanded,
                    onDismissRequest = { timestampFormatExpanded = false }
                ) {
                    timestampFormatLabels.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                appState.saveConfig(config.copy(timestampFormat = value))
                                timestampFormatExpanded = false
                            }
                        )
                    }
                }
            }
            val previewText = when (config.timestampFormat) {
                "none" -> "速记内容"
                "time_only" -> "${DateUtil.nowTimeStr()} 速记内容"
                "time_only_seconds" -> "${DateUtil.nowTimeSecondsStr()} 速记内容"
                "list" -> "- 速记内容"
                "ordered" -> "1. 速记内容"
                "list_time" -> "- ${DateUtil.nowTimeStr()} 速记内容"
                "list_time_seconds" -> "- ${DateUtil.nowTimeSecondsStr()} 速记内容"
                else -> "速记内容"
            }
            Text(previewText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp))

            Spacer(Modifier.height(12.dp))


            Spacer(Modifier.height(16.dp))

            // 时间戳添加顺序
            Text("时间戳添加顺序", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            var timestampOrderExpanded by remember { mutableStateOf(false) }
            val timestampOrderLabels = mapOf("above" to "新添加在上方", "below" to "新添加在下方")
            ExposedDropdownMenuBox(
                expanded = timestampOrderExpanded,
                onExpandedChange = { timestampOrderExpanded = !timestampOrderExpanded }
            ) {
                OutlinedTextField(
                    value = timestampOrderLabels[config.timestampOrder] ?: "新添加在下方",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timestampOrderExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = timestampOrderExpanded,
                    onDismissRequest = { timestampOrderExpanded = false }
                ) {
                    timestampOrderLabels.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                appState.saveConfig(config.copy(timestampOrder = value))
                                timestampOrderExpanded = false
                            }
                        )
                    }
                }
            }
            // 回车键直接保存
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("回车键直接保存", style = MaterialTheme.typography.bodyMedium)
                    Text("开启后回车键直接保存速记，无法换行（单行输入）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(checked = config.enterToSave, onCheckedChange = { appState.saveConfig(config.copy(enterToSave = it)) })
            }

            // Frontmatter filtering
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("\u8fc7\u6ee4 Frontmatter \u663e\u793a", style = MaterialTheme.typography.bodyMedium)
                    Text("\u5f00\u542f\u540e\u7f16\u8f91\u5668\u548c\u684c\u9762\u4fbf\u7b7e\u5c06\u9690\u85cf YAML \u62ac\u5934\u6570\u636e\uff0c\u4fdd\u5b58\u65f6\u81ea\u52a8\u4fdd\u7559",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(checked = config.filterFrontmatter, onCheckedChange = { appState.saveConfig(config.copy(filterFrontmatter = it)) })
            }

            // ══════════════════════════════════════
// 图片设置
            // ================================================
            HorizontalDivider(Modifier.padding(top = 20.dp, bottom = 12.dp))

            Text("图片设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            Text("图片存储目录", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = imageStoragePath,
                    onValueChange = { imageStoragePath = it },
                    label = { Text("assets/") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { onExternalLaunch(); vaultPicker.launch(null) }, modifier = Modifier.height(56.dp)) {
                    Icon(Icons.Default.FolderOpen, "选择文件夹", Modifier.size(18.dp))
                }
            }
            Text("为空时图片保存到日记同目录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp))

            Spacer(Modifier.height(12.dp))

            Text("图片文件名规则", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            var namingFormatExpanded by remember { mutableStateOf(false) }
            val namingFormatLabels = mapOf(
                "original" to "原始文件名",
                "timestamp_original" to "时间戳+原始文件名",
                "custom" to "时间戳+自定义扩展名"
            )
            ExposedDropdownMenuBox(
                expanded = namingFormatExpanded,
                onExpandedChange = { namingFormatExpanded = !namingFormatExpanded }
            ) {
                OutlinedTextField(
                    value = namingFormatLabels[config.imageNamingFormat] ?: "时间戳+原始文件名",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = namingFormatExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = namingFormatExpanded,
                    onDismissRequest = { namingFormatExpanded = false }
                ) {
                    namingFormatLabels.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                appState.saveConfig(config.copy(imageNamingFormat = value))
                                namingFormatExpanded = false
                            }
                        )
                    }
                }
            }

            // Custom naming pattern (only when "custom" is selected)
            if (config.imageNamingFormat == "custom") {
                Spacer(Modifier.height(8.dp))
                Text("自定义命名格式", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = config.imageCustomNamingFormat.ifEmpty { "yyyyMMdd_HHmmssSSS_{filename}{ext}" },
                    onValueChange = { appState.saveConfig(config.copy(imageCustomNamingFormat = it)) },
                    label = { Text("yyyyMMdd_HHmmssSSS_{filename}{ext}") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("可用占位符: yyyy MM dd HH mm ss SSS = 时间戳, {filename} = 原始文件名, {ext} = 文件扩展名",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 2.dp))
            }

            // Preview
            Spacer(Modifier.height(8.dp))
            Text("名称预览", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            val previewFileName = remember(config.imageNamingFormat, config.imageCustomNamingFormat) {
                val now = java.time.LocalDateTime.now()
                val ts = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                when (config.imageNamingFormat) {
                    "original" -> "原始文件名.jpg"
                    "timestamp_original" -> "${ts}_picture.jpg"
                    "custom" -> {
                        val customPtn = config.imageCustomNamingFormat.ifEmpty { "yyyyMMdd_HHmmssSSS_{filename}{ext}" }
                        // Simple preview: don't use ImageUtil to avoid complexity,
                        // just show the pattern with placeholders replaced
                        customPtn
                            .replace("yyyy", now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy")))
                            .replace("MM", now.format(java.time.format.DateTimeFormatter.ofPattern("MM")))
                            .replace("dd", now.format(java.time.format.DateTimeFormatter.ofPattern("dd")))
                            .replace("HH", now.format(java.time.format.DateTimeFormatter.ofPattern("HH")))
                            .replace("mm", now.format(java.time.format.DateTimeFormatter.ofPattern("mm")))
                            .replace("ss", now.format(java.time.format.DateTimeFormatter.ofPattern("ss")))
                            .replace("SSS", now.format(java.time.format.DateTimeFormatter.ofPattern("SSS")))
                            .replace("{filename}", "picture")
                            .replace("{ext}", ".jpg")
                    }
                    else -> "${ts}_picture.jpg"
                }
            }
            if (config.imageNamingFormat == "custom") {
                Text(previewFileName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            } else {
                Text(previewFileName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(16.dp))

            // Link format
            Text("图片 Markdown 链接格式", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            var linkFormatExpanded by remember { mutableStateOf(false) }
            val linkFormatLabels = mapOf(
                "bare" to "![](/path/to/image.jpg)",
                "described" to "![filename](/path/to/image.jpg)"
            )
            ExposedDropdownMenuBox(
                expanded = linkFormatExpanded,
                onExpandedChange = { linkFormatExpanded = !linkFormatExpanded }
            ) {
                OutlinedTextField(
                    value = linkFormatLabels[config.imageLinkFormat] ?: "![filename](/path/to/image.jpg)",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = linkFormatExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = linkFormatExpanded,
                    onDismissRequest = { linkFormatExpanded = false }
                ) {
                    linkFormatLabels.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                appState.saveConfig(config.copy(imageLinkFormat = value))
                                linkFormatExpanded = false
                            }
                        )
                    }
                }
            }

            // Link preview
            Spacer(Modifier.height(8.dp))
            val previewLink = remember(config.imageLinkFormat) {
                when (config.imageLinkFormat) {
                    "bare" -> "![](assets/20260708_143021_picture.jpg)"
                    "described" -> "![20260708_143021_picture](assets/20260708_143021_picture.jpg)"
                    else -> "![](assets/20260708_143021_picture.jpg)"
                }
            }
            Text("链接预览", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(previewLink, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)

            // ================================================
            // 3. 小部件设置            // 分界线 + 3. 小部件设置
            // ══════════════════════════════════════
            HorizontalDivider(Modifier.padding(top = 20.dp, bottom = 12.dp))

            Text("小部件设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            // 自定义图片（快速添加小部件）
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("快速添加 · 自定义图片", style = MaterialTheme.typography.bodyMedium)
                Row {
                    FilledTonalButton(onClick = {
                        onExternalLaunch()
                        imagePicker.launch("image/*")
                    }) {
                        Text("选择图片")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = {
                        // 删除私有目录里的图片文件
                        val f = java.io.File(context.filesDir, "widget_image.jpg")
                        if (f.exists()) f.delete()
                        widgetImageUri = ""
                        appState.saveConfig(config.copy(widgetImageUri = ""))
                        QuickNoteWidget.updateAllWidgets(context)
                    }) {
                        Text("重置")
                    }
                }
            }
            val hasCustomImage = config.widgetImageUri.isNotEmpty()
            Text(
                if (hasCustomImage) "已设置自定义图片（自动裁剪为正方形，带圆角）" else "未设置自定义图片，使用默认 + 号",
                style = MaterialTheme.typography.bodySmall,
                color = if (hasCustomImage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(12.dp))

            // 快捷添加到桌面（四个按钮竖排）
            Text("快捷添加到桌面", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 1. 桌面便签（小部件）
                FilledTonalButton(
                    onClick = {
                        try {
                            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
                            val component = android.content.ComponentName(context, QuickDailyWidget::class.java)
                            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                val successCallback = android.app.PendingIntent.getBroadcast(
                                    context, 0, Intent(), android.app.PendingIntent.FLAG_IMMUTABLE
                                )
                                appWidgetManager.requestPinAppWidget(component, null, successCallback)
                            } else {
                                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                            }
                        } catch (_: Exception) {
                            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("桌面便签（小部件）")
                }

                // 2. 快速添加（小部件）
                FilledTonalButton(
                    onClick = {
                        try {
                            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
                            val component = android.content.ComponentName(context, QuickNoteWidget::class.java)
                            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                val successCallback = android.app.PendingIntent.getBroadcast(
                                    context, 1, Intent(), android.app.PendingIntent.FLAG_IMMUTABLE
                                )
                                appWidgetManager.requestPinAppWidget(component, null, successCallback)
                            } else {
                                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                            }
                        } catch (_: Exception) {
                            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("快速添加（小部件）")
                }

                // 3. 快速添加（桌面图标）
                FilledTonalButton(
                    onClick = {
                        if (!ShortcutHelper.canCreateShortcut(context)) {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = Uri.parse("package:${context.packageName}")
                                context.startActivity(intent)
                                android.widget.Toast.makeText(context,
                                    "请在应用信息页中找到「桌面快捷方式」权限并开启，然后返回重试",
                                    android.widget.Toast.LENGTH_LONG).show()
                            } catch (_: Exception) {}
                        } else {
                            val ok = ShortcutHelper.pinShortcutToDesktop(context)
                            if (!ok) {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    intent.data = Uri.parse("package:${context.packageName}")
                                    context.startActivity(intent)
                                    android.widget.Toast.makeText(context,
                                        "添加失败，请在应用信息页中检查「桌面快捷方式」权限",
                                        android.widget.Toast.LENGTH_LONG).show()
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context,
                                        "添加失败，请手动从桌面添加",
                                        android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("快速添加（桌面图标）")
                }
                // 4. 今日任务（小部件）
                FilledTonalButton(
                    onClick = {
                        try {
                            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
                            val component = android.content.ComponentName(context, TaskWidget::class.java)
                            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                val successCallback = android.app.PendingIntent.getBroadcast(
                                    context, 3, Intent(), android.app.PendingIntent.FLAG_IMMUTABLE
                                )
                                appWidgetManager.requestPinAppWidget(component, null, successCallback)
                            } else {
                                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                            }
                        } catch (_: Exception) {
                            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("今日任务（小部件）")
                }
            }
            Text(
                "自定义图标同时应用于快速添加的小部件和桌面图标。如点击无反应，请给予本APP创建桌面图标的权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp)
            )

            // ══════════════════════════════════════
            // 分界线 + 4. 更新设置
            // ══════════════════════════════════════
            HorizontalDivider(Modifier.padding(top = 20.dp, bottom = 12.dp))

            Text("更新设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启动时自动检查更新", style = MaterialTheme.typography.bodyMedium)
                    Text("开启后每次启动应用时自动检测 GitHub 最新版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Switch(checked = config.autoCheckUpdate, onCheckedChange = { appState.saveConfig(config.copy(autoCheckUpdate = it)) })
            }

            Spacer(Modifier.height(12.dp))

            // 检查更新按钮（移到更新设置里）
            var isCheckingUpdate by remember { mutableStateOf(false) }
            var updateInfo by remember { mutableStateOf<com.quickdaily.util.ReleaseInfo?>(null) }
            var updateStatus by remember { mutableStateOf("") }  // 实时进度/状态
            var updateErrors by remember { mutableStateOf<List<com.quickdaily.util.SourceError>>(emptyList()) }
            var isLatest by remember { mutableStateOf(false) }

            Button(onClick = {
                isCheckingUpdate = true
                updateInfo = null
                updateErrors = emptyList()
                isLatest = false
                updateStatus = "正在检查更新..."
                scope.launch {
                    val result = com.quickdaily.util.UpdateChecker.checkUpdate(currentVersion = BuildConfig.VERSION_NAME, context = context) { progress ->
                        updateStatus = progress
                    }
                    when (result) {
                        is com.quickdaily.util.UpdateResult.UpdateAvailable -> {
                            updateInfo = result.info
                            updateStatus = ""
                        }
                        com.quickdaily.util.UpdateResult.UpToDate -> {
                            isLatest = true
                            updateStatus = ""
                        }
                        is com.quickdaily.util.UpdateResult.Failed -> {
                            updateErrors = result.errors
                            updateStatus = ""
                        }
                        else -> {
                            updateStatus = ""
                        }
                    }
                    isCheckingUpdate = false
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !isCheckingUpdate) {
                Text(if (isCheckingUpdate) updateStatus.ifEmpty { "检查中..." } else "检查更新")
            }

            // 检查中实时进度
            if (isCheckingUpdate && updateStatus.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(updateStatus, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }

            // 发现新版本
            if (updateInfo != null) {
                Spacer(Modifier.height(8.dp))
                Text("发现新版本：${updateInfo!!.version}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(updateInfo!!.body, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    com.quickdaily.util.UpdateChecker.openReleasePage(context, updateInfo!!.releaseUrl)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("前往下载")
                }
            }

           // 已是最新版本
           if (isLatest) {
               Spacer(Modifier.height(4.dp))
                Text("当前已是最新版本（${BuildConfig.VERSION_NAME}）",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary)
           }

            // 失败：显示每个源的错误
            if (updateErrors.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("检查更新失败，已尝试 ${updateErrors.size} 个镜像源：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                updateErrors.forEach { err ->
                    Text("• ${err.source}：${err.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 8.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── 辅助服务 ──
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("辅助服务", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            // 无障碍服务状态显示 — 用 isAvailable 综合判断，每次进入页面重新计算
            var accReady by remember { mutableStateOf(false) }
            // 每次进入辅助服务区域时重新检测（不用 remember 只算一次）
            accReady = com.quickdaily.QuickAccessibilityService.isAvailable(context)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    "速记辅助服务：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (accReady) "已开启" else "未开启",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (accReady) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                "通知面板快捷磁贴点击后，需要此服务模拟返回键收起通知面板。" +
                "如未开启，点击磁贴后会弹出提示并跳转设置页。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(8.dp))

            FilledTonalButton(
                onClick = {
                    try {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    } catch (_: Exception) {
                    }
                }
            ) {
                Text(if (accReady) "前往无障碍设置" else "开启速记辅助服务")
            }

            Spacer(Modifier.height(24.dp))

            // ── 关于 ──
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("关于", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text("QuickDaily ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(12.dp))
            val coolapkAnnotated = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)) { append("酷安@附近的人") }
            }
            ClickableText(text = coolapkAnnotated, onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coolapk.com/u/400522")))
            }, modifier = Modifier.padding(top = 4.dp))

            val githubAnnotated = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)) { append("GitHub@agarcabin") }
            }
            ClickableText(text = githubAnnotated, onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/agarcabin/QuickDaily")))
            }, modifier = Modifier.padding(top = 2.dp))

            val qqGroupAnnotated = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)) { append("添加QQ群：1050092886") }
            }
            ClickableText(text = qqGroupAnnotated, onClick = {
                // 尝试拉起 QQ 加群资料卡，失败则跳浏览器
                val qqIntent = Intent(Intent.ACTION_VIEW, Uri.parse(
                    "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=1050092886&card_type=group&source=qrcode"
                ))
                try {
                    context.startActivity(qqIntent)
                } catch (_: Exception) {
                    // 未安装 QQ，跳转到群官网
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://qm.qq.com/cgi-bin/qm/qr?from=app&p=android&jump_from=webapi&k=20251120")))
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(context, "请安装 QQ 或手动搜索群号 1050092886", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }, modifier = Modifier.padding(top = 2.dp))

           Text("更新内容：", style = MaterialTheme.typography.labelSmall,
               color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
               modifier = Modifier.padding(top = 8.dp))
            Text("1.4.1:\n" +
                "• 新增 Templater 日期格式支持 \n" +
                "• 新增 阅读视图图片显示 \n" +
                "• 新增 编辑器底部快捷工具栏 \n" +
                "• 新增 赞赏码 \n" +
                "• 修复 小部件空任务输入 \n" +
                "• 修复 浮窗任务切换格式 \n" +
                "• 改进 小部件兼容性 \n\n",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp))
            Text("1.4:\n" +
                "• 新增 Frontmatter 过滤 \n" +
                "• 新增 WW 等日期格式支持 \n" +
                "• 新增 悬浮窗增加图片录入功能（可批量导入）\n" +
                "• 新增 悬浮窗增加任务录入功能（双击切换任务状态） \n" +
                "• 新增 《今日任务》桌面小部件 \n\n",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp))
            Text("1.3:\n" +
                "• 新增 7种时间戳格式设置，可适配Thino/Knomo \n" +
               "• 新增 时间戳文本插入顺序\n" +
               "• 新增 无锚点时自动添加锚点文本\n" +
                "• 修复 清空日记内容后无法重新加载模板\n" +
                "• 修复 每日首次录入内容时略过日记模板\n\n" +
                "1.2:\n" +
                "• 新增 快速添加（桌面图标）\n" +
                "• 修复 磁贴点击后收回状态栏\n\n" +
                "1.1:\n" +
                "• 新增 小部件时间戳，回车保存\n" +
                "• 新增 小部件自定义图片\n" +
                "• 新增 状态栏快捷磁贴\n" +
                "• 新增 文本分享至本应用\n" +
                "• 新增 检测更新\n\n" +
                "1.0:\n" +
               "• 正式发布！APP 更名为 QuickDaily\n" +
               "• 开源发布到 GitHub",
               style = MaterialTheme.typography.bodySmall,
               color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
               modifier = Modifier.padding(top = 4.dp))

            Spacer(Modifier.height(20.dp))
            Text("如果您喜欢 QuickDaily，可以扫码支持：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp))
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).combinedClickable(
                    onClick = { showQRFull = true },
                    onLongClick = {
                        try {
                            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.qr_donate)
                            val filename = "QuickDaily_donate_${System.currentTimeMillis()}.jpg"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val values = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                                    put(MediaStore.Images.Media.IS_PENDING, 1)
                                }
                                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                uri?.let {
                                    context.contentResolver.openOutputStream(it)?.use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                    }
                                    values.clear()
                                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                                    context.contentResolver.update(it, values, null, null)
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                dir.mkdirs()
                                val file = java.io.File(dir, filename)
                                java.io.FileOutputStream(file).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                }
                                val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file))
                                context.sendBroadcast(scanIntent)
                            }
                            android.widget.Toast.makeText(context, "已保存至相册，谢谢！", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "保存失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ),
                contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.qr_donate),
                    contentDescription = "赞赏码",
                    modifier = Modifier.width(280.dp).height(280.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // ── 赞赏码大图预览 Dialog ──
            if (showQRFull) {
                Dialog(onDismissRequest = { showQRFull = false }) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.qr_donate),
                                contentDescription = "赞赏码",
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "点击关闭 · 长按保存到相册",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}
