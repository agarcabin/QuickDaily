package com.quickdaily.ui

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quickdaily.AppState
import com.quickdaily.markdown.MdRenderer
import com.quickdaily.markdown.toggleTaskCheck
import com.quickdaily.util.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    appState: AppState = viewModel(),
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val diaryContent by appState.diaryContent.collectAsState()
    val isLoaded by appState.isLoaded.collectAsState()
    val todayPath by appState.todayPath.collectAsState()
    val config by appState.config.collectAsState()
    val title = todayPath.substringAfterLast("/").removeSuffix(".md")

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
                appState.onContentChanged(newText)
            }
        }
    }

    LaunchedEffect(Unit) { appState.loadToday() }
    LaunchedEffect(diaryContent) {
        if (diaryContent != textFieldValue.text) textFieldValue = TextFieldValue(diaryContent)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title.ifEmpty { "QuickDaily" }, style = MaterialTheme.typography.titleMedium) },
                actions = {
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
        },
        bottomBar = {
            if (!showPreview) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    // 小白条上方 + 输入法上方
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 图片 - 拉起安卓图片选择器
                        ToolbarIconButton(
                            icon = { Icon(Icons.Default.Image, "插入图片", modifier = Modifier.size(22.dp)) },
                            onClick = { imagePicker.launch("image/*") }
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
                                appState.onContentChanged(nt)
                            }
                        )
                        // #号 - 插入单个#
                        ToolbarIconButton(
                            icon = { Text("#", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                            onClick = {
                                val t = textFieldValue.text; val c = textFieldValue.selection.start
                                val nt = t.substring(0, c) + "#" + t.substring(c)
                                textFieldValue = TextFieldValue(nt, TextRange(c + 1))
                                appState.onContentChanged(nt)
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
                                appState.onContentChanged(nt)
                            }
                        )
                        // 加粗 - 切换****
                        ToolbarIconButton(
                            icon = { Icon(Icons.Default.FormatBold, "加粗", modifier = Modifier.size(22.dp)) },
                            onClick = {
                                val t = textFieldValue.text; val c = textFieldValue.selection.start
                                if (c >= 2 && c + 2 <= t.length && t.substring(c - 2, c) == "**" && t.substring(c, c + 2) == "**") {
                                    val nt = t.substring(0, c - 2) + t.substring(c + 2)
                                    textFieldValue = TextFieldValue(nt, TextRange(c - 2)); appState.onContentChanged(nt)
                                } else {
                                    val nt = t.substring(0, c) + "****" + t.substring(c)
                                    textFieldValue = TextFieldValue(nt, TextRange(c + 2)); appState.onContentChanged(nt)
                                }
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
                MdRenderer(text = diaryContent, vaultBasePath = config.vaultPath, onToggleCheckbox = { index ->
                    appState.onContentChanged(toggleTaskCheck(diaryContent, index))
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
        }
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp)
    ) {
        icon()
    }
}
