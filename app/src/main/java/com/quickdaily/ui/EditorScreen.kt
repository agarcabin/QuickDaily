package com.quickdaily.ui

import android.app.Activity
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.AttachFile
import com.quickdaily.BetaLogger
import androidx.compose.ui.platform.LocalView
import android.view.inputmethod.InputMethodManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quickdaily.AppState
import com.quickdaily.markdown.MdRenderer
import com.quickdaily.markdown.toggleTaskCheck
import com.quickdaily.util.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    appState: AppState = viewModel(),
    onExternalLaunch: () -> Unit = {},
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Bug2: 设置导航栏颜色与工具栏一致（提前 capture，避免 @Composable 访问问题）
    val navBarColor = MaterialTheme.colorScheme.surface.toArgb()
    val diaryContent by appState.diaryContent.collectAsState()
    val isLoaded by appState.isLoaded.collectAsState()
    val todayPath by appState.todayPath.collectAsState()
    val config by appState.config.collectAsState()
    val allTags by appState.tags.collectAsState()
    val canUndo by appState.canUndo.collectAsState()
    val canRedo by appState.canRedo.collectAsState()
        val view = LocalView.current
val title = todayPath.substringAfterLast("/").removeSuffix(".md")
    SideEffect {
        try {
            val window = (context as? Activity)?.window ?: return@SideEffect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            window.navigationBarColor = navBarColor
            android.util.Log.d("QuickDaily", "Editor nav bar color set")
        } catch (_: Exception) { }
    }

    var showPreview by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    // 图片选择器（跟悬浮窗一样）
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val links = ImageUtil.processImages(
                context, uris, config.vaultPath,
                config.imageStoragePath, config.imageNamingFormat,
                config.imageLinkFormat, config.imageCustomNamingFormat
            )
            withContext(Dispatchers.Main) {
                val text = textFieldValue.text
                val cursor = textFieldValue.selection.start
                val imagesText = links.joinToString("\n")
                val newText = text.substring(0, cursor) + imagesText + "\n" + text.substring(cursor)
                textFieldValue = TextFieldValue(newText, TextRange(cursor + imagesText.length + 1))
                appState.onContentChanged(newText, forceUndoPoint = true)
            }
        }
    }
    // 附件选择器
    val attachmentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val mimeType = context.contentResolver.getType(uri) ?: "*/*"
            val link = if (mimeType.startsWith("image/")) {
                val links = ImageUtil.processImages(
                    context, listOf(uri), config.vaultPath,
                    config.imageStoragePath, config.imageNamingFormat,
                    config.imageLinkFormat, config.imageCustomNamingFormat
                )
                links.firstOrNull() ?: return@launch
            } else {
                val vaultPath = config.vaultPath
                val storagePath = config.imageStoragePath
                val dir = if (storagePath.isBlank()) "" else storagePath.trim('/')
                val dirPath = if (dir.isNotEmpty()) vaultPath.trimEnd('/') + "/" + dir else vaultPath.trimEnd('/')
                val destDirFile = java.io.File(dirPath)
                destDirFile.mkdirs()
                val displayName = com.quickdaily.util.ImageUtil.getDisplayName(context, uri)
                val ext = com.quickdaily.util.ImageUtil.getExtension(context, uri)
                val fileName = com.quickdaily.util.ImageUtil.generateFileName(config.imageNamingFormat, displayName, ext, config.imageCustomNamingFormat)
                val destFile = java.io.File(destDirFile, fileName)
                context.contentResolver.openInputStream(uri)?.use { input: java.io.InputStream? ->
                    if (input != null) {
                        java.io.FileOutputStream(destFile).use { output: java.io.FileOutputStream ->
                            input.copyTo(output)
                        }
                    }
                }
                val relativePath = if (dir.isNotEmpty()) dir + "/" + fileName else fileName
                "![[" + relativePath + "]]"
            }
            withContext(Dispatchers.Main) {
                val text = textFieldValue.text
                val cursor = textFieldValue.selection.start
                val newText = text.substring(0, cursor) + link + "\n" + text.substring(cursor)
                textFieldValue = TextFieldValue(newText, TextRange(cursor + link.length + 1))
                appState.onContentChanged(newText, forceUndoPoint = true)
            }
        }
    }


    // -- Tag autocomplete --
    val tagCompletion = remember(textFieldValue, config) {
        if (!config.tagAutocomplete) return@remember Triple(false, "", 0)
        val text = textFieldValue.text
        val cursor = textFieldValue.selection.start
        if (cursor > 0 && cursor <= text.length) {
            val before = text.substring(0, cursor)
            val hi = before.lastIndexOf('#')
            if (hi >= 0) {
                val after = before.substring(hi + 1)
                // A lone # is a valid completion prefix: show recent tags immediately.
                if (after.isEmpty() || (after[0] != ' ' && !after.all { it == '#' })) {
                    val p = after.takeWhile { it.isLetterOrDigit() || it == '_' || it == '/' || it == '-' }
                    val wordBefore = hi > 0 && (text[hi - 1].isLetterOrDigit() || text[hi - 1] == '_')
                    if (!wordBefore) {
                        val tagFinished = p in allTags && (after.length == p.length || after.length > p.length && (!after[p.length].isLetterOrDigit() && after[p.length] != '#'))
                        if (!tagFinished) {
                            return@remember Triple(true, p, hi)
                        }
                    }
                }
            }
        }
        Triple(false, "", 0)
    }

    val (tagActive, tagPrefix, tagHashPos) = tagCompletion

    val matchingTags = remember(tagActive, tagPrefix, allTags) {
        if (!tagActive) emptyList()
        else {
            val p = tagPrefix
            if (p.isEmpty()) {
                val recent = com.quickdaily.util.RecentTags.get(context)
                (recent + allTags.filterNot { it in recent }).take(3)
            }
            else {
                allTags.filter { it.contains(p as CharSequence, ignoreCase = true) }.take(8)
            }
        }
    }

    val selectTag: (String) -> Unit = remember(tagHashPos) {
        { tag ->
            val text = textFieldValue.text
            val cursor = textFieldValue.selection.start
            val hp = tagHashPos
            val needSpaceBefore = hp > 0 && text[hp - 1] != ' ' && text[hp - 1] != '\n'
            val prefix = if (needSpaceBefore) " #" else "#"
            val newText = text.substring(0, hp) + prefix + tag + " " + text.substring(cursor)
            val newCursor = hp + prefix.length + tag.length + 1
            textFieldValue = TextFieldValue(newText, TextRange(newCursor))
            appState.onContentChanged(newText, forceUndoPoint = true)
            com.quickdaily.util.RecentTags.record(context, tag)
        }
    }

    LaunchedEffect(Unit) { appState.loadToday() }
    LaunchedEffect(diaryContent) {
        if (diaryContent != textFieldValue.text) textFieldValue = TextFieldValue(diaryContent)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
            TopAppBar(
                title = { Text(title.ifEmpty { "QuickDaily" }, style = MaterialTheme.typography.titleMedium) },
                actions = {

                    TextButton(onClick = {
                        val vaultName = config.vaultPath.trimEnd('/').substringAfterLast('/')
                        if (vaultName.isNotBlank()) {
                            try {
                                val date = com.quickdaily.util.DateUtil.todayStr(config.dateFormat)
                                val relativePath = Uri.encode("${config.diaryFolder.trimEnd('/')}/${date}.md")
                                val uri = Uri.parse("obsidian://open?vault=${Uri.encode(vaultName)}&file=$relativePath")
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "未安装 Obsidian", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("打开Obsidian",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary)
                    }

                    IconButton(onClick = { showPreview = !showPreview }) {
                        Icon(if (showPreview) Icons.Default.Edit else Icons.Default.Visibility, null)
                    }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            }
        },
        bottomBar = {
            if (!showPreview) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    // 小白条上方 + 输入法上方
                    modifier = Modifier.fillMaxWidth().imePadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 图片 - 拉起安卓图片选择器
                        ToolbarIconButton(
                            icon = { Icon(Icons.Default.Image, "插入图片", modifier = Modifier.size(22.dp)) },
                            onClick = { onExternalLaunch(); imagePicker.launch("image/*") }
                        )
                        // 任务 - 三态循环
                        ToolbarIconButton(
                            icon = { Icon(Icons.Default.CheckBoxOutlineBlank, "插入任务", modifier = Modifier.size(22.dp)) },
                            onClick = {
                                val t = textFieldValue.text; val c = textFieldValue.selection.start
                                val ls = t.lastIndexOf('\n', c - 1) + 1; val le = t.indexOf('\n', c).let { if (it < 0) t.length else it }
                                val line = t.substring(ls, le)
                                val re = Regex("""^\s*(-\s*\[\s*([ xX])\s*\])\s*""")
                                val m = re.find(line)
                                val (nt, nc) = if (m != null) {
                                    val chk = m.groupValues[2]; val rest = line.substring(m.value.length).trimStart()
                                    if (chk.trim().isEmpty()) {
                                        t.substring(0, ls) + "- [x] $rest" + t.substring(le) to (ls + 6)
                                    } else {
                                        t.substring(0, ls) + rest + t.substring(le) to ls
                                    }
                                } else {
                                    t.substring(0, ls) + "- [ ] $line" + t.substring(le) to (ls + 6)
                                }
                                textFieldValue = TextFieldValue(nt, TextRange(nc))
                                appState.onContentChanged(nt, forceUndoPoint = true)
                            }
                        )
                        // #号 - 标题循环
                        ToolbarIconButton(
                            icon = { Text("#", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                            onClick = {
                                val text = textFieldValue.text
                                val pos = textFieldValue.selection.start
                                val lineStart = text.lastIndexOf("\n", pos - 1) + 1
                                val lineEnd = text.indexOf("\n", pos).let { if (it < 0) text.length else it }
                                val line = text.substring(lineStart, lineEnd)
                                val trimmed = line.trimStart()
                                val newTrimmed = when {
                                    trimmed.startsWith("### ") -> trimmed.removePrefix("### ")
                                    trimmed.startsWith("## ") -> "### " + trimmed.removePrefix("## ")
                                    trimmed.startsWith("# ") -> "## " + trimmed.removePrefix("# ")
                                    else -> "# " + trimmed
                                }
                                val indent = line.substring(0, line.length - trimmed.length)
                                val newLine = indent + newTrimmed
                                val nt = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
                                val nc = lineStart + newLine.length
                                textFieldValue = TextFieldValue(nt, TextRange(nc))
                                appState.onContentChanged(nt, forceUndoPoint = true)
                            }
                        )
                        // -号 - 行首切换-
                        ToolbarIconButton(
                            icon = { Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                            onClick = {
                                val t = textFieldValue.text; val c = textFieldValue.selection.start
                                val ls = t.lastIndexOf('\n', c - 1) + 1
                                val cl = t.substring(ls)
                                val (nt, nc) = if (cl.startsWith("- ")) {
                                    t.substring(0, ls) + cl.removePrefix("- ") to (c - 2).coerceAtLeast(ls)
                                } else {
                                    t.substring(0, ls) + "- " + cl to c + 2
                                }
                                textFieldValue = TextFieldValue(nt, TextRange(nc))
                                appState.onContentChanged(nt, forceUndoPoint = true)
                            }
                        )
                        // 加粗 - 切换****
                        ToolbarIconButton(
                            icon = { Icon(Icons.Default.FormatBold, "加粗", modifier = Modifier.size(22.dp)) },
                            onClick = {
                                val t = textFieldValue.text; val c = textFieldValue.selection.start
                                if (c >= 2 && c + 2 <= t.length && t.substring(c - 2, c) == "**" && t.substring(c, c + 2) == "**") {
                                    val nt = t.substring(0, c - 2) + t.substring(c + 2)
                                    textFieldValue = TextFieldValue(nt, TextRange(c - 2)); appState.onContentChanged(nt, forceUndoPoint = true)
                                } else {
                                    val nt = t.substring(0, c) + "****" + t.substring(c)
                                    textFieldValue = TextFieldValue(nt, TextRange(c + 2)); appState.onContentChanged(nt, forceUndoPoint = true)
                                }
                            }
                        )
                        // 附件
                        ToolbarIconButton(
                            icon = { Icon(Icons.Default.AttachFile, "插入附件", modifier = Modifier.size(22.dp)) },
                            onClick = { onExternalLaunch(); attachmentPicker.launch("*/*") }
                        )
                        // 撤销
                        ToolbarIconButton(
                            icon = { Icon(Icons.Default.Undo, "撤销", modifier = Modifier.size(22.dp)) },
                            onClick = { appState.undo(); BetaLogger.log("Toolbar", "undo") },
                            enabled = canUndo
                        )
                        // 重做
                        ToolbarIconButton(
                            icon = { Icon(Icons.Default.Redo, "重做", modifier = Modifier.size(22.dp)) },
                            onClick = { appState.redo(); BetaLogger.log("Toolbar", "redo") },
                            enabled = canRedo
                        )
                        // 关闭键盘
                        ToolbarIconButton(
                            icon = { Icon(Icons.Default.KeyboardArrowDown, "关闭键盘", modifier = Modifier.size(22.dp)) },
                            onClick = {
                                val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.hideSoftInputFromWindow(view.windowToken, 0)
                                BetaLogger.log("Toolbar", "close_keyboard")
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

        if (!isLoaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (showPreview) {
            Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                MdRenderer(text = diaryContent, vaultBasePath = config.vaultPath, imageStoragePath = config.imageStoragePath.takeIf { it.isNotBlank() }, onToggleCheckbox = { index ->
                    appState.onContentChanged(toggleTaskCheck(diaryContent, index), forceUndoPoint = true)
                })
            }
        } else {
            val scrollState = rememberScrollState()
            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            var viewportH by remember { mutableStateOf(0) }
            val density = LocalDensity.current
            val padPx = with(density) { 16.dp.toPx() }
            val sel = textFieldValue.selection.start
            LaunchedEffect(sel) {
                textLayoutResult?.let { layout ->
                    if (viewportH <= 0) return@let
                    val r = layout.getCursorRect(sel)
                    val cursorY = r.bottom + padPx
                    val st = scrollState.value
                    val midY = st + viewportH / 2
                    if (cursorY > midY) {
                        val target = (cursorY - viewportH / 2).toInt().coerceAtLeast(0)
                        scrollState.animateScrollTo(target)
                    } else if (r.top + padPx < st) {
                        scrollState.animateScrollTo((r.top + padPx).toInt().coerceAtLeast(0))
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                        .onSizeChanged { viewportH = it.height }
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            appState.onContentChanged(newValue.text)
                        },
                        onTextLayout = { textLayoutResult = it },
                        textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 24.sp),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 400.dp),
                        decorationBox = { innerField ->
                            if (textFieldValue.text.isEmpty()) Text("开始写今天的日记...", color = Color.Gray, fontSize = 16.sp)
                            innerField()
                        }
                    )
                }
                // Tag autocomplete Popup near cursor
                if (tagActive && matchingTags.isNotEmpty()) {
                    val cursorPos = textFieldValue.selection.start
                    val layoutResult = textLayoutResult
                    val cr = layoutResult?.takeIf { cursorPos <= it.layoutInput.text.length }?.getCursorRect(cursorPos)
                    val density = LocalDensity.current
                    val padPx = with(density) { 16.dp.toPx() }
                    val sy = scrollState.value
                    val popupX = ((cr?.left?.toInt() ?: 0) + padPx.toInt()).coerceAtLeast(8)
                    val popupY = ((cr?.bottom?.toInt() ?: 0) + padPx.toInt() - sy + 8).coerceAtLeast(0)
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(popupX, popupY),
                        properties = PopupProperties(focusable = false, dismissOnBackPress = true, dismissOnClickOutside = true)
                    ) {
                        Surface(
                            modifier = Modifier.widthIn(max = 300.dp).heightIn(max = 200.dp),
                            shadowElevation = 6.dp,
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 2.dp)) {
                                matchingTags.forEach { tag ->
                                    TextButton(
                                        onClick = { selectTag(tag) },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp)
                                    ) {
                                        Text(
                                            "#$tag",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                }
            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ObsidianIcon(modifier: Modifier = Modifier) {
    val tintColor = androidx.compose.material3.LocalContentColor.current
    Canvas(modifier = modifier) {
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val path = Path().apply {
            moveTo(w / 2f, 0f)
            lineTo(w, h * 0.45f)
            lineTo(w / 2f, h)
            lineTo(0f, h * 0.45f)
            close()
        }
        drawPath(path, tintColor)
        
        val inner = Path().apply {
            moveTo(w / 2f, h * 0.25f)
            lineTo(w * 0.6f, h * 0.45f)
            lineTo(w / 2f, h * 0.65f)
            lineTo(w * 0.4f, h * 0.45f)
            close()
        }
        drawPath(inner, tintColor.copy(alpha = 0.35f))
    }
}

@Composable
private fun ToolbarIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val dim = com.quickdaily.ui.theme.LocalAppDimensions.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(dim.spacing3xl)
    ) {
        icon()
    }
}


