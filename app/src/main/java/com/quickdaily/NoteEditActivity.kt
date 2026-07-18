package com.quickdaily

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.quickdaily.BetaLogger
import com.quickdaily.util.DateUtil
import com.quickdaily.util.ContentUtil
import com.quickdaily.util.ImageUtil
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.quickdaily.ui.theme.*
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import android.net.Uri
import com.quickdaily.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class NoteEditActivity : ComponentActivity() {
    private var noteText by mutableStateOf("")
    private val selectedImages = mutableStateListOf<Uri>()
    private val pendingAttachments = mutableStateListOf<Uri>()
    private var noteTimestampFormat by mutableStateOf("list_time")
    private var noteAddAnchorIfMissing by mutableStateOf(true)
    private var noteTimestampOrder by mutableStateOf("above")
    private var noteEnterToSave by mutableStateOf(false)

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        selectedImages.addAll(uris)
    }

    private val attachmentPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val fileName = java.net.URLDecoder.decode(it.lastPathSegment ?: "", "UTF-8")
                noteText += "\n[$fileName]($it)"
                pendingAttachments.add(it)
            } catch (_: Exception) {
                noteText += "\n[attachment]($it)"
                pendingAttachments.add(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefillTxt = intent.getStringExtra("prefill_text") ?: ""
        if (prefillTxt.isNotBlank()) noteText = prefillTxt
        val prefs = getSharedPreferences("QuickDaily", 0)
        // Scan existing tags for autocomplete
        val vaultPath = prefs.getString("vault_path", "") ?: ""
        if (vaultPath.isNotBlank()) {
            lifecycleScope.launch(Dispatchers.IO) {
                com.quickdaily.util.TagScanner.getTags(vaultPath)
            }
        }
        noteTimestampFormat = prefs.getString("timestamp_format", "list_time") ?: "list_time"
        noteAddAnchorIfMissing = prefs.getBoolean("add_anchor_if_missing", true)
        noteTimestampOrder = prefs.getString("timestamp_order", "above") ?: "above"
        noteEnterToSave = prefs.getBoolean("enter_to_save", true)
        val dm = resources.displayMetrics
        val w = (dm.widthPixels * 0.88f).toInt()
        val h = (dm.heightPixels * 0.35f).toInt()
        val yOff = (dm.heightPixels * 0.25f).toInt()

        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            val lp = attributes
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.x = 0; lp.y = yOff; lp.width = w; lp.height = h
            lp.dimAmount = 0.0f
            lp.format = android.graphics.PixelFormat.TRANSLUCENT
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
            attributes = lp
        }

        setContent {
            QuickDailyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    CompositionLocalProvider(LocalFloaterColors provides FloaterColors()) {
                        NoteEditDialog(
                        text = noteText,
                        onTextChange = { noteText = it },
                        enterToSave = noteEnterToSave,
                        onSave = {
                    if (hasRealContent(noteText) || selectedImages.isNotEmpty()) appendToDiary(noteText.trim())
                    else finish()
                        },
                        onClose = { finish() },
                        imageUris = selectedImages,
                        onPickImages = {
                imagePicker.launch("image/*")
            },
                       onPickAttachment = {
               attachmentPicker.launch("*/*")
           },

                        onRemoveImage = { index -> selectedImages.removeAt(index) }
                        )
                    }
                }
            }
        }
    }

    private fun appendToDiary(text: String) {
        // 空内容检查：只有任务标记没有实质内容时直接返回
        if (!hasRealContent(text) && selectedImages.isEmpty()) {
            finish()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = getSharedPreferences("QuickDaily", 0)
            val vaultPath = prefs.getString("vault_path", "") ?: ""
            if (vaultPath.isBlank()) { withContext(Dispatchers.Main) { finish() }; return@launch }
            val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
            val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
            val d = DateUtil.todayStr(dateFormat)
            val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/$d.md"
            val anchor = (prefs.getString("anchor_text", "") ?: "").trim()
            // 同时识别 - [ ] 和 - [x] 前缀
            // 同时识别 - [ ] 和 - [x] 前缀（含/不含尾随空格）
            val trimmedText = text.trim()
            val isTask = trimmedText.startsWith("- [ ] ") || trimmedText.startsWith("- [x] ") || trimmedText.startsWith("- [X] ") ||
                trimmedText.startsWith("- [ ]") || trimmedText.startsWith("- [x]") || trimmedText.startsWith("- [X]")
            val line = if (isTask) {
                val wasChecked = trimmedText.startsWith("- [x] ") || trimmedText.startsWith("- [X] ") || trimmedText.startsWith("- [x]") || trimmedText.startsWith("- [X]")
                val taskDesc = trimmedText.let { t ->
                    when {
                        t.startsWith("- [ ] ") -> t.removePrefix("- [ ] ").trim()
                        t.startsWith("- [x] ") -> t.removePrefix("- [x] ").trim()
                        t.startsWith("- [X] ") -> t.removePrefix("- [X] ").trim()
                        t.startsWith("- [ ]") -> t.removePrefix("- [ ]").trim()
                        t.startsWith("- [x]") -> t.removePrefix("- [x]").trim()
                        t.startsWith("- [X]") -> t.removePrefix("- [X]").trim()
                        else -> t
                    }
                }
                val marker = if (wasChecked) "- [x]" else "- [ ]"
                when (noteTimestampFormat) {
                    "none" -> "$marker $taskDesc"
                    "time_only" -> "$marker ${DateUtil.nowTimeStr()} $taskDesc"
                    "time_only_seconds" -> "$marker ${DateUtil.nowTimeSecondsStr()} $taskDesc"
                    "list" -> "$marker $taskDesc"
                    "ordered" -> "$marker $taskDesc"
                    "list_time" -> "$marker ${DateUtil.nowTimeStr()} $taskDesc"
                    "list_time_seconds" -> "$marker ${DateUtil.nowTimeSecondsStr()} $taskDesc"
                    else -> "$marker $taskDesc"
                }
            } else {
                when (noteTimestampFormat) {
                    "none" -> text
                    "time_only" -> "${DateUtil.nowTimeStr()} $text"
                    "time_only_seconds" -> "${DateUtil.nowTimeSecondsStr()} $text"
                    "list" -> "- $text"
                    "ordered" -> "1. $text"
                    "list_time" -> "- ${DateUtil.nowTimeStr()} $text"
                    "list_time_seconds" -> "- ${DateUtil.nowTimeSecondsStr()} $text"
                   else -> text
               }
            }

            var existing = FileUtil.read(path)
            var parsed = ContentUtil.parseFrontmatter(existing)
            var body = if (parsed.hasFrontmatter) parsed.body else existing

            // 文件不存在、为空、或仅有frontmatter（无正文）时，从模板加载
            if (existing.isEmpty() || (parsed.hasFrontmatter && parsed.body.isBlank())) {
                val tplPathPref = prefs.getString("template_path", "") ?: ""
                if (tplPathPref.isNotBlank()) {
                    val tplPath = if (tplPathPref.startsWith("/")) tplPathPref
                    else "${vaultPath.trimEnd('/')}/${tplPathPref}"
                    val tplContent = FileUtil.readOrNull(tplPath)
                    if (tplContent != null && tplContent.isNotEmpty()) {
                        existing = tplContent
                        // Bug4: 重新解析模板内容，使 body 正确更新
                        val reParsed = ContentUtil.parseFrontmatter(existing)
                        body = if (reParsed.hasFrontmatter) reParsed.body else existing
                        parsed = reParsed
                    }
                }
            }

            if (anchor.isNotEmpty() && !body.contains(anchor) && noteAddAnchorIfMissing) {
                body = if (body.isNotEmpty() && !body.endsWith("\n")) {
                    body + "\n$anchor\n"
                } else {
                    body + "$anchor\n"
                }
            }
            val workingContent = body
            // 处理附件（将 content URI 替换为 vault 相对路径）
            var resolvedLine = line
            if (pendingAttachments.isNotEmpty()) {
                val attachStoragePath = prefs.getString("image_storage_path", "") ?: ""
                val attachNamingFormat = prefs.getString("image_naming_format", "timestamp_ext") ?: "timestamp_ext"
                val attachCustomNaming = prefs.getString("image_custom_naming_format", "") ?: ""
                pendingAttachments.toList().forEach { uri ->
                    try {
                        val relPath = com.quickdaily.util.ImageUtil.copyToVault(
                            this@NoteEditActivity, uri, vaultPath,
                            attachStoragePath, attachNamingFormat,
                            "described", attachCustomNaming)
                        if (relPath != null) {
                            resolvedLine = resolvedLine.replace(uri.toString(), relPath)
                        }
                    } catch (_: Exception) { }
                }
                withContext(Dispatchers.Main) { pendingAttachments.clear() }
            }

            // 处理图片（移到 nc 之前，以便图片与文本行一起插入）
            val imageLinks = if (selectedImages.isNotEmpty()) {
                val imgStoragePath = prefs.getString("image_storage_path", "") ?: ""
                val imgNamingFormat = prefs.getString("image_naming_format", "timestamp_ext") ?: "timestamp_ext"
                val imgLinkFormat = prefs.getString("image_link_format", "described") ?: "described"
                val imgCustomNaming = prefs.getString("image_custom_naming_format", "") ?: ""
                ImageUtil.processImages(this@NoteEditActivity, selectedImages.toList(), vaultPath, imgStoragePath, imgNamingFormat, imgLinkFormat, imgCustomNaming)
            } else emptyList()

            // 如果有图片链接，追加到文本行后面（Bug1: 图片与文本在同一位置插入，而非末尾）
            val effectiveLine = if (imageLinks.isNotEmpty()) {
                resolvedLine + "\n" + imageLinks.joinToString("\n")
            } else resolvedLine

            val nc = if (noteTimestampOrder == "below" && anchor.isNotEmpty()) {
                val bodyLines = workingContent.lines().toMutableList()
                val anchorIdx = bodyLines.indexOfFirst { it.trim().contains(anchor.trim()) }
                if (anchorIdx >= 0) {
                    var endIdx = bodyLines.size
                    for (i in (anchorIdx + 1) until bodyLines.size) {
                        val tl = bodyLines[i].trimStart()
                        if (tl.startsWith("# ") || tl.startsWith("## ") || tl.startsWith("### ")) {
                            endIdx = i
                            break
                        }
                    }
                    var lastDash = -1
                    for (i in (anchorIdx + 1) until endIdx) {
                        if (bodyLines[i].trimStart().startsWith("- ")) lastDash = i
                    }
                    val insAt = if (lastDash >= 0) lastDash + 1 else anchorIdx + 1
                    bodyLines.add(insAt, effectiveLine)
                    val nb = bodyLines.joinToString("\n")
                    if (parsed.hasFrontmatter) ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, nb) else nb
                } else {
                    val allLines = workingContent.lines().toMutableList()
                    var lastDashAll = -1
                    for (i in allLines.indices) {
                        if (allLines[i].trimStart().startsWith("- ")) lastDashAll = i
                    }
                    if (lastDashAll >= 0) {
                        allLines.add(lastDashAll + 1, effectiveLine)
                        val nb = allLines.joinToString("\n")
                        if (parsed.hasFrontmatter) ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, nb) else nb
                    } else {
                        if (workingContent.endsWith("\n")) {
                            if (parsed.hasFrontmatter) ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, workingContent + "$effectiveLine\n") else workingContent + "$effectiveLine\n"
                        } else {
                            if (parsed.hasFrontmatter) ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, workingContent + "\n$effectiveLine\n") else workingContent + "\n$effectiveLine\n"
                        }
                    }
                }
            } else if (anchor.isNotEmpty() && workingContent.contains(anchor) && noteTimestampOrder == "above") {
                val idx = workingContent.indexOf(anchor) + anchor.length
                val newBody = workingContent.substring(0, idx) + "\n" + effectiveLine + workingContent.substring(idx)
                if (parsed.hasFrontmatter) {
                    ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, newBody)
                } else {
                    newBody
                }
            } else if (workingContent.isEmpty()) {
                if (parsed.hasFrontmatter) {
                    ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, "$effectiveLine\n")
                } else {
                    "$effectiveLine\n"
                }
            } else if (workingContent.endsWith("\n")) {
                if (parsed.hasFrontmatter) {
                    ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, workingContent + "$effectiveLine\n")
                } else {
                    workingContent + "$effectiveLine\n"
                }
            } else {
                if (parsed.hasFrontmatter) {
                    ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, workingContent + "\n$effectiveLine\n")
                } else {
                    workingContent + "\n$effectiveLine\n"
                }
            }
            // 图片已合并到 resolvedLine-based effectiveLine 中，不再需要 finalNc

            if (selectedImages.isNotEmpty()) {
                withContext(Dispatchers.Main) { selectedImages.clear() }
            }

            FileUtil.write(path, nc)
            WidgetRefreshHelper.refreshAll(this@NoteEditActivity)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@NoteEditActivity, "已保存", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_OUTSIDE) {
            if (hasRealContent(noteText) || selectedImages.isNotEmpty()) {
                    appendToDiary(noteText.trim())
            } else {
                finish()
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}

/**
 * 判断文本是否有实质内容（去除任务标记后仍有内容）。
 * 用于防止仅含 "- [ ] " 前缀的空任务被保存。
 */
private fun cycleHeading(tfv: TextFieldValue): TextFieldValue {
    val text = tfv.text
    val pos = tfv.selection.start
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
    val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
    val cursorPos = lineStart + newLine.length
    return TextFieldValue(newText, TextRange(cursorPos))
}

private fun hasRealContent(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return false
    // 任务标记前缀（同时检查有无尾随空格，因为 .trim() 会去掉末尾空格）
    val taskPrefixes = listOf("- [ ] ", "- [x] ", "- [X] ", "- [ ]", "- [x]", "- [X]")
    for (prefix in taskPrefixes) {
        if (trimmed.startsWith(prefix)) {
            val rest = trimmed.removePrefix(prefix).trim()
            return rest.isNotBlank()
        }
    }
    return true
}

/** 在光标所在行切换任务状态：无标记 → - [ ] → - [x] → 无标记（三态循环） */
private fun taskToggleAtCursor(tfv: TextFieldValue): String {
    val text = tfv.text
    val pos = tfv.selection.start
    val lineStart = text.lastIndexOf('\n', pos - 1) + 1
    val lineEnd = text.indexOf('\n', pos).let { if (it < 0) text.length else it }
    val line = text.substring(lineStart, lineEnd)
    // 使用更精确的匹配 — 支持 - [ ]、- [x]、- [X] 以及前后可能有空格的情况
    val taskRegex = Regex("""^\s*(-\s*\[\s*([ xX])\s*\])\s*""")
    val match = taskRegex.find(line)
    val newLine = if (match != null) {
        val checked = match.groupValues[2] // " " / "x" / "X"
        val rest = line.substring(match.value.length).trimStart()
        if (checked.trim().isEmpty()) {
            // - [ ] → - [x]
            "- [x] $rest".trimStart()
        } else {
            // - [x] → 去除任务标记，只保留文字
            rest
        }
    } else {
        // 无标记 → - [ ]
        "- [ ] $line"
    }
    return text.substring(0, lineStart) + newLine + text.substring(lineEnd)
}

@Composable
private fun NoteEditDialog(
    text: String,
    onTextChange: (String) -> Unit,
    enterToSave: Boolean,
    onSave: () -> Unit,
    onClose: () -> Unit,
    imageUris: SnapshotStateList<Uri>,
    onPickImages: () -> Unit,
    onPickAttachment: () -> Unit,
    onRemoveImage: (Int) -> Unit
) {
    val floater = LocalFloaterColors.current
    val dim = LocalAppDimensions.current
    val focusRequester = remember { FocusRequester() }
    var tfv by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val localUndoStack = remember { mutableStateListOf<String>() }
    val localRedoStack = remember { mutableStateListOf<String>() }
    var lastUndoTime by remember { mutableLongStateOf(0L) }
    val neCtx = LocalContext.current
    val neView = LocalView.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(text) { if (text != tfv.text) tfv = TextFieldValue(text, TextRange(text.length)) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = floater.background,
        shape = RoundedCornerShape(dim.radiusXl),
        shadowElevation = 0.dp
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
                    Box(Modifier.fillMaxWidth()) {
                Text("速记", style = MaterialTheme.typography.labelMedium, color = floater.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
                TextButton(onClick = {
                    onClose()
                }, modifier = Modifier.align(Alignment.CenterEnd)) { Text("关闭", color = floater.onBackgroundVariant, style = MaterialTheme.typography.labelSmall) }
            }



            // ── 内容区（撑满剩余空间，将工具栏推到最下方）──
            Column(Modifier.weight(1f)) {
            // Thumbnail preview
            if (imageUris.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(70.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(imageUris) { index, uri ->
                        Box(modifier = Modifier.size(60.dp)) {
                            val ctx = LocalContext.current
                            val thumbBitmap = remember(uri) {
                                runCatching {
                                    val thumb = ctx.contentResolver.loadThumbnail(
                                        uri, android.util.Size(120, 120), null
                                    )
                                    thumb?.asImageBitmap()
                                }.getOrNull()
                            }
                            thumbBitmap?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            IconButton(
                                onClick = { onRemoveImage(index) },
                                modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
                            ) {
                                Icon(Icons.Default.Close, "删除", tint = Color.Red, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            BasicTextField(value = tfv, onValueChange = { newTfv ->
                    if (enterToSave) {
                        val oldText = tfv.text
                        val newText = newTfv.text
                        val insertedNewlines = newText.count { it == '\n' } - oldText.count { it == '\n' }
                        val lengthDiff = newText.length - oldText.length
                        if (insertedNewlines > 0 && lengthDiff == 1) {
                           onTextChange(oldText)
                           if (oldText.isNotBlank() || imageUris.isNotEmpty()) onSave() else onClose()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastUndoTime > 1500 && oldText != newTfv.text) {
                                localUndoStack.add(oldText)
                                if (localUndoStack.size > 50) localUndoStack.removeAt(0)
                                localRedoStack.clear()
                                lastUndoTime = now
                            }
                            tfv = newTfv
                            onTextChange(newText)
                        }
                    } else {
                        val oldTextNot = tfv.text
                        val now = System.currentTimeMillis()
                        if (now - lastUndoTime > 1500 && oldTextNot != newTfv.text) {
                            localUndoStack.add(oldTextNot)
                            if (localUndoStack.size > 50) localUndoStack.removeAt(0)
                            localRedoStack.clear()
                            lastUndoTime = now
                        }
                        tfv = newTfv
                        onTextChange(newTfv.text)
                    }
                    
            },
                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = floater.onBackground),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("写点什么...", color = floater.onBackgroundDim, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                    inner() })
            } // end content Column (weight)
            var noteTagList by remember { mutableStateOf(emptyList<String>()) }
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val vp = neCtx.getSharedPreferences("QuickDaily", 0).getString("vault_path", "") ?: ""
                    if (vp.isNotBlank()) {
                        noteTagList = com.quickdaily.util.TagScanner.getTags(vp)
                    }
                }
            }

            val tagCompletion = remember(tfv) {
                val text = tfv.text
                val cursor = tfv.selection.start
                if (cursor > 0 && cursor <= text.length) {
                    val before = text.substring(0, cursor)
                    val hi = before.lastIndexOf('#')
                    if (hi >= 0) {
                        val after = before.substring(hi + 1)
                        if (after.isNotEmpty() && after[0] != ' ' && !after.all { it == '#' }) {
                            val p = after.takeWhile { it.isLetterOrDigit() || it == '_' || it == '/' || it == '-' }
                            val wordBefore = hi > 0 && (text[hi - 1].isLetterOrDigit() || text[hi - 1] == '_')
                            if (!wordBefore) {
                                val tagFinished2 = p in noteTagList && (after.length == p.length || after.length > p.length && (!after[p.length].isLetterOrDigit() && after[p.length] != '#'))
                                if (!tagFinished2) {
                                    return@remember Triple(true, p, hi)
                                }
                            }
                        }
                    }
                }
                Triple(false, "", 0)
            }

            val (tagActive2, tagPrefix2, tagHashPos2) = tagCompletion



            val noteMatchingTags = remember(tagActive2, tagPrefix2, noteTagList) {
                if (!tagActive2) emptyList()
                else {
                    val p = tagPrefix2
                    if (p.isEmpty()) noteTagList.take(8)
                    else noteTagList.filter { it.contains(p as CharSequence, ignoreCase = true) }.take(8)
                }
            }

            val noteSelectTag: (String) -> Unit = remember(tagHashPos2) {
                { tag ->
                    val text = tfv.text
                    val cursor = tfv.selection.start
                    val hp = tagHashPos2
                    val needSpaceBefore2 = hp > 0 && text[hp - 1] != ' ' && text[hp - 1] != '\n'
                    val prefix = if (needSpaceBefore2) " #" else "#"
                    val newText = text.substring(0, hp) + prefix + tag + " " + text.substring(cursor)
                    val newCursor = hp + prefix.length + tag.length + 1
                    tfv = TextFieldValue(newText, TextRange(newCursor))
                    onTextChange(newText)
                }
            }

            // ── Tag autocomplete dropdown ──
            if (tagActive2 && noteMatchingTags.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        noteMatchingTags.forEach { tag ->
                            TextButton(
                                onClick = { noteSelectTag(tag) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
                            ) {
                                Text(
                                    "#$tag",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── 底部工具栏（5个按钮，平替之前的 +[图片] 和 +[任务]）──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPickImages, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Image, "图片", tint = floater.primary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {
                    val newText = taskToggleAtCursor(tfv)
                    tfv = TextFieldValue(newText, TextRange(newText.length))
                    onTextChange(newText)
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.CheckBoxOutlineBlank, "任务", tint = floater.primary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {
                    tfv = cycleHeading(tfv)
                    onTextChange(tfv.text)
                }, modifier = Modifier.size(dim.iconXl)) {
                    Text("#", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = floater.primary)
                }
                IconButton(onClick = {
                    val t = tfv.text; val c = tfv.selection.start
                    val ls = t.lastIndexOf('\n', c - 1) + 1
                    val cl = t.substring(ls)
                    val (nt, nc) = if (cl.startsWith("- ")) {
                        t.substring(0, ls) + cl.removePrefix("- ") to (c - 2).coerceAtLeast(ls)
                    } else {
                        t.substring(0, ls) + "- " + cl to c + 2
                    }
                    tfv = TextFieldValue(nt, TextRange(nc))
                    onTextChange(nt)
                }, modifier = Modifier.size(36.dp)) {
                    Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF6EB8FF))
                }
                IconButton(onClick = {
                    val t = tfv.text; val c = tfv.selection.start
                    if (c >= 2 && c + 2 <= t.length && t.substring(c - 2, c) == "**" && t.substring(c, c + 2) == "**") {
                        val nt = t.substring(0, c - 2) + t.substring(c + 2)
                        tfv = TextFieldValue(nt, TextRange(c - 2)); onTextChange(nt)
                    } else {
                        val nt = t.substring(0, c) + "****" + t.substring(c)
                        tfv = TextFieldValue(nt, TextRange(c + 2)); onTextChange(nt)
                    }
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.FormatBold, "加粗", tint = floater.primary, modifier = Modifier.size(20.dp))
                }
                // 撤销
                IconButton(
                    onClick = {
                        if (localUndoStack.isNotEmpty()) {
                            val cur = tfv.text
                            localRedoStack.add(cur)
                            if (localRedoStack.size > 50) localRedoStack.removeAt(0)
                            val prev = localUndoStack.removeAt(localUndoStack.lastIndex)
                            tfv = TextFieldValue(prev, TextRange(prev.length))
                            onTextChange(prev)
                            BetaLogger.log("Toolbar", "undo")
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    enabled = localUndoStack.isNotEmpty()
                ) {
                    Icon(Icons.Default.Undo, "撤销", tint = floater.primary, modifier = Modifier.size(20.dp))
                }
                // 重做
                IconButton(
                    onClick = {
                        if (localRedoStack.isNotEmpty()) {
                            val cur = tfv.text
                            localUndoStack.add(cur)
                            if (localUndoStack.size > 50) localUndoStack.removeAt(0)
                            val next = localRedoStack.removeAt(localRedoStack.lastIndex)
                            tfv = TextFieldValue(next, TextRange(next.length))
                            onTextChange(next)
                            BetaLogger.log("Toolbar", "redo")
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    enabled = localRedoStack.isNotEmpty()
                ) {
                    Icon(Icons.Default.Redo, "重做", tint = floater.primary, modifier = Modifier.size(20.dp))
                }
                // 关闭键盘
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val t = tfv.text
                        if (t.isNotBlank() || imageUris.isNotEmpty()) onSave() else onClose()
                        BetaLogger.log("Toolbar", "save")
                    },
                    modifier = Modifier.size(dim.iconXl)
                ) {
                    Icon(Icons.Default.Check, "保存", tint = floater.primary, modifier = Modifier.size(dim.iconMd))
                }
            } // end toolbar Row
        }
    }
}


