#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import re

# Step 1: Extract Kotlin code from gen_settings.py (GBK encoding)
raw = open(r'C:\Users\Ivan\Documents\QuickDaily\gen_settings.py', 'r', encoding='gbk').read()
kotlin_lines = []
for line in raw.split('\n'):
    s = line.strip()
    if s.startswith('L(') and s.endswith(')'):
        inner = s[2:-1]
        if inner == '""':
            kotlin_lines.append('')
        elif inner.startswith('"') and inner.endswith('"'):
            content = inner[1:-1]
            # Unescape escaped quotes
            content = content.replace('\\"', '"')
            kotlin_lines.append(content)
kotlin_code = '\n'.join(kotlin_lines)
print(f'Extracted {len(kotlin_lines)} lines of Kotlin code')

# Step 2: Read HEAD SettingsScreen.kt for imports
head = open(r'C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt', 'r', encoding='utf-8-sig').read()
import_lines = []
for hline in head.split('\n'):
    if hline.startswith('package ') or hline.startswith('import '):
        import_lines.append(hline)

# Check what imports the Kotlin code needs but HEAD might not have
extra_imports = []
for imp in [
    'import android.net.Uri',
    'import android.os.Build',
    'import android.os.Environment',
    'import android.provider.Settings',
    'import android.provider.MediaStore',
    'import android.graphics.Bitmap',
    'import android.graphics.BitmapFactory',
    'import java.io.File',
    'import java.io.FileOutputStream',
    'import android.app.Activity',
    'import android.content.ContentValues',
    'import android.content.Intent',
    'import androidx.compose.ui.graphics.toArgb',
    'import androidx.compose.ui.platform.LocalContext',
    'import com.quickdaily.util.UriUtil',
    'import com.quickdaily.util.ShortcutHelper',
    'import kotlinx.coroutines.launch',
    'import com.quickdaily.ui.theme.LocalAppDimensions',
]:
    if imp not in import_lines:
        extra_imports.append(imp)

# Step 3: Combine
all_imports = '\n'.join(import_lines)
if extra_imports:
    all_imports += '\n' + '\n'.join(extra_imports)

result = all_imports + '\n\n' + kotlin_code

# Step 4: Apply fixes
# 4.1 Fix tab name
result = result.replace('"日记存储"', '"路径配置"')

# 4.2 Fix namingOptions
old_naming = ('private val namingOptions = listOf(\n    NamingOption("timestamp_original", "时间戳_原名"),\n    NamingOption("timestamp_ext", "时间戳+扩展名"),\n    NamingOption("original", "保留原名"),\n    NamingOption("custom", "自定义格式"),\n)')
new_naming = ('private val namingOptions = listOf(\n    NamingOption("original", "原名（image.jpg）"),\n    NamingOption("timestamp_original", "时间戳+原名（2026-07-17_120820_image.jpg）"),\n    NamingOption("custom", "自定义名称"),\n)')
result = result.replace(old_naming, new_naming)

# 4.3 Fix linkOptions
old_links = ('private val linkOptions = listOf(\n    "described" to "Markdown ![](描述)",\n    "obsidian_wikilink" to "Obsidian ![[双向链接]]",\n)')
new_links = ('private val linkOptions = listOf(\n    "described" to "Markdown：![image_name](路径)",\n    "obsidian_wikilink" to "Obsidian：![[image_name]]",\n)')
result = result.replace(old_links, new_links)

# 4.4 Fix anchor text OutlinedTextField - remove leadingIcon, add trailingIcon with Refresh, singleLine=false
old_anchor = '                OutlinedTextField(\n                    value = anchorText,\n                    onValueChange = onAnchorTextChange,\n                    label = { Text("锚点文本") },\n                    placeholder = { Text("## 今日速记") },\n                    modifier = Modifier.fillMaxWidth(),\n                    singleLine = true,\n                    leadingIcon = { Icon(Icons.Default.TextFormat, null, Modifier.size(20.dp)) }\n                )'
new_anchor = '                OutlinedTextField(\n                    value = anchorText,\n                    onValueChange = onAnchorTextChange,\n                    label = { Text("锚点文本") },\n                    placeholder = { Text("## 今日速记") },\n                    modifier = Modifier.fillMaxWidth(),\n                    singleLine = false,\n                    minLines = 1,\n                    trailingIcon = {\n                        IconButton(onClick = { onAnchorTextChange("## 今日速记") }) {\n                            Icon(Icons.Default.Refresh, "重置为默认")\n                        }\n                    }\n                )'
result = result.replace(old_anchor, new_anchor)

# 4.5 Replace "无锚点时自动添加" ListItem with Row + timestamp example
old_noanchor = '                ListItem(\n                    headlineContent = { Text("无锚点时自动添加") },\n                    trailingContent = {\n                        Switch(checked = config.addAnchorIfMissing, onCheckedChange = {\n                            onConfigChange(config.copy(addAnchorIfMissing = it))\n                        })\n                    },\n                    modifier = Modifier.heightIn(min = 48.dp)\n                )'
new_noanchor = '                Row(\n                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),\n                    horizontalArrangement = Arrangement.SpaceBetween,\n                    verticalAlignment = Alignment.CenterVertically\n                ) {\n                    Text("无锚点时自动添加", style = MaterialTheme.typography.bodyLarge)\n                    Switch(checked = config.addAnchorIfMissing, onCheckedChange = {\n                        onConfigChange(config.copy(addAnchorIfMissing = it))\n                    })\n                }\n\n                if (config.addAnchorIfMissing || config.timestampFormat != "none") {\n                    Spacer(Modifier.height(4.dp))\n                    Text(\n                        "时间戳示例：",\n                        style = MaterialTheme.typography.labelSmall,\n                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)\n                    )\n                    Spacer(Modifier.height(2.dp))\n                    val previewText = remember(config.timestampFormat, config.addAnchorIfMissing, anchorText) {\n                        val now = com.quickdaily.util.DateUtil.nowTimeStr()\n                        val nowSec = com.quickdaily.util.DateUtil.nowTimeSecondsStr()\n                        buildString {\n                            if (config.addAnchorIfMissing && anchorText.isNotBlank()) {\n                                appendLine(anchorText)\n                            }\n                            when (config.timestampFormat) {\n                                "none" -> append("- 这是一段文本")\n                                "time_only" -> append(" 这是一段文本")\n                                "time_only_seconds" -> append(" 这是一段文本")\n                                "list" -> append("- 这是一段文本")\n                                "ordered" -> append("1. 这是一段文本")\n                                "list_time" -> append("-  这是一段文本")\n                                "list_time_seconds" -> append("-  这是一段文本")\n                                else -> append("- 这是一段文本")\n                            }\n                        }\n                    }\n                    Text(\n                        previewText,\n                        style = MaterialTheme.typography.bodySmall,\n                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)\n                    )\n                }'
result = result.replace(old_noanchor, new_noanchor)

# 4.6 Fix 回车保存 text
old_enter = '                    headlineContent = { Text("回车保存") },\n                    supportingContent = { Text("在编辑器中按回车键快速保存") },'
new_enter = '                    headlineContent = { Text("回车触发保存") },\n                    supportingContent = { Text("在悬浮窗中按回车键即触发保存。开启后悬浮窗无法多行输入。") },'
result = result.replace(old_enter, new_enter)

# 4.7 Fix 标签自动补全 text
old_tag = '                    supportingContent = { Text("输入 # 时提示已有标签") },'
new_tag = '                    supportingContent = { Text("输入#时，自动补全已有标签。开启后会影响启动速度，酌情选择。") },'
result = result.replace(old_tag, new_tag)

# 4.8 Fix save button in EditorSettingsTab
old_btn = '        FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {\n            Icon(Icons.Default.Check, null, Modifier.size(18.dp))\n            Spacer(Modifier.width(8.dp))\n            Text("保存设置")\n        }'
new_btn = '        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {\n            Icon(Icons.Default.Check, null, Modifier.size(18.dp))\n            Spacer(Modifier.width(8.dp))\n            Text("保存并返回")\n        }'
result = result.replace(old_btn, new_btn)

# Step 5: Write the final file
fp = r'C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt'
with open(fp, 'w', encoding='utf-8') as f:
    f.write(result)
print(f'Written {len(result.split(chr(10)))} lines')

# Verify key changes
checks = [
    ('Tab name', '"路径配置"' in result),
    ('Naming options', '时间戳+原名（2026-07-17_120820_image.jpg）' in result),
    ('Link options', 'Markdown：![image_name]' in result),
    ('Anchor text trailingIcon Refresh', 'Icons.Default.Refresh' in result),
    ('Anchor singleLine false', 'singleLine = false' in result),
    ('Anchor minLines 1', 'minLines = 1' in result),
    ('No-anchor Row', 'Row(' in result and '无锚点时自动添加' in result),
    ('Timestamp example', '时间戳示例：' in result),
    ('回车触发保存', '回车触发保存' in result),
    ('标签自动补全', '输入#时，自动补全已有标签' in result),
    ('Button save', 'Button(onClick = onSave, modifier = Modifier.fillMaxWidth())' in result),
    ('保存并返回', '保存并返回' in result),
]
for name, ok in checks:
    print(f'  [{"OK" if ok else "FAIL"}] {name}')
