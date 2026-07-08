package com.quickdaily

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.quickdaily.util.DateUtil
import com.quickdaily.util.ContentUtil
import com.quickdaily.util.ImageUtil
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.snapshots.SnapshotStateList
import android.net.Uri
import com.quickdaily.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.MotionEvent
import android.widget.Toast

class NoteEditActivity : ComponentActivity() {
    private var noteText by mutableStateOf("")
    private val selectedImages = mutableStateListOf<Uri>()
    private var noteTimestampFormat by mutableStateOf("list_time")
    private var noteAddAnchorIfMissing by mutableStateOf(true)
    private var noteTimestampOrder by mutableStateOf("above")
    private var noteEnterToSave by mutableStateOf(false)

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        selectedImages.addAll(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("QuickDaily", 0)
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
            MaterialTheme(colorScheme = lightColorScheme(
                background = Color.Transparent,
                surface = Color.Transparent
            )) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    NoteEditDialog(
                        text = noteText,
                        onTextChange = { noteText = it },
                        enterToSave = noteEnterToSave,
                        onSave = {
                    if (noteText.isNotBlank()) appendToDiary(noteText.trim())
                    else finish()
                        },
                        onClose = { finish() },
                        imageUris = selectedImages,
                        onPickImages = { imagePicker.launch("image/*") },
                        onRemoveImage = { index -> selectedImages.removeAt(index) }
                    )
                }
            }
        }
    }

    private fun appendToDiary(text: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = getSharedPreferences("QuickDaily", 0)
            val vaultPath = prefs.getString("vault_path", "") ?: ""
            if (vaultPath.isBlank()) { withContext(Dispatchers.Main) { finish() }; return@launch }
            val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
            val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
            val d = DateUtil.todayStr(dateFormat)
            val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/$d.md"
            val anchor = (prefs.getString("anchor_text", "") ?: "").trim()
            val line = when (noteTimestampFormat) {
                "none" -> text
                "time_only" -> "${DateUtil.nowTimeStr()} $text"
                "time_only_seconds" -> "${DateUtil.nowTimeSecondsStr()} $text"
                "list" -> "- $text"
                "ordered" -> "1. $text"
                "list_time" -> "- ${DateUtil.nowTimeStr()} $text"
                "list_time_seconds" -> "- ${DateUtil.nowTimeSecondsStr()} $text"
               else -> text
           }

            var existing = FileUtil.read(path)
            val parsed = ContentUtil.parseFrontmatter(existing)
            var body = if (parsed.hasFrontmatter) parsed.body else existing

            // 今日文件不存在或为空时，从模板加载
            if (existing.isEmpty()) {
                val tplPathPref = prefs.getString("template_path", "") ?: ""
                if (tplPathPref.isNotBlank()) {
                    val tplPath = if (tplPathPref.startsWith("/")) tplPathPref
                    else "${vaultPath.trimEnd('/')}/${tplPathPref}"
                    val tplContent = FileUtil.readOrNull(tplPath)
                    if (tplContent != null && tplContent.isNotEmpty()) {
                        existing = tplContent
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
            val nc = if (anchor.isNotEmpty() && workingContent.contains(anchor) && noteTimestampOrder == "above") {
                val idx = workingContent.indexOf(anchor) + anchor.length
                val newBody = workingContent.substring(0, idx) + "\n" + line + workingContent.substring(idx)
                if (parsed.hasFrontmatter) {
                    ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, newBody)
                } else {
                    newBody
                }
            } else if (workingContent.isEmpty()) {
                if (parsed.hasFrontmatter) {
                    ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, "$line\n")
                } else {
                    "$line\n"
                }
            } else if (workingContent.endsWith("\n")) {
                if (parsed.hasFrontmatter) {
                    ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, workingContent + "$line\n")
                } else {
                    workingContent + "$line\n"
                }
            } else {
                if (parsed.hasFrontmatter) {
                    ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, workingContent + "\n$line\n")
                } else {
                    workingContent + "\n$line\n"
                }
            }
            val imageLinks = if (selectedImages.isNotEmpty()) {
                val imgStoragePath = prefs.getString("image_storage_path", "") ?: ""
                val imgNamingFormat = prefs.getString("image_naming_format", "timestamp_ext") ?: "timestamp_ext"
                val imgLinkFormat = prefs.getString("image_link_format", "described") ?: "described"
                val imgCustomNaming = prefs.getString("image_custom_naming_format", "") ?: ""
                ImageUtil.processImages(this@NoteEditActivity, selectedImages.toList(), vaultPath, imgStoragePath, imgNamingFormat, imgLinkFormat, imgCustomNaming)
            } else emptyList()

            val finalNc = if (imageLinks.isNotEmpty()) {
                val imagesText = imageLinks.joinToString("\n")
                if (nc.endsWith("\n")) "${nc}${imagesText}\n" else "${nc}\n${imagesText}\n"
            } else nc

            if (selectedImages.isNotEmpty()) {
                withContext(Dispatchers.Main) { selectedImages.clear() }
            }

            FileUtil.write(path, finalNc)
            QuickDailyWidget.updateAllWidgets(this@NoteEditActivity)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@NoteEditActivity, "已保存", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_OUTSIDE) {
            if (noteText.isNotBlank()) {
                    if (noteText.isNotBlank()) appendToDiary(noteText.trim())
            } else {
                finish()
            }
            return true
        }
        return super.onTouchEvent(event)
    }
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
    onRemoveImage: (Int) -> Unit
) {

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xEE1B1B2B),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 0.dp
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onClose) { Text("取消", color = Color(0xFFAAAAAA), fontSize = 13.sp) }
                Text("速记", fontSize = 14.sp, color = Color(0xFFCCCCCC))
                TextButton(onClick = {
                    if (text.isNotBlank()) onSave() else onClose()
                }) { Text("保存", color = Color(0xFF6EB8FF), fontSize = 13.sp) }
            }

            // Image picker button
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPickImages) {
                    Text("+ [图片]", color = Color(0xFF6EB8FF), fontSize = 13.sp)
                }
            }

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

            BasicTextField(value = text, onValueChange = { newText ->
                if (enterToSave) {
                    // 单行模式：过滤掉换行符
                    onTextChange(newText.replace("\n", ""))
                } else {
                    onTextChange(newText)
                }
            },
                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = Color(0xFFEEEEEE)),
                modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(focusRequester),
                singleLine = enterToSave,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = if (enterToSave) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        if (enterToSave && text.isNotBlank()) onSave()
                    }
                ),
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("写点什么...", color = Color(0x66FFFFFF), fontSize = 14.sp)
                    inner()
                })
        }
    }
}
