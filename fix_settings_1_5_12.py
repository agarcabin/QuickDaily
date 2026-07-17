# -*- coding: utf-8 -*-
import re
fp = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
with open(fp, "r", encoding="utf-8") as f:
    text = f.read()

changes = 0

# 1. Fix tab name
if '"日记存储"' in text:
    text = text.replace('"日记存储"', '"路径配置"')
    changes += 1
    print("1. Tab name: 日记存储 -> 路径配置")

# 2. Fix namingOptions
old_naming = ('private val namingOptions = listOf(\n    NamingOption("timestamp_original", "时间戳_原名"),\n    NamingOption("timestamp_ext", "时间戳+扩展名"),\n    NamingOption("original", "保留原名"),\n    NamingOption("custom", "自定义格式"),\n)')
new_naming = ('private val namingOptions = listOf(\n    NamingOption("original", "原名（image.jpg）"),\n    NamingOption("timestamp_original", "时间戳+原名（2026-07-17_120820_image.jpg）"),\n    NamingOption("custom", "自定义名称"),\n)')
if old_naming in text:
    text = text.replace(old_naming, new_naming)
    changes += 1
    print("2. Naming options updated")

# 3. Fix linkOptions
old_links = ('private val linkOptions = listOf(\n    "described" to "Markdown ![](描述)",\n    "obsidian_wikilink" to "Obsidian ![[双向链接]]",\n)')
new_links = ('private val linkOptions = listOf(\n    "described" to "Markdown：![image_name](路径)",\n    "obsidian_wikilink" to "Obsidian：![[image_name]]",\n)')
if old_links in text:
    text = text.replace(old_links, new_links)
    changes += 1
    print("3. Link options updated")

# 4. Replace anchor text OutlinedTextField
old_anchor = '                OutlinedTextField(\n                    value = anchorText,\n                    onValueChange = onAnchorTextChange,\n                    label = { Text("锚点文本") },\n                    placeholder = { Text("## 今日速记") },\n                    modifier = Modifier.fillMaxWidth(),\n                    singleLine = true,\n                    leadingIcon = { Icon(Icons.Default.TextFormat, null, Modifier.size(20.dp)) }\n                )'
new_anchor = '                OutlinedTextField(\n                    value = anchorText,\n                    onValueChange = onAnchorTextChange,\n                    label = { Text("锚点文本") },\n                    placeholder = { Text("## 今日速记") },\n                    modifier = Modifier.fillMaxWidth(),\n                    singleLine = false,\n                    minLines = 1,\n                    trailingIcon = {\n                        IconButton(onClick = { onAnchorTextChange("## 今日速记") }) {\n                            Icon(Icons.Default.Refresh, "重置为默认")\n                        }\n                    }\n                )'
if old_anchor in text:
    text = text.replace(old_anchor, new_anchor)
    changes += 1
    print("4. Anchor text field updated")
else:
    m = re.search(r'锚点文本', text)
    if m:
        start = max(0, m.start()-100)
        end = min(len(text), m.end()+400)
        print("4. NOT FOUND, context:", repr(text[start:end])[:300])

# 5. Replace "无锚点时自动添加" ListItem with Row + timestamp example
old_noanchor = '                ListItem(\n                    headlineContent = { Text("无锚点时自动添加") },\n                    trailingContent = {\n                        Switch(checked = config.addAnchorIfMissing, onCheckedChange = {\n                            onConfigChange(config.copy(addAnchorIfMissing = it))\n                        })\n                    },\n                    modifier = Modifier.heightIn(min = 48.dp)\n                )'
new_noanchor = '                Row(\n                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),\n                    horizontalArrangement = Arrangement.SpaceBetween,\n                    verticalAlignment = Alignment.CenterVertically\n                ) {\n                    Text("无锚点时自动添加", style = MaterialTheme.typography.bodyLarge)\n                    Switch(checked = config.addAnchorIfMissing, onCheckedChange = {\n                        onConfigChange(config.copy(addAnchorIfMissing = it))\n                    })\n                }\n\n                if (config.addAnchorIfMissing || config.timestampFormat != "none") {\n                    Spacer(Modifier.height(4.dp))\n                    Text(\n                        "时间戳示例：",\n                        style = MaterialTheme.typography.labelSmall,\n                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)\n                    )\n                    Spacer(Modifier.height(2.dp))\n                    val previewText = remember(config.timestampFormat, config.addAnchorIfMissing, anchorText) {\n                        val now = com.quickdaily.util.DateUtil.nowTimeStr()\n                        val nowSec = com.quickdaily.util.DateUtil.nowTimeSecondsStr()\n                        buildString {\n                            if (config.addAnchorIfMissing && anchorText.isNotBlank()) {\n                                appendLine(anchorText)\n                            }\n                            when (config.timestampFormat) {\n                                "none" -> append("- 这是一段文本")\n                                "time_only" -> append(" 这是一段文本")\n                                "time_only_seconds" -> append(" 这是一段文本")\n                                "list" -> append("- 这是一段文本")\n                                "ordered" -> append("1. 这是一段文本")\n                                "list_time" -> append("-  这是一段文本")\n                                "list_time_seconds" -> append("-  这是一段文本")\n                                else -> append("- 这是一段文本")\n                            }\n                        }\n                    }\n                    Text(\n                        previewText,\n                        style = MaterialTheme.typography.bodySmall,\n                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)\n                    )\n                }'
if old_noanchor in text:
    text = text.replace(old_noanchor, new_noanchor)
    changes += 1
    print("5. No-anchor row + timestamp example updated")
else:
    m = re.search(r'无锚点时自动添加', text)
    if m:
        start = max(0, m.start()-50)
        end = min(len(text), m.end()+300)
        print("5. NOT FOUND, context:", repr(text[start:end])[:300])

# 6. Fix 回车保存 text
old_enter = '                    headlineContent = { Text("回车保存") },\n                    supportingContent = { Text("在编辑器中按回车键快速保存") },'
new_enter = '                    headlineContent = { Text("回车触发保存") },\n                    supportingContent = { Text("在悬浮窗中按回车键即触发保存。开启后悬浮窗无法多行输入。") },'
if old_enter in text:
    text = text.replace(old_enter, new_enter)
    changes += 1
    print("6. 回车保存 -> 回车触发保存")

# 7. Fix 标签自动补全 text    
old_tag = '                    supportingContent = { Text("输入 # 时提示已有标签") },'
new_tag = '                    supportingContent = { Text("输入#时，自动补全已有标签。开启后会影响启动速度，酌情选择。") },'
if old_tag in text:
    text = text.replace(old_tag, new_tag)
    changes += 1
    print("7. 标签自动补全 text updated")

# 8. Fix save button
old_btn = '        FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {\n            Icon(Icons.Default.Check, null, Modifier.size(18.dp))\n            Spacer(Modifier.width(8.dp))\n            Text("保存设置")\n        }'
new_btn = '        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {\n            Icon(Icons.Default.Check, null, Modifier.size(18.dp))\n            Spacer(Modifier.width(8.dp))\n            Text("保存并返回")\n        }'
if old_btn in text:
    text = text.replace(old_btn, new_btn)
    changes += 1
    print("8. Save button updated")

with open(fp, "w", encoding="utf-8") as f:
    f.write(text)
print(f"\nTotal: {changes} changes applied")
