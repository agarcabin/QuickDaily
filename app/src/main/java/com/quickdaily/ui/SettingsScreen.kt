package com.quickdaily.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quickdaily.AppState
import com.quickdaily.DiaryConfig
import com.quickdaily.QuickNoteWidget
import com.quickdaily.util.UriUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appState: AppState = viewModel(),
    onBack: () -> Unit,
    onExternalLaunch: () -> Unit = {}
) {
    val context = LocalContext.current
    val config by appState.config.collectAsState()
    val todayPath by appState.todayPath.collectAsState()

    var vaultPath by remember { mutableStateOf(config.vaultPath) }
    var diaryFolder by remember { mutableStateOf(config.diaryFolder) }
    var dateFormat by remember { mutableStateOf(config.dateFormat) }
    var templatePath by remember { mutableStateOf(config.templatePath) }
    var anchorText by remember { mutableStateOf(config.anchorText) }

    var obsidianDetected by remember { mutableStateOf(false) }
    var obsidianMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    // 小部件图片 URI
    var widgetImageUri by remember { mutableStateOf(config.widgetImageUri) }
    
    // ── 图片选择器 ──
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* 权限持久化失败不崩溃 */ }
            widgetImageUri = it.toString()
            appState.saveConfig(config.copy(widgetImageUri = it.toString()))
            // 更新所有小部件
            QuickNoteWidget.updateAllWidgets(context)
        }
    }

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
                // 转为相对 vault 的路径
                templatePath = if (vaultPath.isNotBlank() && path.startsWith(vaultPath)) {
                    path.removePrefix(vaultPath).trimStart('/')
                } else {
                    path
                }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
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
            // ── Vault 路径 ──
            Text("Obsidian 仓库路径", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = vaultPath,
                    onValueChange = { vaultPath = it },
                    label = { Text("/storage/emulated/0/Documents/Vault") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        onExternalLaunch()
                        vaultPicker.launch(null)
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, "选择文件夹", Modifier.size(18.dp))
                }
            }

            // 检测 Obsidian 配置
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        val obsCfg = appState.loadObsidianConfig(vaultPath)
                        if (obsCfg != null) {
                            diaryFolder = obsCfg.diaryFolder
                            dateFormat = obsCfg.dateFormat
                            templatePath = obsCfg.templatePath
                            obsidianDetected = true
                            obsidianMsg = "已读取 Obsidian 配置"
                        } else {
                            obsidianDetected = false
                            obsidianMsg = "未找到 .obsidian/daily-notes.json"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("从 Obsidian 读取配置")
            }
            if (obsidianMsg.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    obsidianMsg,
                    color = if (obsidianDetected)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // ── 日记文件夹 ──
            Text("日记文件夹", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = diaryFolder,
                onValueChange = { diaryFolder = it },
                label = { Text("Daily") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            // ── 日期格式 ──
            Text("日期格式", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = dateFormat,
                onValueChange = { dateFormat = it },
                label = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            // ── 模板路径 ──
            Text("模板路径", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = templatePath,
                    onValueChange = { templatePath = it },
                    label = { Text("Templates/daily.md（可选）") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        onExternalLaunch()
                        templatePicker.launch(arrayOf("text/markdown", "text/plain", "*/*"))
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(Icons.Default.FileOpen, "选择文件", Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 速记锚点 ──
            Text("速记锚点文本", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = anchorText,
                onValueChange = { anchorText = it },
                label = { Text("例：## 今日速记") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                "速记内容插入到该文本之后；为空则添加到末尾",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ── 速记设置 ──
            Text("速记设置", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            
            // 时间戳开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("添加时间戳", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = config.addTimestamp,
                    onCheckedChange = { checked ->
                        appState.saveConfig(config.copy(addTimestamp = checked))
                    }
                )
            }
            Text(
                "开启后在速记内容前添加时间，如 \"10:30 内容\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp)
            )
            
            Spacer(Modifier.height(12.dp))
            
            // 回车键直接保存
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("回车键直接保存", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = config.enterToSave,
                    onCheckedChange = { checked ->
                        appState.saveConfig(config.copy(enterToSave = checked))
                    }
                )
            }
            Text(
                "开启后回车键直接保存速记，无法换行（单行输入）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ── 小部件设置 ──
            Text("小部件设置", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            
            // 自定义图片
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("自定义图片", style = MaterialTheme.typography.bodyMedium)
                Row {
                    FilledTonalButton(
                        onClick = {
                            onExternalLaunch()
                            imagePicker.launch("image/*")
                        }
                    ) {
                        Text("选择图片")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            widgetImageUri = ""
                            appState.saveConfig(config.copy(widgetImageUri = ""))
                            // 更新所有小部件
                            QuickNoteWidget.updateAllWidgets(context)
                        }
                    ) {
                        Text("重置")
                    }
                }
            }
            if (widgetImageUri.isNotEmpty()) {
                Text(
                    "已选择图片：${widgetImageUri.take(50)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text(
                    "未选择图片，使用默认+号",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── 预览 ──
            Text("今天的日记路径", style = MaterialTheme.typography.titleSmall)
            Text(
                todayPath.ifEmpty { "(输入 vault 路径后自动计算)" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(24.dp))

            // ── 保存 ──
            Button(
                onClick = {
                    appState.saveConfig(
                        DiaryConfig(vaultPath, diaryFolder, dateFormat, templatePath, anchorText)
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = vaultPath.isNotBlank()
            ) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("保存并返回")
            }

            Spacer(Modifier.height(32.dp))

            // ── 关于 ──
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("关于", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text("QuickDaily 1.0.2", style = MaterialTheme.typography.titleMedium)
            // 检查更新按钮
            Spacer(Modifier.height(8.dp))
            var isCheckingUpdate by remember { mutableStateOf(false) }
            var updateInfo by remember { mutableStateOf<com.quickdaily.util.ReleaseInfo?>(null) }
            var updateError by remember { mutableStateOf("") }
            
            Button(
                onClick = {
                    isCheckingUpdate = true
                    updateError = ""
                    updateInfo = null
                    scope.launch {
                        val info = com.quickdaily.util.UpdateChecker.checkUpdate()
                        if (info != null) {
                            updateInfo = info
                        } else {
                            updateError = "检查更新失败，请稍后重试"
                        }
                        isCheckingUpdate = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCheckingUpdate
            ) {
                if (isCheckingUpdate) {
                    Text("检查中...")
                } else {
                    Text("检查更新")
                }
            }
            
            if (updateInfo != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "发现新版本：${updateInfo!!.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    updateInfo!!.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        com.quickdaily.util.UpdateChecker.openReleasePage(context, updateInfo!!.releaseUrl)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("前往 GitHub 下载")
                }
            }
            
            if (updateError.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    updateError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Spacer(Modifier.height(12.dp))
            // 可点击的酷安链接
            val coolapkAnnotated = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)) {
                    append("酷安@附近的人")
                }
            }
            ClickableText(
                text = coolapkAnnotated,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coolapk.com/u/400522"))
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(top = 4.dp)
            )
            // GitHub 链接
            val githubAnnotated = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)) {
                    append("GitHub@agarcabin")
                }
            }
            ClickableText(
                text = githubAnnotated,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/agarcabin/QuickDaily"))
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                "更新内容：",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "1.0:\n" +
                "• 正式发布！APP 更名为 QuickDaily\n" +
                "• 包名、类名全部统一为 QuickDaily\n" +
                "• 开源发布到 GitHub\n" +
                "\n" +
                "beta0.16:\n" +
                "• 新增速记锚点文本设置\n" +
                "• 速记可插入到指定锚点文本之后\n" +
                "\n" +
                "beta0.15:\n" +
                "• 空 .md 文件自动加载模板内容\n" +
                "• 光标超出屏幕自动滚回可视区域\n" +
                "\n" +
                "beta0.14:\n" +
                "• 禁用 Scaffold contentWindowInsets 消除键盘抬高\n" +
                "• 移除所有自动滚动/留白逻辑，回归简洁\n" +
                "\n" +
                "beta0.13:\n" +
                "• 状态栏改用 Scaffold 自动着色\n" +
                "• 悬浮窗重构：透明主题 + 外部点击保存 + Toast\n" +
                "• 键盘弹起时光标自动居中\n" +
                "• 笔记底部留白优化\n" +
                "\n" +
                "beta12.0:\n" +
                "• 达尔文进化：修复4个已知Bug + 架构精简\n" +
                "• 读取失败不再覆盖日记（ReadResult sealed class）\n" +
                "• UriUtil 支持 SD 卡 vault\n" +
                "• DateUtil 统一日期转换逻辑\n" +
                "• 速记窗使用 Obsidian 配置的日期格式\n" +
                "• Widget 线程安全 + 状态栏修复\n" +
                "\n" +
                "beta11.0:\n" +
                "• 悬浮窗完全透明背景，桌面透视\n" +
                "• 文本滚动方案替代 imePadding\n" +
                "• 性能优化去卡顿\n" +
                "\n" +
                "beta0.12:\n" +
                "• 键盘白条修复 + 状态栏着色\n" +
                "• 悬浮窗透明背景修复\n" +
                "\n" +
                "beta0.11:\n" +
                "• 应用图标：自定义 logo\n" +
                "• 首页和悬浮窗 UI 优化\n" +
                "\n" +
                "beta0.10:\n" +
                "• 悬浮窗背景半透明\n" +
                "• 编辑页键盘自适应不遮挡文本\n" +
                "\n" +
                "beta0.9:\n" +
                "• 重构悬浮窗：精确控制位置/大小\n" +
                "• 小部件名称区分：桌面便签 / 快速添加\n" +
                "• SAF 选择器闪退修复\n" +
                "\n" +
                "beta0.8:\n" +
                "• 代码审查高危/中危修复\n" +
                "• Android 13+ 图片权限\n" +
                "\n" +
                "beta0.7:\n" +
                "• 去掉隐藏文本组，小部件改名\n" +
                "• 悬浮窗口居中，退出后台自杀\n" +
                "\n" +
                "beta0.6:\n" +
                "• 键盘自适应抬高\n" +
                "• 切后台自杀，切前台重读文件\n" +
                "• 速记浮窗深色背景\n" +
                "\n" +
                "beta0.5:\n" +
                "• 隐藏文本组设置管理\n" +
                "• 桌面小部件初版\n" +
                "\n" +
                "beta0.4:\n" +
                "• 桌面便签小部件\n" +
                "• 模板路径自动补 .md\n" +
                "\n" +
                "beta0.3:\n" +
                "• 保存配置自动 trim + 默认值\n" +
                "• 标题改为日期，模板不强制写标题\n" +
                "\n" +
                "beta0.2:\n" +
                "• 首次启动进设置\n" +
                "• SAF 文件夹/文件选择器\n" +
                "• 启动申请存储权限\n" +
                "\n" +
                "beta0.1:\n" +
                "• 秒开日记 + 自动保存\n" +
                "• Obsidian 配置读取 + 模板\n" +
                "• Markdown 渲染 + Android 15 适配",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
