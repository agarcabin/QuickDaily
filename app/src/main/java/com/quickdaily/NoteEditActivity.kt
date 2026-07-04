package com.quickdaily

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
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
import com.quickdaily.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.MotionEvent
import android.widget.Toast

class NoteEditActivity : ComponentActivity() {
    private var noteText by mutableStateOf("")
    private var noteAddTimestamp by mutableStateOf(false)
    private var noteEnterToSave by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("QuickDaily", 0)
        noteAddTimestamp = prefs.getBoolean("add_timestamp", true)
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
                            if (noteText.isNotBlank()) appendToDiary(noteText.trim(), noteAddTimestamp)
                            else finish()
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    private fun appendToDiary(text: String, addTimestamp: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = getSharedPreferences("QuickDaily", 0)
            val vaultPath = prefs.getString("vault_path", "") ?: ""
            if (vaultPath.isBlank()) { withContext(Dispatchers.Main) { finish() }; return@launch }
            val diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily"
            val dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD"
            val d = DateUtil.todayStr(dateFormat)
            val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/$d.md"
            val anchor = (prefs.getString("anchor_text", "") ?: "").trim()
            val line = if (addTimestamp) "${DateUtil.nowTimeStr()} $text" else text
            val existing = FileUtil.read(path)
            val nc = if (anchor.isNotEmpty() && existing.contains(anchor)) {
                val idx = existing.indexOf(anchor) + anchor.length
                existing.substring(0, idx) + "\n" + line + existing.substring(idx)
            } else if (existing.isEmpty()) "$line\n"
            else if (existing.endsWith("\n")) "$existing$line\n"
            else "$existing\n$line\n"
            FileUtil.write(path, nc)
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
                appendToDiary(noteText.trim(), noteAddTimestamp)
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
    onClose: () -> Unit
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
