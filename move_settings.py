import re
fp = r'C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt'
with open(fp, 'r', encoding='utf-8') as f:
    text = f.read()
changes = []
crlf = '\r\n'

# === Move image naming/link settings from EditorSettingsTab to DiaryStorageTab ===

# 1. Remove naming/link from EditorSettingsTab
# Find the exact block in EditorSettingsTab
editor_start = text.find('                DropdownSetting(' + crlf + '                    label = "图片命名格式",')
if editor_start >= 0:
    # Find where this block ends - it's before the next section
    end_marker = crlf + '            }' + crlf + '        }' + crlf + crlf + '        Spacer(Modifier.height(8.dp))' + crlf + '        Button(onClick = onSave,'
    block_end = text.find(end_marker, editor_start)
    if block_end >= 0:
        # Remove from editor_start to end of Column+Card (before Spacer)
        print(f'EditorSettingsTab image block found at {editor_start} to {block_end}')
        text = text[:editor_start] + text[block_end:]
        changes.append('1. Removed image settings from EditorSettingsTab')
    else:
        print('End marker not found')
else:
    print('EditorSettingsTab image block not found')

# 2. Remove image storage path from DiaryStorageTab 仓库配置
old_storage = crlf + '                HorizontalDivider()' + crlf + crlf + '                OutlinedTextField(' + crlf + '                    value = imageStoragePath,' + crlf + '                    onValueChange = onImageStoragePathChange,' + crlf + '                    label = { Text("图片储存目录") },' + crlf + '                    placeholder = { Text("assets/images（相对仓库路径）") },' + crlf + '                    modifier = Modifier.fillMaxWidth(),' + crlf + '                    singleLine = true,' + crlf + '                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, null, Modifier.size(20.dp)) },' + crlf + '                    trailingIcon = {' + crlf + '                        IconButton(onClick = onPickImageStorage) {' + crlf + '                            Icon(Icons.Default.FolderOpen, "选择文件夹")' + crlf + '                        }' + crlf + '                    }' + crlf + '                )'
if old_storage in text:
    text = text.replace(old_storage, '')
    changes.append('2. Removed image storage from 仓库配置')
else:
    print('Storage not found')

# 3. Remove 今天的日记路径 card
old_card = crlf + crlf + '        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(' + crlf + '            containerColor = MaterialTheme.colorScheme.surfaceVariant' + crlf + '        )) {' + crlf + '            Column(modifier = Modifier.padding(16.dp)) {' + crlf + '                Text("今天的日记路径", style = MaterialTheme.typography.labelMedium)' + crlf + '                Spacer(Modifier.height(4.dp))' + crlf + '                Text(todayPath.ifEmpty { \"(输入 vault 路径后自动计算)\" },' + crlf + '                    style = MaterialTheme.typography.bodySmall,' + crlf + '                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))' + crlf + '            }' + crlf + '        }'
if old_card in text:
    text = text.replace(old_card, '')
    changes.append('3. Removed 今天的日记路径 card')
else:
    print('Card not found')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(text)
print('\\n'.join(changes) if changes else 'No changes made')
