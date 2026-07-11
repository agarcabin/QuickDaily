package com.quickdaily.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    appState: AppState = viewModel(),
    onSettingsClick: () -> Unit
) {
    val diaryContent by appState.diaryContent.collectAsState()
    val isLoaded by appState.isLoaded.collectAsState()
    val todayPath by appState.todayPath.collectAsState()
    val config by appState.config.collectAsState()
    val title = todayPath.substringAfterLast("/").removeSuffix(".md")

    var showPreview by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

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
        // ── 底部工具栏（仅编辑模式显示） ──
        if (!showPreview) {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 图片
                    ToolbarIconButton(
                        icon = { Icon(Icons.Default.Image, "插入图片", modifier = Modifier.size(22.dp)) },
                        onClick = {
                            val start = textFieldValue.selection.start
                            val text = textFieldValue.text
                            val newText = text.substring(0, start) + "![]()" + text.substring(start)
                            textFieldValue = TextFieldValue(newText, TextRange(start + 4))
                        }
                    )
                    // 任务
                    ToolbarIconButton(
                        icon = { Icon(Icons.Default.CheckBoxOutlineBlank, "插入任务", modifier = Modifier.size(22.dp)) },
                        onClick = {
                            val text = textFieldValue.text
                            val cursor = textFieldValue.selection.start
                            val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
                            val newText = text.substring(0, lineStart) + "- [ ] " + text.substring(lineStart)
                            textFieldValue = TextFieldValue(newText, TextRange(cursor + 6))
                        }
                    )
                    // 标题
                    ToolbarIconButton(
                        icon = { Text("#", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                        onClick = {
                            val text = textFieldValue.text
                            val cursor = textFieldValue.selection.start
                            val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
                            val newText = text.substring(0, lineStart) + "## " + text.substring(lineStart)
                            textFieldValue = TextFieldValue(newText, TextRange(cursor + 3))
                        }
                    )
                    // 列表
                    ToolbarIconButton(
                        icon = { Icon(Icons.Default.FormatListBulleted, "插入列表", modifier = Modifier.size(22.dp)) },
                        onClick = {
                            val text = textFieldValue.text
                            val cursor = textFieldValue.selection.start
                            val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
                            val newText = text.substring(0, lineStart) + "- " + text.substring(lineStart)
                            textFieldValue = TextFieldValue(newText, TextRange(cursor + 2))
                        }
                    )
                    // 加粗
                    ToolbarIconButton(
                        icon = { Icon(Icons.Default.FormatBold, "加粗", modifier = Modifier.size(22.dp)) },
                        onClick = {
                            val start = textFieldValue.selection.start
                            val end = textFieldValue.selection.end
                            val text = textFieldValue.text
                            if (start != end) {
                                val selected = text.substring(start, end)
                                val newText = text.substring(0, start) + "**" + selected + "**" + text.substring(end)
                                textFieldValue = TextFieldValue(newText, TextRange(newText.length))
                            } else {
                                val newText = text.substring(0, start) + "****" + text.substring(start)
                                textFieldValue = TextFieldValue(newText, TextRange(start + 2))
                            }
                        }
                    )
                }
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
