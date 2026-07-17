fp = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
with open(fp, "r", encoding="utf-8") as f:
    c = f.read()

# 1. Rename tab "日记存储" → "路径配置"
c = c.replace("日记存储", "路径配置")

# 2. Remove image storage section from repo config card
import re
# Find the HorizontalDivider + image storage textfield block
old = "                HorizontalDivider()\n\n                OutlinedTextField(\n                    value = imageStoragePath,\n                    onValueChange = onImageStoragePathChange,\n                    label = { Text(\"图片储存目录\") },\n                    placeholder = { Text(\"assets/images（相对仓库路径）\") },\n                    modifier = Modifier.fillMaxWidth(),\n                    singleLine = true,\n                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, null, Modifier.size(20.dp)) },\n                    trailingIcon = {\n                        IconButton(onClick = onPickImageStorage) {\n                            Icon(Icons.Default.FolderOpen, \"选择文件夹\")\n                        }\n                    }\n                )"
c = c.replace(old, "")

# 3. Add obsidian config path tooltip after the obsidian msg
# Find: "if (obsidianMsg.isNotEmpty()) {" block and add tooltip after it
old_msg = """                if (obsidianMsg.isNotEmpty()) {
                    Text(obsidianMsg,
                        color = if (obsidianDetected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }"""
new_msg = old_msg + """
                if (vaultPath.isNotBlank()) {
                    Text(
                        "Obsidian配置路径：" + vaultPath + "/.obsidian/daily-notes.json",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }"""
c = c.replace(old_msg, new_msg)

# 4. Rename "日记文件配置" → "日记配置" and restructure the card
c = c.replace("Text(\"日记文件配置\"", "Text(\"日记配置\"")

# 5. Change diary folder field: rename label, add picker button
c = c.replace(
    'label = { Text("日记文件夹") }',
    'label = { Text("日记文件夹路径") }'
)

# Add trailingIcon to diary folder field (it currently doesn't have one)
c = c.replace(
    "                    singleLine = true\n                )\n                OutlinedTextField(\n                    value = dateFormat,",
    """                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onPickVault) {
                            Icon(Icons.Default.FolderOpen, "选择文件夹")
                        }
                    }
                )
                OutlinedTextField(
                    value = dateFormat,"""
)

# 6. Remove leading icon from template path field
c = c.replace(
    '                    leadingIcon = { Icon(Icons.Default.Description, null, Modifier.size(20.dp)) },\n                    trailingIcon = {',
    '                    trailingIcon = {'
)

# 7. Add today's path tooltip after template path (before closing Column of diary card)
old_end = """                    trailingIcon = {
                        IconButton(onClick = onPickTemplate) {
                            Icon(Icons.Default.FileOpen, "选择文件")
                        }
                    }
                )
            }
        }"""
new_end = """                    trailingIcon = {
                        IconButton(onClick = onPickTemplate) {
                            Icon(Icons.Default.FileOpen, "选择文件")
                        }
                    }
                )
                if (todayPath.isNotEmpty()) {
                    Text(
                        "今日日记文件路径：" + todayPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }"""
c = c.replace(old_end, new_end)

# 8. Remove the 今天的日记路径 card
old_card = """        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("今天的日记路径", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(todayPath.ifEmpty { "(输入 vault 路径后自动计算)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }

"""
c = c.replace(old_card, "")

# 9. Remove 图片设置 section from EditorSettingsTab
old_pic = """        Text("图片设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownSetting(
                    label = "图片命名格式",
                    selectedKey = config.imageNamingFormat,
                    options = namingOptions.map { it.key to it.label },
                    onSelect = { onConfigChange(config.copy(imageNamingFormat = it)) }
                )
                if (config.imageNamingFormat == "custom") {
                    OutlinedTextField(
                        value = config.imageCustomNamingFormat,
                        onValueChange = { onConfigChange(config.copy(imageCustomNamingFormat = it)) },
                        label = { Text("自定义命名格式") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                DropdownSetting(
                    label = "图片链接格式",
                    selectedKey = config.imageLinkFormat,
                    options = linkOptions,
                    onSelect = { onConfigChange(config.copy(imageLinkFormat = it)) }
                )
            }
        }

"""
c = c.replace(old_pic, "")

# 10. Add 附件配置 section before the save button
old_btn = """        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = vaultEnabled) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("保存并返回")
        }"""
new_attach = """        Text("附件配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownSetting(
                    label = "图片命名格式",
                    selectedKey = config.imageNamingFormat,
                    options = namingOptions.map { it.key to it.label },
                    onSelect = { onConfigChange(config.copy(imageNamingFormat = it)) }
                )
                if (config.imageNamingFormat == "custom") {
                    OutlinedTextField(
                        value = config.imageCustomNamingFormat,
                        onValueChange = { onConfigChange(config.copy(imageCustomNamingFormat = it)) },
                        label = { Text("自定义命名格式") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                DropdownSetting(
                    label = "图片链接格式",
                    selectedKey = config.imageLinkFormat,
                    options = linkOptions,
                    onSelect = { onConfigChange(config.copy(imageLinkFormat = it)) }
                )
                OutlinedTextField(
                    value = imageStoragePath,
                    onValueChange = onImageStoragePathChange,
                    label = { Text("附件储存目录") },
                    placeholder = { Text("assets/images（相对仓库路径）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onPickImageStorage) {
                            Icon(Icons.Default.FolderOpen, "选择文件夹")
                        }
                    }
                )
                if (vaultPath.isNotBlank() && imageStoragePath.isNotBlank()) {
                    Text(
                        "附件储存路径示例：" + vaultPath + "/" + imageStoragePath + "/image.jpg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = vaultEnabled) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("保存并返回")
        }"""
c = c.replace(old_btn, new_attach)

with open(fp, "w", encoding="utf-8") as f:
    f.write(c)

print("All fixes applied successfully")
# Fix ImageUtil.kt smart cast issue
fp1 = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\util\ImageUtil.kt"
with open(fp1, "r", encoding="utf-8") as f:
    c1 = f.read()

old = "    if (name != null && name.contains(\".\")) {\n        return \".\" + name.substringAfterLast(\".\")\n    }"
new = "    val localName = name\n    if (localName != null && localName.contains(\".\")) {\n        return \".\" + localName.substringAfterLast(\".\")\n    }"
c1 = c1.replace(old, new)

with open(fp1, "w", encoding="utf-8") as f:
    f.write(c1)
print("ImageUtil smart cast fixed")
