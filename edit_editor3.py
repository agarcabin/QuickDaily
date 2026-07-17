# -*- coding: utf-8 -*-
filepath = 'app/src/main/java/com/quickdaily/ui/EditorScreen.kt'

with open(filepath, 'rb') as f:
    raw = f.read()
bom = raw[:3]
text = raw.decode('utf-8-sig')

# 1. Add UNDO/Redo/KbdArrow imports after Visibility (line 18)
add_after = 'import androidx.compose.material.icons.filled.Visibility'
to_insert = '\n' + 'import androidx.compose.material.icons.filled.Redo\n' + 'import androidx.compose.material.icons.filled.Undo\n' + 'import androidx.compose.material.icons.filled.KeyboardArrowDown\n' + 'import androidx.compose.material.icons.filled.AttachFile'
assert add_after in text, 'Visibility import not found'
idx = text.find(add_after) + len(add_after)
text = text[:idx] + to_insert + text[idx:]

# 2. Add canUndo/canRedo state after config (line 58)
config_marker = '    val config by appState.config.collectAsState()\r'
assert config_marker in text, 'config state not found'
idx = text.find(config_marker) + len(config_marker)
undo_redo_states = '\n    val canUndo by appState.canUndo.collectAsState()\n    val canRedo by appState.canRedo.collectAsState()'
text = text[:idx] + undo_redo_states + text[idx:]

# 3. Add attachmentPicker after imagePicker
# Find end of imagePicker - appState.onContentChanged with 3 closing braces
marker = 'appState.onContentChanged(newText)'
assert marker in text, 'imagePicker end marker not found'
idx = text.find(marker) + len(marker)
# Skip to end of the 3 closing braces
for _ in range(3):
    idx = text.index('\n', idx + 1)
    idx = text.index('}', idx) + 1
# Go to the empty line after the closing braces
idx = text.index('\n', idx) + 1  
text = text[:idx] + '''    // \u9644\u4ef6\u9009\u62e9\u5668
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
                val newText = text.substring(0, cursor) + link + "\\\\n" + text.substring(cursor)
                textFieldValue = TextFieldValue(newText, TextRange(cursor + link.length + 1))
                appState.onContentChanged(newText)
            }
        }
    }

''' + text[idx:]

# 4. Find the closing paren of bold ToolbarIconButton and the Row closing brace
# We need to: 
# a) Close the bold button  
# b) Add attachment button
# c) Add undo/redo/keyboard buttons
# d) Close the Row

# Find the structure: the Row end is at '                    }' after the bold button
# Bold button ends with a closing ')' then several closing braces for the toolbar/Row/Surface/if/bottomBar

# Look for: ')  // close bold\n                    }\n                }\n            }\n        }\n    ) { padding ->'
# This is the current structure where bold is the last button

# Find the bold button's closing paren and the Row closing
closing_paren = '                        )\n                    }\n                }\n            }\n        }\n    ) { padding ->'
assert closing_paren in text, 'Bold button closing structure not found'

buttons_to_add = '''                        )
                        // \u9500\u9664 - \u62c9\u8d77\u5b89\u5353\u9644\u4ef6\u9009\u62e9\u5668
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

# Replace '                        )\n                    }' (bold close + Row close) 
# with all the new buttons + the bold close + Row close
text = text.replace(closing_paren, buttons_to_add + '\n                    }\n                }\n            }\n        }\n    ) { padding ->')

# Write back
with open(filepath, 'wb') as f:
    f.write(bom + text.encode('utf-8'))

print('Done - all changes applied')
