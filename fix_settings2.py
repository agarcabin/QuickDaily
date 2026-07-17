# Fix SettingsScreen - restructure tabs and sections
fp = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"

with open(fp, "r", encoding="utf-8") as f:
    c = f.read()

# 1. Rename tab
c = c.replace('"日记存储"', '"路径配置"')

# 2. In 仓库配置 card: remove the image storage path section,
#    and add obsidian config path tooltip after the status message
# The image storage path is the OutlinedTextField with label "图片储存目录"
# We need to remove it and its surrounding divider
old_repo = '''                HorizontalDivider()

                OutlinedTextField(
                    value = imageStoragePath,
                    onValueChange = onImageStoragePathChange,
                    label = { Text("图片储存目录") },
                    placeholder = { Text("assets/images（相对仓库路径）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        IconButton(onClick = onPickImageStorage) {
                            Icon(Icons.Default.FolderOpen, "选择文件夹")
                        }
                    }
                )'''

new_repo = '''                if (obsidianMsg.isNotEmpty()) {
                    Text(obsidianMsg,
                        color = if (obsidianDetected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }'''

# Wait, the original code has the obsidianMsg AFTER the FilledTonalButton.
# Let me restructure differently.

# First, let me find the exact block to replace.
# The 仓库配置 card currently has:
# - vault path textfield
# - FilledTonalButton (read obsidian config)
# - obsidianMsg text
# - HorizontalDivider
# - image storage path textfield
#
# I need to change it to:
# - vault path textfield
# - FilledTonalButton (read obsidian config)
# - obsidianMsg text
# - Obsidian config path tooltip text (NEW)
# - (no more divider or image storage path)
print("Planning replacements...")
print("File encoding OK")
