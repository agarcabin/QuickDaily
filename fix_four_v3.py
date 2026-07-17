import sys, re

def load(path):
    with open(path, "r", encoding="utf-8") as f: return f.read()
def save(path, content):
    with open(path, "w", encoding="utf-8") as f: f.write(content)

tf = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\theme\Theme.kt"
sf = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
ef = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\EditorScreen.kt"

t = load(tf); s = load(sf); e = load(ef)
cr = "\r\n"
changes = []

# 1. Theme: fix pinkish surface/background
if "surface = Color(0xFFFDFBFF)" in t:
    t = t.replace("surface = Color(0xFFFDFBFF)", "surface = Color(0xFFFFFFFF)")
    t = t.replace("background = Color(0xFFFDFBFF)", "background = Color(0xFFF7F8FA)")
    changes.append("1. surface/background: neutral white")
else:
    print("1 NOT MATCHED")

# 2a. Remove old Obsidian button from bottom toolbar
old_btn_start = "// Obsidian 跳转"
idx = e.find(old_btn_start)
if idx >= 0:
    btn_end_marker = cr + "                        )"
    end_idx = e.find(btn_end_marker, idx)
    if end_idx >= 0:
        full_btn = e[idx:end_idx + len(btn_end_marker)]
        e = e.replace(full_btn, "")
        changes.append("2a. Removed old Obsidian button")
    else:
        changes.append("2a partial")
else:
    print("2a NOT MATCHED")

# Also remove the blank line after the removal
e = e.replace(cr + cr + "                        // 附件", cr + "                        // 附件")

# 2b. Add text button to TopAppBar before eye icon
eye_marker = "IconButton(onClick = { showPreview = !showPreview }) {" + cr + "                        Icon(if (showPreview) Icons.Default.Edit else Icons.Default.Visibility,"
new_top = (
    'TextButton(onClick = {' + cr +
    '                            val vaultName = config.vaultPath.trimEnd(\'/\').substringAfterLast(\'/\')' + cr +
    '                            if (vaultName.isNotBlank()) {' + cr +
    '                                try {' + cr +
    '                                    val uri = Uri.parse("obsidian://open?vault=${Uri.encode(vaultName)}")' + cr +
    '                                    val intent = Intent(Intent.ACTION_VIEW, uri)' + cr +
    '                                    context.startActivity(intent)' + cr +
    '                                } catch (e: Exception) {' + cr +
    '                                    Toast.makeText(context, "未安装 Obsidian", Toast.LENGTH_SHORT).show()' + cr +
    '                                }' + cr +
    '                            }' + cr +
    '                        }) {' + cr +
    '                            Text("打开Obsidian",' + cr +
    '                                style = MaterialTheme.typography.labelSmall,' + cr +
    '                                color = MaterialTheme.colorScheme.onPrimary)' + cr +
    '                        }' + cr +
    '                        IconButton(onClick = { showPreview = !showPreview }) {' + cr +
    '                        Icon(if (showPreview) Icons.Default.Edit else Icons.Default.Visibility,'
)
if eye_marker in e:
    e = e.replace(eye_marker, new_top)
    changes.append("2b. TextButton '打开Obsidian' in TopAppBar")
else:
    print("2b NOT MATCHED")

# 3. Timestamp example: always show, remove spacers
old_ts_cond = 'if (config.addAnchorIfMissing || config.timestampFormat != "none") {'
if old_ts_cond in s:
    s = s.replace(old_ts_cond, 'if (config.timestampFormat != "none") {')
    changes.append("3a. Timestamp always shown")
else:
    print("3a NOT MATCHED")

# Remove Spacer before 时间戳示例：
old_spacer = 'Spacer(Modifier.height(4.dp))' + cr + '                    Text(' + cr + '                        "时间戳示例：",'
new_nospacer = 'Text(' + cr + '                        "时间戳示例：",'
if old_spacer in s:
    s = s.replace(old_spacer, new_nospacer)
    changes.append("3b. Removed Spacer before timestamp")
else:
    print("3b NOT MATCHED")

# 4a. Diary folder: add trailingIcon
old_d = '                OutlinedTextField(' + cr + '                    value = diaryFolder,' + cr + '                    onValueChange = onDiaryFolderChange,' + cr + '                    label = { Text("日记文件夹") },' + cr + '                    placeholder = { Text("Daily") },' + cr + '                    modifier = Modifier.fillMaxWidth(),' + cr + '                    singleLine = true' + cr + '                )'
new_d = '                OutlinedTextField(' + cr + '                    value = diaryFolder,' + cr + '                    onValueChange = onDiaryFolderChange,' + cr + '                    label = { Text("日记文件夹路径") },' + cr + '                    placeholder = { Text("Daily") },' + cr + '                    modifier = Modifier.fillMaxWidth(),' + cr + '                    singleLine = true,' + cr + '                    trailingIcon = {' + cr + '                        IconButton(onClick = onPickDiaryFolder) {' + cr + '                            Icon(Icons.Default.FolderOpen, "选择文件夹")' + cr + '                        }' + cr + '                    }' + cr + '                )'
if old_d in s:
    s = s.replace(old_d, new_d)
    changes.append("4a. Diary folder: picker + rename")
else:
    print("4a NOT MATCHED")

# 4b. Add callback param
old_p = '    onPickImageStorage: () -> Unit,'
new_p = '    onPickImageStorage: () -> Unit,' + cr + '    onPickDiaryFolder: () -> Unit,'
if old_p in s:
    s = s.replace(old_p, new_p)
    changes.append("4b. onPickDiaryFolder param")

# 4c. Add to call site
old_c = '                        onPickImageStorage = { onExternalLaunch(); imageStoragePicker.launch(null) },'
new_c = '                        onPickImageStorage = { onExternalLaunch(); imageStoragePicker.launch(null) },' + cr + '                        onPickDiaryFolder = { onExternalLaunch(); diaryFolderPicker.launch(null) },'
if old_c in s:
    s = s.replace(old_c, new_c)
    changes.append("4c. Added to call site")

# 4d. Add launcher
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
if marker in s:
    s = s.replace(marker, picker + marker)
    changes.append("4d. diaryFolderPicker launcher")

save(tf, t); save(sf, s); save(ef, e)
print(" | ".join(changes) if changes else "NONE")
