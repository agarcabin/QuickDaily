import re
fp = r'C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt'
with open(fp, 'r', encoding='utf-8') as f:
    lines = f.readlines()

changes = []
crlf = '\n'  # lines from readlines() end with \n

# 1. Add config+onConfigChange to function DEF
for i, line in enumerate(lines):
    s = line.rstrip('\r\n')
    if s == '    onImageStoragePathChange: (String) -> Unit,':
        lines.insert(i+1, '    config: DiaryConfig,\n')
        lines.insert(i+2, '    onConfigChange: (DiaryConfig) -> Unit,\n')
        changes.append('Added config/onConfigChange to function params')
        break

# 2. Add context to function body
for i, line in enumerate(lines):
    s = line.rstrip('\r\n')
    if s == ') {' and i > 440 and i < 460:
        lines.insert(i+1, '    val context = LocalContext.current\n')
        changes.append('Added context to function body')
        break

# 3. Add params to call site
for i, line in enumerate(lines):
    s = line.rstrip('\r\n')
    if 'onImageStoragePathChange = { imageStoragePath = it },' in s:
        lines.insert(i+1, '                        config = config,\n')
        lines.insert(i+2, '                        onConfigChange = { newCfg -> appState.saveConfig(newCfg) },\n')
        changes.append('Added config/onConfigChange to call site')
        break

# 4. Remove leftover card skeleton
new_lines = []
for i, line in enumerate(lines):
    s = line.rstrip('\r\n')
    if 'containerColor = MaterialTheme.colorScheme.surfaceVariant' in s:
        # Skip this and the next 4 lines (Card start brace, Column, Column close, blank)
        skip = 4
        j = i + 1
        while j < len(lines) and skip > 0:
            s2 = lines[j].rstrip('\r\n')
            skip -= 1
            j += 1
        changes.append('Removed leftover card skeleton')
        i = j  # won't work for loop, but we track with continue
        continue
    new_lines.append(line)
lines = new_lines

# 5. Add attachments section before save button
for i, line in enumerate(lines):
    s = line.rstrip('\r\n')
    if 'Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = vaultEnabled)' in s:
        # Build attachments section
        att = []
        def al(text):
            att.append(text + '\n')
        al('')
        al('        Text("附件配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)')
        al('        Card(modifier = Modifier.fillMaxWidth()) {')
        al('            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {')
        al('                DropdownSetting(')
        al('                    label = "图片命名格式",')
        al('                    selectedKey = config.imageNamingFormat,')
        al('                    options = namingOptions.map { it.key to it.label },')
        al('                    onSelect = { onConfigChange(config.copy(imageNamingFormat = it)) }')
        al('                )')
        al('                if (config.imageNamingFormat == "custom") {')
        al('                    OutlinedTextField(')
        al('                        value = config.imageCustomNamingFormat,')
        al('                        onValueChange = { onConfigChange(config.copy(imageCustomNamingFormat = it)) },')
        al('                        label = { Text("自定义命名格式") },')
        al('                        modifier = Modifier.fillMaxWidth(),')
        al('                        singleLine = true,')
        al('                        trailingIcon = {')
        al('                            IconButton(onClick = { onConfigChange(config.copy(imageCustomNamingFormat = "yyyy-MM-dd_HHmmss_{filename}{ext}")) }) {')
        al('                                Icon(Icons.Default.Refresh, "重置为默认")')
        al('                            }')
        al('                        }')
        al('                    )')
        al('                    Spacer(Modifier.height(4.dp))')
        al('                    Text(')
        al('                        "可用占位符（点击可复制）",')
        al('                        style = MaterialTheme.typography.labelSmall,')
        al('                        color = MaterialTheme.colorScheme.primary')
        al('                    )')
        al('                    Spacer(Modifier.height(4.dp))')
        al('                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)')
        al('                    val tokens = listOf(')
        al('                        "{filename}" to "原文件名（不含扩展名）",')
        al('                        "{ext}" to "扩展名（如 .jpg、.mp3）",')
        al('                        "yyyy" to "年份（4位）",')
        al('                        "MM" to "月份（2位）",')
        al('                        "dd" to "日（2位）",')
        al('                        "HH" to "小时（24小时制）",')
        al('                        "mm" to "分钟",')
        al('                        "ss" to "秒钟"')
        al('                    )')
        al('                    Column {')
        al('                        tokens.forEach { (token, desc) ->')
        al('                            Text(')
        al('                                text = "$token - $desc",')
        al('                                style = MaterialTheme.typography.bodySmall,')
        al('                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),')
        al('                                modifier = Modifier')
        al('                                    .fillMaxWidth()')
        al('                                    .clickable {')
        al('                                        try {')
        al('                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(token, token))')
        al('                                            android.widget.Toast.makeText(context, "已复制 $token", android.widget.Toast.LENGTH_SHORT).show()')
        al('                                        } catch (_: Exception) { }')
        al('                                    }')
        al('                            )')
        al('                        }')
        al('                    }')
        al('                }')
        al('                DropdownSetting(')
        al('                    label = "图片链接格式",')
        al('                    selectedKey = config.imageLinkFormat,')
        al('                    options = linkOptions,')
        al('                    onSelect = { onConfigChange(config.copy(imageLinkFormat = it)) }')
        al('                )')
        al('                OutlinedTextField(')
        al('                    value = imageStoragePath,')
        al('                    onValueChange = onImageStoragePathChange,')
        al('                    label = { Text("附件储存目录") },')
        al('                    placeholder = { Text("assets/images（相对仓库路径）") },')
        al('                    modifier = Modifier.fillMaxWidth(),')
        al('                    singleLine = true,')
        al('                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, null, Modifier.size(20.dp)) },')
        al('                    trailingIcon = {')
        al('                        IconButton(onClick = onPickImageStorage) {')
        al('                            Icon(Icons.Default.FolderOpen, "选择文件夹")')
        al('                        }')
        al('                    }')
        al('                )')
        al('                val exampleName = when (config.imageNamingFormat) {')
        al('                    "original" -> "image.jpg"')
        al('                    "timestamp_original" -> com.quickdaily.util.DateUtil.nowTimeStr() + "_image.jpg"')
        al('                    "custom" -> { val f = config.imageCustomNamingFormat.ifEmpty { "image.jpg" }; f.replace("{filename}", "image").replace("{ext}", ".jpg") }')
        al('                    else -> "image.jpg"')
        al('                }')
        al('                Text(')
        al('                    text = "附件储存路径示例：{vaultPath}/{attachmentDir}/$exampleName",')
        al('                    style = MaterialTheme.typography.bodySmall,')
        al('                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)')
        al('                )')
        al('            }')
        al('        }')
        lines[i:i] = att
        changes.append('Added attachments section')
        break

with open(fp, 'w', encoding='utf-8') as f:
    f.writelines(lines)

print('Changes:', ' | '.join(changes) if changes else 'NONE')
