# -*- coding: utf-8 -*-
filepath = 'app/src/main/java/com/quickdaily/ui/SettingsScreen.kt'

with open(filepath, 'rb') as f:
    raw = f.read()
bom = raw[:3]
text = raw[3:].decode('utf-8')

# 1. Add imageStoragePicker after templatePicker
# Find end of templatePicker (closing paren + 3 closing braces + blank line)
tpl_end = text.find('templatePicker.launch(arrayOf("text/markdown"')
# Find closing of this lambda
line_start = text.rfind('\n', 0, tpl_end)
line_end = text.find('\n', tpl_end)
# Go up to the actual end of templatePicker
end_of_block = text.find('\n    //', line_end)  # // is start of next section comment
if end_of_block < 0:
    end_of_block = text.find('\n\n', line_end)
    
# Insert after the line after templatePicker
insert_pos = text.find('}\n', line_end) + 2

image_storage_picker = """    // \u2014\u2014 SAF \u6587\u4ef6\u5939\u9009\u62e9\u5668\uff08\u56fe\u7247\u50a8\u5b58\u76ee\u5f55\uff09 \u2014\u2014
    val imageStoragePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
            val path = UriUtil.treeUriToPath(it)
            if (path != null) {
                if (vaultPath.isNotBlank() && path.startsWith(vaultPath)) {
                    imageStoragePath = path.removePrefix(vaultPath).trimStart(\"/\")
                } else {
                    imageStoragePath = path
                }
            }
        }
    }

"""

text = text[:insert_pos] + image_storage_picker + text[insert_pos:]

# 2. Change second vaultPicker.launch(null) to imageStoragePicker.launch(null)
import re
matches = list(re.finditer(r'vaultPicker\.launch\(null\)', text))
# Second occurrence is the bug
idx = matches[1].start()
text = text[:idx] + 'imageStoragePicker.launch(null)' + text[idx + len('vaultPicker.launch(null)'):]

with open(filepath, 'wb') as f:
    f.write(bom + text.encode('utf-8'))

print('Done!')
print('imageStoragePicker:', 'imageStoragePicker' in text)
print('Second vaultPicker fixed:', text.find('imageStoragePicker.launch(null)') > 0)
