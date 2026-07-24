package com.quickdaily

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.quickdaily.util.DiaryAppendUtil
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
class NoteEditActivity : ComponentActivity() {
    companion object {
        const val EXTRA_RETURN_TO_HOME = "return_to_home"
    }

    private var noteText by mutableStateOf("")
    private val selectedImages = mutableStateListOf<Uri>()
    private val pendingAttachments = mutableStateListOf<Uri>()
    private var noteTimestampFormat by mutableStateOf("list_time")
    private var noteAddAnchorIfMissing by mutableStateOf(true)
    private var noteTimestampOrder by mutableStateOf("above")
    private var noteSaveInProgress = false
    private var noteEnterToSave by mutableStateOf(false)
    private var returnToHomeAfterClose = false

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        selectedImages.addAll(uris)
    }

    private val attachmentPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val fileName = ImageUtil.getDisplayName(this, it).ifBlank { "attachment" }
                noteText += "\n![[${it}]]"
                pendingAttachments.add(it)
                BetaLogger.log("NoteEdit", "attachment selected name=$fileName uri=$it count=${pendingAttachments.size}")
            } catch (_: Exception) {
                noteText += "\n![[${it}]]"
                pendingAttachments.add(it)
                BetaLogger.log("NoteEdit", "attachment selected without persisted permission uri=$it count=${pendingAttachments.size}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        returnToHomeAfterClose = intent.getBooleanExtra(EXTRA_RETURN_TO_HOME, false)
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
                    if (hasRealContent(noteText) || selectedImages.isNotEmpty() || pendingAttachments.isNotEmpty()) appendToDiary(noteText.trim())
                    else finishEditor()
                        },
                        onClose = { finishEditor() },
                        onHome = {
                            startActivity(Intent(this@NoteEditActivity, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            })
                            finish()
                        },
                        imageUris = selectedImages,
                        hasAttachments = pendingAttachments.isNotEmpty(),
                        attachmentUris = pendingAttachments,
                        onPickImages = {
                imagePicker.launch("image/*")
            },
                       onPickAttachment = {
               attachmentPicker.launch(arrayOf("*/*"))
           },

                        onRemoveImage = { index -> selectedImages.removeAt(index) }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleInstance 会复用当前编辑器。不要重新初始化 noteText，避免重复点击入口时丢草稿。
        if (intent.hasExtra(EXTRA_RETURN_TO_HOME)) {
            returnToHomeAfterClose = intent.getBooleanExtra(EXTRA_RETURN_TO_HOME, false)
        }
        BetaLogger.log("Lifecycle", "NoteEditActivity reused")
    }

    private fun finishEditor() {
        if (returnToHomeAfterClose) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            })
        }
        finish()
    }

    private fun appendToDiary(text: String) {
        if (noteSaveInProgress) return
        if (!hasRealContent(text) && selectedImages.isEmpty() && pendingAttachments.isEmpty()) {
            finishEditor()
            return
        }
        noteSaveInProgress = true
        lifecycleScope.launch {
            when (val result = FloatingNoteSaveUseCase(this@NoteEditActivity).save(
                text,
                selectedImages.toList(),
                pendingAttachments.toList()
            )) {
                FloatingNoteSaveResult.Saved -> {
                    selectedImages.clear()
                    pendingAttachments.clear()
                    Toast.makeText(this@NoteEditActivity, "已保存", Toast.LENGTH_SHORT).show()
                    finishEditor()
                }
                FloatingNoteSaveResult.NoContent -> finishEditor()
                is FloatingNoteSaveResult.Failed -> {
                    noteSaveInProgress = false
                    BetaLogger.log("NoteEdit", "save failed=${result.message}")
                    Toast.makeText(this@NoteEditActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_OUTSIDE) {
            if (hasRealContent(noteText) || selectedImages.isNotEmpty() || pendingAttachments.isNotEmpty()) {
                    appendToDiary(noteText.trim())
            } else {
                finishEditor()
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

internal fun hasRealContent(text: String): Boolean {
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
fun NoteEditDialog(
    text: String,
    onTextChange: (String) -> Unit,
    enterToSave: Boolean,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onHome: () -> Unit,
    imageUris: List<Uri>,
    hasAttachments: Boolean,
    attachmentUris: List<Uri>,
    onPickImages: () -> Unit,
    onPickAttachment: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onTiming: (stage: String, detail: String?) -> Unit = { _, _ -> }
) {
    val floater = LocalFloaterColors.current
    val dim = LocalAppDimensions.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    var focusRequested by remember { mutableStateOf(false) }
    var imeShowRequested by remember { mutableStateOf(false) }
    var tfv by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val localUndoStack = remember { mutableStateListOf<String>() }
    val localRedoStack = remember { mutableStateListOf<String>() }
    var lastUndoTime by remember { mutableLongStateOf(0L) }
    val neCtx = LocalContext.current
    val neView = LocalView.current

    fun recordUndo(previousText: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        localRedoStack.clear()
        if (force || now - lastUndoTime > 1500) {
            localUndoStack.add(previousText)
            if (localUndoStack.size > 50) localUndoStack.removeAt(0)
            lastUndoTime = if (force) 0L else now
        }
    }

    fun applyTextChange(newValue: TextFieldValue, forceUndo: Boolean = false) {
        val oldText = tfv.text
        if (oldText == newValue.text) {
            tfv = newValue
            return
        }
        recordUndo(oldText, forceUndo)
        tfv = newValue
        onTextChange(newValue.text)
    }

    SideEffect {
        if (!focusRequested) {
            focusRequested = true
            onTiming("focus_request", null)
            focusRequester.requestFocus()
        }
    }
    LaunchedEffect(density, imeInsets) {
        snapshotFlow { imeInsets.getBottom(density) > 0 }
            .filter { it }
            .take(1)
            .collect { onTiming("ime_visible", null) }
    }
    LaunchedEffect(text) { if (text != tfv.text) tfv = TextFieldValue(text, TextRange(text.length)) }
    val tagVaultPath = neCtx.getSharedPreferences("QuickDaily", 0).getString("vault_path", "") ?: ""
    var noteTagList by remember(tagVaultPath) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(tagVaultPath) {
        noteTagList = if (tagVaultPath.isBlank()) emptyList() else withContext(Dispatchers.IO) {
            com.quickdaily.util.TagScanner.getTags(tagVaultPath)
        }
    }
    val tagCompletion = remember(tfv.text, tfv.selection, noteTagList) {
        val currentText = tfv.text
        val cursor = tfv.selection.start
        if (cursor > 0 && cursor <= currentText.length) {
            val before = currentText.substring(0, cursor)
            val hashPos = before.lastIndexOf('#')
            if (hashPos >= 0) {
                val after = before.substring(hashPos + 1)
                val wordBefore = hashPos > 0 && (currentText[hashPos - 1].isLetterOrDigit() || currentText[hashPos - 1] == '_')
                if (!wordBefore && (after.isEmpty() || (after[0] != ' ' && !after.all { it == '#' }))) {
                    val prefix = after.takeWhile { it.isLetterOrDigit() || it == '_' || it == '/' || it == '-' }
                    val finished = prefix in noteTagList && (after.length == prefix.length || after.length > prefix.length && (!after[prefix.length].isLetterOrDigit() && after[prefix.length] != '#'))
                    if (!finished) return@remember Triple(true, prefix, hashPos)
                }
            }
        }
        Triple(false, "", 0)
    }
    val (tagActive2, tagPrefix2, tagHashPos2) = tagCompletion
    val noteMatchingTags = remember(tagActive2, tagPrefix2, noteTagList) {
        if (!tagActive2) emptyList() else if (tagPrefix2.isEmpty()) {
            val recent = com.quickdaily.util.RecentTags.get(neCtx)
            (recent + noteTagList.filterNot { it in recent }).take(3)
        } else noteTagList.filter { it.contains(tagPrefix2, ignoreCase = true) }.take(8)
    }
    val noteSelectTag: (String) -> Unit = remember(tagHashPos2) {
        { tag ->
            val currentText = tfv.text
            val cursor = tfv.selection.start
            val needSpaceBefore = tagHashPos2 > 0 && currentText[tagHashPos2 - 1] != ' ' && currentText[tagHashPos2 - 1] != '\n'
            val prefix = if (needSpaceBefore) " #" else "#"
            val newText = currentText.substring(0, tagHashPos2) + prefix + tag + " " + currentText.substring(cursor)
            val newCursor = tagHashPos2 + prefix.length + tag.length + 1
            applyTextChange(TextFieldValue(newText, TextRange(newCursor)), forceUndo = true)
            com.quickdaily.util.RecentTags.record(neCtx, tag)
        }
    }

    fun saveOrClose() {
        onTiming("enter_action", "hasTagCandidates=${noteMatchingTags.isNotEmpty()}")
        if (noteMatchingTags.isNotEmpty()) {
            noteSelectTag(noteMatchingTags.first())
        } else if (tfv.text.isNotBlank() || imageUris.isNotEmpty() || hasAttachments) {
            onSave()
        } else {
            onClose()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = floater.background,
        shape = RoundedCornerShape(dim.radiusXl),
        shadowElevation = 0.dp
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
                    Box(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        onHome()
                    },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Text("首页", color = floater.primary, style = MaterialTheme.typography.labelSmall)
                }
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
            if (attachmentUris.isNotEmpty()) {
                Text(
                    text = "已添加附件：${attachmentUris.size} 个",
                    style = MaterialTheme.typography.labelSmall,
                    color = floater.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            BasicTextField(value = tfv, onValueChange = { newTfv ->
                    val oldText = tfv.text
                    val newText = newTfv.text
                    val insertedNewlines = newText.count { it == '\n' } - oldText.count { it == '\n' }
                    if (enterToSave && insertedNewlines > 0 && newText.length == oldText.length + 1) {
                        // IMEs that commit Enter as a newline still use the same explicit action.
                        saveOrClose()
                    } else {
                        if (oldText != newText) recordUndo(oldText)
                        tfv = newTfv
                        onTextChange(newText)
                    }
            },
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (enterToSave) saveOrClose() }),
                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = floater.onBackground),
                cursorBrush = SolidColor(FloatingCursorPolicy.colorFor(floater.background)),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            onTiming("focus_acquired", null)
                            if (!imeShowRequested) {
                                imeShowRequested = true
                                onTiming("ime_show_request", null)
                                keyboardController?.show()
                            }
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (enterToSave &&
                            event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            saveOrClose()
                            true
                        } else {
                            false
                        }
                    },
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("写点什么...", color = floater.onBackgroundDim, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                    inner() })
            } // end content Column (weight)
            // ── Tag autocomplete row ──
            if (tagActive2 && noteMatchingTags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(noteMatchingTags, key = { _, tag -> tag }) { _, tag ->
                        TextButton(
                            onClick = { noteSelectTag(tag) },
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("#$tag", style = MaterialTheme.typography.bodySmall, color = floater.primary)
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
                    applyTextChange(TextFieldValue(newText, TextRange(newText.length)), forceUndo = true)
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.CheckBoxOutlineBlank, "任务", tint = floater.primary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {
                    applyTextChange(cycleHeading(tfv), forceUndo = true)
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
                    applyTextChange(TextFieldValue(nt, TextRange(nc)), forceUndo = true)
                }, modifier = Modifier.size(36.dp)) {
                    Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = floater.primary)
                }
                IconButton(onClick = {
                    val t = tfv.text; val c = tfv.selection.start
                    if (c >= 2 && c + 2 <= t.length && t.substring(c - 2, c) == "**" && t.substring(c, c + 2) == "**") {
                        val nt = t.substring(0, c - 2) + t.substring(c + 2)
                        applyTextChange(TextFieldValue(nt, TextRange(c - 2)), forceUndo = true)
                    } else {
                        val nt = t.substring(0, c) + "****" + t.substring(c)
                        applyTextChange(TextFieldValue(nt, TextRange(c + 2)), forceUndo = true)
                    }
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.FormatBold, "加粗", tint = floater.primary, modifier = Modifier.size(20.dp))
                }
                // 附件
                IconButton(onClick = onPickAttachment, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.AttachFile, "附件", tint = floater.primary, modifier = Modifier.size(20.dp))
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
                            lastUndoTime = 0L
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
                            lastUndoTime = 0L
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
                        if (t.isNotBlank() || imageUris.isNotEmpty() || hasAttachments) onSave() else onClose()
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


