import re, sys
fp = r'C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt'
with open(fp, 'r', encoding='utf-8') as f:
    text = f.read()
changes = []
crlf = '\r\n'

# ====== 1. Add config + onConfigChange + context to function signature ======
old_sig_end = ') {' + crlf + '    Column('
# Find the DiaryStorageTab function def and add params
old_def = (
    '    onImageStoragePathChange: (String) -> Unit,' + crlf +
    '    onReadObsidianConfig: () -> Unit,' + crlf +
    '    onPickVault: () -> Unit,' + crlf +
    '    onPickTemplate: () -> Unit,' + crlf +
    '    onPickImageStorage: () -> Unit,' + crlf +
    '    onSave: () -> Unit,' + crlf +
    '    vaultEnabled: Boolean,' + crlf +
    ') {' + crlf +
    '    Column('
)

new_def = (
    '    onImageStoragePathChange: (String) -> Unit,' + crlf +
    '    config: DiaryConfig,' + crlf +
    '    onConfigChange: (DiaryConfig) -> Unit,' + crlf +
    '    onReadObsidianConfig: () -> Unit,' + crlf +
    '    onPickVault: () -> Unit,' + crlf +
    '    onPickTemplate: () -> Unit,' + crlf +
    '    onPickImageStorage: () -> Unit,' + crlf +
    '    onSave: () -> Unit,' + crlf +
    '    vaultEnabled: Boolean,' + crlf +
    ') {' + crlf +
    '    val context = LocalContext.current' + crlf +
    '    Column('
)

if old_def in text:
    text = text.replace(old_def, new_def)
    changes.append('1. Added config/onConfigChange/context')
else:
    print('1. NOT FOUND: old_def')
    idx = text.find('onImageStoragePathChange')
    if idx >= 0:
        print('  Context:', repr(text[idx:idx+300]))

# ====== 2. Add config + onConfigChange to call site ======
old_call = (
    '                        onImageStoragePathChange = { imageStoragePath = it },' + crlf +
    '                        onReadObsidianConfig = {'
)

new_call = (
    '                        onImageStoragePathChange = { imageStoragePath = it },' + crlf +
    '                        config = config,' + crlf +
    '                        onConfigChange = { newCfg -> appState.saveConfig(newCfg) },' + crlf +
    '                        onReadObsidianConfig = {'
)

if old_call in text:
    text = text.replace(old_call, new_call)
    changes.append('2. Added config/onConfigChange to call site')
else:
    print('2. NOT FOUND: old_call')

# ====== 3. Add 附件配置 section ======
# Find the save button in DiaryStorageTab and insert before it
insert_before = crlf + crlf + '        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = vaultEnabled)'

attachments = crlf + crlf + (
    '        Text("附件配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)' + crlf +
    '        Card(modifier = Modifier.fillMaxWidth()) {' + crlf +
    '            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {' + crlf +
    '                DropdownSetting(' + crlf +
    '                    label = "图片命名格式",' + crlf +
    '                    selectedKey = config.imageNamingFormat,' + crlf +
    '                    options = namingOptions.map { it.key to it.label },' + crlf +
    '                    onSelect = { onConfigChange(config.copy(imageNamingFormat = it)) }' + crlf +
    '                )' + crlf +
    '                if (config.imageNamingFormat == "custom") {' + crlf +
    '                    OutlinedTextField(' + crlf +
    '                        value = config.imageCustomNamingFormat,' + crlf +
    '                        onValueChange = { onConfigChange(config.copy(imageCustomNamingFormat = it)) },' + crlf +
    '                        label = { Text("自定义命名格式") },' + crlf +
    '                        modifier = Modifier.fillMaxWidth(),' + crlf +
    '                        singleLine = true,' + crlf +
    '                        trailingIcon = {' + crlf +
    '                            IconButton(onClick = { onConfigChange(config.copy(imageCustomNamingFormat = "yyyy-MM-dd_HHmmss_{filename}{ext}")) }) {' + crlf +
    '                                Icon(Icons.Default.Refresh, "\u91cd\u7f6e\u4e3a\u9ed8\u8ba4")' + crlf +
    '                            }' + crlf +
    '                        }' + crlf +
    '                    )' + crlf +
    '                    Spacer(Modifier.height(4.dp))' + crlf +
    '                    Text(' + crlf +
    '                        "\u53ef\u7528\u5360\u4f4d\u7b26\uff08\u70b9\u51fb\u53ef\u590d\u5236\uff09",' + crlf +
    '                        style = MaterialTheme.typography.labelSmall,' + crlf +
    '                        color = MaterialTheme.colorScheme.primary' + crlf +
    '                    )' + crlf +
    '                    Spacer(Modifier.height(4.dp))' + crlf +
    '                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)' + crlf +
    '                    val tokens = listOf(' + crlf +
    '                        "{filename}" to "\u539f\u6587\u4ef6\u540d\uff08\u4e0d\u542b\u6269\u5c55\u540d\uff09",' + crlf +
    '                        "{ext}" to "\u6269\u5c55\u540d\uff08\u5982 .jpg\u3001.mp3\uff09",' + crlf +
    '                        "yyyy" to "\u5e74\u4efd\uff084\u4f4d\uff09",' + crlf +
    '                        "MM" to "\u6708\u4efd\uff082\u4f4d\uff09",' + crlf +
    '                        "dd" to "\u65e5\uff082\u4f4d\uff09",' + crlf +
    '                        "HH" to "\u5c0f\u65f6\uff0824\u5c0f\u65f6\u5236\uff09",' + crlf +
    '                        "mm" to "\u5206\u949f",' + crlf +
    '                        "ss" to "\u79d2\u949f"' + crlf +
    '                    )' + crlf +
    '                    Column {' + crlf +
    '                        tokens.forEach { (token, desc) ->' + crlf +
    '                            Text(' + crlf +
    '                                text = " - ",' + crlf +
    '                                style = MaterialTheme.typography.bodySmall,' + crlf +
    '                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),' + crlf +
    '                                modifier = Modifier' + crlf +
    '                                    .fillMaxWidth()' + crlf +
    '                                    .clickable {' + crlf +
    '                                        try {' + crlf +
    '                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(token, token))' + crlf +
    '                                            android.widget.Toast.makeText(context, "\u5df2\u590d\u5236 ", android.widget.Toast.LENGTH_SHORT).show()' + crlf +
    '                                        } catch (_: Exception) { }' + crlf +
    '                                    }' + crlf +
    '                            )' + crlf +
    '                        }' + crlf +
    '                    }' + crlf +
    '                }' + crlf +
    '                DropdownSetting(' + crlf +
    '                    label = "\u56fe\u7247\u94fe\u63a5\u683c\u5f0f",' + crlf +
    '                    selectedKey = config.imageLinkFormat,' + crlf +
    '                    options = linkOptions,' + crlf +
    '                    onSelect = { onConfigChange(config.copy(imageLinkFormat = it)) }' + crlf +
    '                )' + crlf +
    '                OutlinedTextField(' + crlf +
    '                    value = imageStoragePath,' + crlf +
    '                    onValueChange = onImageStoragePathChange,' + crlf +
    '                    label = { Text("附件储存目录") },' + crlf +
    '                    placeholder = { Text("assets/images（相对仓库路径）") },' + crlf +
    '                    modifier = Modifier.fillMaxWidth(),' + crlf +
    '                    singleLine = true,' + crlf +
    '                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, null, Modifier.size(20.dp)) },' + crlf +
    '                    trailingIcon = {' + crlf +
    '                        IconButton(onClick = onPickImageStorage) {' + crlf +
    '                            Icon(Icons.Default.FolderOpen, "\u9009\u62e9\u6587\u4ef6\u5939")' + crlf +
    '                        }' + crlf +
    '                    }' + crlf +
    '                )' + crlf +
    '                val exampleName = when (config.imageNamingFormat) {' + crlf +
    '                    "original" -> "image.jpg"' + crlf +
    '                    "timestamp_original" -> com.quickdaily.util.DateUtil.nowTimeStr() + "_image.jpg"' + crlf +
    '                    "custom" -> { val f = config.imageCustomNamingFormat.ifEmpty { "image.jpg" }; f.replace("{filename}", "image").replace("{ext}", ".jpg") }' + crlf +
    '                    else -> "image.jpg"' + crlf +
    '                }' + crlf +
    '                Text(' + crlf +
    '                    text = "\u9644\u4ef6\u5b58\u50a8\u8def\u5f84\u793a\u4f8b\uff1a{vaultPath}/{attachmentDir}/",' + crlf +
    '                    style = MaterialTheme.typography.bodySmall,' + crlf +
    '                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)' + crlf +
    '                )' + crlf +
    '            }' + crlf +
    '        }'
)

if insert_before in text:
    text = text.replace(insert_before, attachments + insert_before)
    changes.append('3. Added attachments section')
else:
    print('3. NOT FOUND: insert_before')
    idx = text.find('Button(onClick = onSave')
    if idx >= 0:
        print('  Context:', repr(text[idx:idx+150]))

with open(fp, 'w', encoding='utf-8') as f:
    f.write(text)
print('\\n'.join(changes) if changes else 'No changes')
