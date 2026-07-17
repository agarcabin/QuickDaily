import sys

def load(path):
    with open(path, 'r', encoding='utf-8') as f: return f.read()
def save(path, content):
    with open(path, 'w', encoding='utf-8') as f: f.write(content)

theme_fp = r'C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\theme\Theme.kt'
settings_fp = r'C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt'
editor_fp = r'C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\EditorScreen.kt'

theme = load(theme_fp)
settings = load(settings_fp)
editor = load(editor_fp)
cr = chr(13) + chr(10)
changes = []

# ==== CHANGE 1: Fix surface/background from pinkish 0xFFFDFBFF to neutral white ====
old_surface = '    surface = Color(0xFFFDFBFF),'
new_surface = '    surface = Color(0xFFFFFFFF),'
old_bg = '    background = Color(0xFFFDFBFF),'
new_bg = '    background = Color(0xFFF7F8FA),'

if old_surface in theme:
    theme = theme.replace(old_surface, new_surface)
    changes.append('1. surface: 0xFFFDFBFF -> pure white')
if old_bg in theme:
    theme = theme.replace(old_bg, new_bg)
    changes.append('1b. background: 0xFFFDFBFF -> 0xFFF7F8FA')

# ==== CHANGE 2: Move Obsidian button to TopAppBar, use text ====
# 2a: Remove old Obsidian button from bottom toolbar
old_btn = (
    '                        // Obsidian 跳转' + cr +
    '                        ToolbarIconButton(' + cr +
    '                            icon = { ObsidianIcon(modifier = Modifier.size(22.dp)) },' + cr +
    '                            onClick = {' + cr +
    '                                val vaultName = config.vaultPath.trimEnd(\'/\').substringAfterLast(\'/\')' + cr +
    '                                if (vaultName.isNotBlank()) {' + cr +
    '                                    try {' + cr +
    '                                        val uri = Uri.parse("obsidian://open?vault=")' + cr +
    '                                        val intent = Intent(Intent.ACTION_VIEW, uri)' + cr +
    '                                        context.startActivity(intent)' + cr +
    '                                    } catch (e: Exception) {' + cr +
    '                                        Toast.makeText(context, "未安装 Obsidian", Toast.LENGTH_SHORT).show()' + cr +
    '                                    }' + cr +
    '                                }' + cr +
    '                            }' + cr +
    '                        )'
)
if old_btn in editor:
    editor = editor.replace(old_btn, '')
    changes.append('2a. Removed old Obsidian button from toolbar')
else:
    print('2a NOT MATCHED')

# 2b: Add text button to TopAppBar before eye icon
eye_marker = (
    '                    IconButton(onClick = { showPreview = !showPreview }) {' + cr +
    '                        Icon(if (showPreview) Icons.Default.Edit else Icons.Default.Visibility,' + cr +
    '                            contentDescription = null)' + cr +
    '                    }'
)
new_top = (
    '                    TextButton(onClick = {' + cr +
    '                        val vaultName = config.vaultPath.trimEnd(\'/\').substringAfterLast(\'/\')' + cr +
    '                        if (vaultName.isNotBlank()) {' + cr +
    '                            try {' + cr +
    '                                val uri = Uri.parse("obsidian://open?vault=")' + cr +
    '                                val intent = Intent(Intent.ACTION_VIEW, uri)' + cr +
    '                                context.startActivity(intent)' + cr +
    '                            } catch (e: Exception) {' + cr +
    '                                Toast.makeText(context, "未安装 Obsidian", Toast.LENGTH_SHORT).show()' + cr +
    '                            }' + cr +
    '                        }' + cr +
    '                    }) {' + cr +
    '                        Text("打开Obsidian",' + cr +
    '                            style = MaterialTheme.typography.labelSmall,' + cr +
    '                            color = MaterialTheme.colorScheme.onPrimary)' + cr +
    '                    }' + cr +
    '                    IconButton(onClick = { showPreview = !showPreview }) {' + cr +
    '                        Icon(if (showPreview) Icons.Default.Edit else Icons.Default.Visibility,' + cr +
    '                            contentDescription = null)' + cr +
    '                    }'
)
if eye_marker in editor:
    editor = editor.replace(eye_marker, new_top)
    changes.append('2b. Added TextButton "打开Obsidian" to TopAppBar')
else:
    print('2b NOT MATCHED')

# ==== CHANGE 3: Timestamp example always show, remove spacers ====
old_ts_block = (
    '                if (config.addAnchorIfMissing || config.timestampFormat != "none") {' + cr +
    '                    Spacer(Modifier.height(4.dp))' + cr +
    '                    Text(' + cr +
    '                        "时间戳示例：",' + cr +
    '                        style = MaterialTheme.typography.labelSmall,' + cr +
    '                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)' + cr +
    '                    )' + cr +
    '                    Spacer(Modifier.height(2.dp))'
)
new_ts_block = (
    '                if (config.timestampFormat != "none") {' + cr +
    '                    Spacer(Modifier.height(4.dp))' + cr +
    '                    Text(' + cr +
    '                        "时间戳示例：",' + cr +
    '                        style = MaterialTheme.typography.labelSmall,' + cr +
    '                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)' + cr +
    '                    )'
)
# First change the condition, then handle the spacers
if old_ts_block in settings:
    settings = settings.replace(old_ts_block, new_ts_block)
    changes.append('3a. Timestamp example: always shown')
else:
    print('3a NOT MATCHED')

# Remove the Spacer at the end of the timestamp example block (before the closing })
old_ts_end = (
    '                        style = MaterialTheme.typography.bodySmall,' + cr +
    '                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)' + cr +
    '                    )' + cr +
    '                }' + cr +
    '            }'
new_ts_end = (
    '                        style = MaterialTheme.typography.bodySmall,' + cr +
    '                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)' + cr +
    '                    )' + cr +
    '                }' + cr +
    '            }'
# Actually the ending is fine, just remove the leading Spacer for the empty line before the if
# Let me remove the empty line before the if block (line 690)
# Find: blank line + '                if (config.addAnchorIfMissing...'
old_blank = cr + cr + '                if (config.addAnchorIfMissing'
new_blank = cr + '                if (config.addAnchorIfMissing'
if old_blank in settings:
    settings = settings.replace(old_blank, new_blank)
    changes.append('3b. Removed blank line before timestamp example')
else:
    print('3b NOT MATCHED')
    
# Also remove the Spacer before the Text("时间戳示例")
# Already handled above in new_ts_block (the Spacer was removed)

# Try to remove the Spacer before the if block
old_spacer_before = cr + '                    Spacer(Modifier.height(4.dp))' + cr + '                    Text(' + cr + '                        "\u65f6\u95f4\u6233\u793a\u4f8b\uff1a",'
# Already handled above

# ==== CHANGE 4: Diary folder picker ====
# 4a: Add trailingIcon to diary folder OutlinedTextField
old_diary = (
    '                OutlinedTextField(' + cr +
    '                    value = diaryFolder,' + cr +
    '                    onValueChange = onDiaryFolderChange,' + cr +
    '                    label = { Text("\u65e5\u8bb0\u6587\u4ef6\u5939") },' + cr +
    '                    placeholder = { Text("Daily") },' + cr +
    '                    modifier = Modifier.fillMaxWidth(),' + cr +
    '                    singleLine = true' + cr +
    '                )'
)
new_diary = (
    '                OutlinedTextField(' + cr +
    '                    value = diaryFolder,' + cr +
    '                    onValueChange = onDiaryFolderChange,' + cr +
    '                    label = { Text("\u65e5\u8bb0\u6587\u4ef6\u5939\u8def\u5f84") },' + cr +
    '                    placeholder = { Text("Daily") },' + cr +
    '                    modifier = Modifier.fillMaxWidth(),' + cr +
    '                    singleLine = true,' + cr +
    '                    trailingIcon = {' + cr +
    '                        IconButton(onClick = onPickDiaryFolder) {' + cr +
    '                            Icon(Icons.Default.FolderOpen, "\u9009\u62e9\u6587\u4ef6\u5939")' + cr +
    '                        }' + cr +
    '                    }' + cr +
    '                )'
)
if old_diary in settings:
    settings = settings.replace(old_diary, new_diary)
    changes.append('4a. Diary folder: added picker, renamed to "日记文件夹路径"')
else:
    print('4a NOT MATCHED')

# 4b: Add onPickDiaryFolder callback to function params
old_param = '    onPickImageStorage: () -> Unit,'
new_param = '    onPickImageStorage: () -> Unit,' + cr + '    onPickDiaryFolder: () -> Unit,'
if old_param in settings:
    settings = settings.replace(old_param, new_param)
    changes.append('4b. Added onPickDiaryFolder param')
else:
    print('4b NOT MATCHED')

# 4c: Add callback to call site
old_call = '                        onPickImageStorage = { onExternalLaunch(); imageStoragePicker.launch(null) },'
new_call = '                        onPickImageStorage = { onExternalLaunch(); imageStoragePicker.launch(null) },' + cr + '                        onPickDiaryFolder = { onExternalLaunch(); diaryFolderPicker.launch(null) },'
if old_call in settings:
    settings = settings.replace(old_call, new_call)
    changes.append('4c. Added onPickDiaryFolder to call site')
else:
    print('4c NOT MATCHED')

# 4d: Add diaryFolderPicker launcher (after imageStoragePicker)
picker = (
    cr + '    val diaryFolderPicker = rememberLauncherForActivityResult(' + cr +
    '        ActivityResultContracts.OpenDocumentTree()' + cr +
    '    ) { uri: Uri? ->' + cr +
    '        uri?.let {' + cr +
    '            val path = com.quickdaily.util.UriUtil.treeUriToPath(it)' + cr +
    '            if (path != null) {' + cr +
    '                diaryFolder = if (vaultPath.isNotBlank() && path.startsWith(vaultPath)) {' + cr +
    '                    path.removePrefix(vaultPath).trimStart(\'/\')' + cr +
    '                } else {' + cr +
    '                    path' + cr +
    '                }' + cr +
    '            }' + cr +
    '        }' + cr +
    '    }' + cr
)
marker = '    val templatePicker = rememberLauncherForActivityResult('
if marker in settings:
    settings = settings.replace(marker, picker + marker)
    changes.append('4d. Added diaryFolderPicker launcher')
else:
    print('4d NOT MATCHED')

save(theme_fp, theme)
save(settings_fp, settings)
save(editor_fp, editor)
print(' | '.join(changes) if changes else 'NONE')
