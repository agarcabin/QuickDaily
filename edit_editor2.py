# -*- coding: utf-8 -*-
import sys, os

filepath = 'app/src/main/java/com/quickdaily/ui/EditorScreen.kt'

# Read file
with open(filepath, 'rb') as f:
    raw = f.read()

bom = raw[:3]
text = raw.decode('utf-8-sig')

# 1. Add AttachFile import
old = 'import androidx.compose.material.icons.filled.FormatBold'
new = old + '\nimport androidx.compose.material.icons.filled.AttachFile'
assert old in text, 'Import FormatBold not found!'
text = text.replace(old, new, 1)

# 2. Add attachmentPicker after imagePicker  
# Find end of imagePicker by searching for final closing braces after appState.onContentChanged
marker = 'appState.onContentChanged(newText)\n            }\n        }\n    }'
assert marker in text, 'imagePicker end marker not found!'
idx = text.find(marker) + len(marker)
text = text[:idx] + '''\n
    // \u9644\u4ef6\u9009\u62e9\u5668
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
    }

''' + text[idx:]

# 3. Find the undo button by looking for 'enabled = canUndo'
# Then find the closing parens preceding it to insert attachment button
# Pattern: )\\n + comment line + ToolbarIconButton( 
undo_target = 'ToolbarIconButton(\n                            icon = { Icon(Icons.Default.Undo,'
# Find the last occurrence (there's only one)
u_idx = text.find(undo_target)
# Find the closing paren and comment before this
# Look backwards from u_idx
before = text[:u_idx]
last_paren = before.rfind(')')
last_line_start = before.rfind('\n', 0, last_paren) + 1
comment_line_start = before.rfind('\n', 0, last_paren - 1) + 1
comment_line_end = before.find('\n', last_paren + 1)
comment_line = before[last_paren:comment_line_end].rstrip()
# Find what's between the last closing paren and the ToolbarIconButton
between = before[last_paren:]
# The insertion point is at the comment line (the \\\\) after the closing paren
insert_at = last_paren + 1  

attachment_btn = '''                        // \u9644\u4ef6 - \u62c9\u8d77\u5b89\u5353\u9644\u4ef6\u9009\u62e9\u5668
                        ToolbarIconButton(
                            icon = { Icon(Icons.Default.AttachFile, "\u63d2\u5165\u9644\u4ef6", modifier = Modifier.size(dim.iconLg)) },
                            onClick = { onExternalLaunch(); attachmentPicker.launch("*/*") }
                        )
'''

# Find the undo comment line  
u_comment_pos = before.rfind('//')
# Insert attachment button after the closing paren of bold button, before the undo comment
# Go to the end of the closing paren line
closing_paren = before.rfind(')')
closing_paren_line_end = before.find('\\n', closing_paren) + 1
text = text[:closing_paren_line_end] + attachment_btn + text[closing_paren_line_end:]

# Write file
with open(filepath, 'wb') as f:
    f.write(bom + text.encode('utf-8'))

print('Done!')
print('Changes:')
print('1. Added AttachFile import')
print('2. Added attachmentPicker')
print('3. Added attachment button before undo')
