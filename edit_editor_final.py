# -*- coding: utf-8 -*-
filepath = 'app/src/main/java/com/quickdaily/ui/EditorScreen.kt'

with open(filepath, 'rb') as f:
    raw = f.read()
bom = raw[:3]
text = raw.decode('utf-8-sig')

# 1. Add missing imports after Visibility (line 18)
add_after = 'import androidx.compose.material.icons.filled.Visibility'
to_insert = '\n' + 'import androidx.compose.material.icons.filled.Redo\n' + 'import androidx.compose.material.icons.filled.Undo\n' + 'import androidx.compose.material.icons.filled.KeyboardArrowDown\n' + 'import androidx.compose.material.icons.filled.AttachFile'
idx = text.find(add_after) + len(add_after)
text = text[:idx] + to_insert + text[idx:]
assert 'import androidx.compose.material.icons.filled.Undo' in text, 'Undo import failed'

# 2. Add canUndo/canRedo state after config
config_marker = '    val config by appState.config.collectAsState()\r'
idx = text.find(config_marker) + len(config_marker)
undo_redo_states = '\n    val canUndo by appState.canUndo.collectAsState()\n    val canRedo by appState.canRedo.collectAsState()'
text = text[:idx] + undo_redo_states + text[idx:]
assert 'canUndo' in text, 'canUndo state failed'

# 3. Add attachmentPicker after imagePicker
marker = 'appState.onContentChanged(newText)\r'
idx = text.find(marker) + len(marker)
# Skip the 3 closing braces
for _ in range(3):
    nl = text.index('\n', idx)
    brace = text.index('}', nl) + 1
    idx = brace
attach_picker = '''    // \u9644\u4ef6\u9009\u62e9\u5668
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
                val dir = if (storagePath.isBlank()) "" else storagePath.trim("/")
                val destDir = if (dir.isNotEmpty()) vaultPath.trimEnd("/") + "/" + dir else vaultPath.trimEnd("/")
                java.io.File(destDir).mkdirs()
                val displayName = com.quickdaily.util.ImageUtil.getDisplayName(context, uri)
                val ext = com.quickdaily.util.ImageUtil.getExtension(context, uri)
                val fileName = com.quickdaily.util.ImageUtil.generateFileName(config.imageNamingFormat, displayName, ext, config.imageCustomNamingFormat)
                val destFile = java.io.File(destDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                val relativePath = if (dir.isNotEmpty()) dir + "/" + fileName else fileName
                "[" + displayName + ext + "](" + relativePath + ")"
            }
            withContext(Dispatchers.Main) {
                val text = textFieldValue.text
                val cursor = textFieldValue.selection.start
                val newText = text.substring(0, cursor) + link + "\\n" + text.substring(cursor)
                textFieldValue = TextFieldValue(newText, TextRange(cursor + link.length + 1))
                appState.onContentChanged(newText)
            }
        }
    }\n\n'''
nl = text.index('\n', idx)
text = text[:nl+1] + attach_picker + text[nl+1:]
assert 'attachmentPicker' in text, 'attachmentPicker failed'

# 4. Replace bold button closing with full toolbar (attachment + undo + redo + kbd)
old_closing = '                        )\r\n                    }\r\n                }\r\n            }\r\n        }\r\n    ) { padding ->\r'
assert old_closing in text, 'old_closing not found'

new_buttons = '''                        )
                        // \u9644\u4ef6
                        ToolbarIconButton(
                            icon = { Icon(Icons.Default.AttachFile, "\u63d2\u5165\u9644\u4ef6", modifier = Modifier.size(22.dp)) },
                            onClick = { onExternalLaunch(); attachmentPicker.launch("*/*") }
                        )
                        // \u64a4\u9500
                        ToolbarIconButton(
                            icon = { Icon(Icons.Default.Undo, "\u64a4\u9500", modifier = Modifier.size(22.dp)) },
                            onClick = { appState.undo(); BetaLogger.log("Toolbar", "undo") },
                            enabled = canUndo
                        )
                        // \u91cd\u505a
                        ToolbarIconButton(
                            icon = { Icon(Icons.Default.Redo, "\u91cd\u505a", modifier = Modifier.size(22.dp)) },
                            onClick = { appState.redo(); BetaLogger.log("Toolbar", "redo") },
                            enabled = canRedo
                        )
                        // \u5173\u95ed\u952e\u76d8
                        ToolbarIconButton(
                            icon = { Icon(Icons.Default.KeyboardArrowDown, "\u5173\u95ed\u952e\u76d8", modifier = Modifier.size(22.dp)) },
                            onClick = {
                                val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.hideSoftInputFromWindow(view.windowToken, 0)
                                BetaLogger.log("Toolbar", "close_keyboard")
                            }
                        )'''

new_closing = new_buttons + '\r\n                    }\r\n                }\r\n            }\r\n        }\r\n    ) { padding ->\r'
text = text.replace(old_closing, new_closing, 1)
assert '\\u9644\\u4ef6' not in text and 'AttachmentFile' in text or 'AttachFile' in text, 'attachment button replacement might have failed'

# Write back
with open(filepath, 'wb') as f:
    f.write(bom + text.encode('utf-8'))

print('All changes applied successfully!')

# Verify
with open(filepath, 'rb') as f:
    verify = f.read().decode('utf-8-sig')
print('AttachFile import:', 'AttachFile' in verify)
print('attachmentPicker:', 'attachmentPicker' in verify)
print('canUndo:', 'canUndo' in verify)
print('canRedo:', 'canRedo' in verify)
print('Undo button:', 'Icons.Default.Undo' in verify)
print('Redo button:', 'Icons.Default.Redo' in verify)
print('KbdArrow:', 'KeyboardArrowDown' in verify)
