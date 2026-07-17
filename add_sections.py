fp = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
text = open(fp, "r", encoding="utf-8").read()

changes = 0

# Add "附件配置" before "图片设置"
if "附件配置" not in text:
    old = 'Text("图片设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)'
    new = 'Text("附件配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)\n        Text("图片设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)'
    if old in text:
        text = text.replace(old, new, 1)
        changes += 1
        print("Added: 附件配置")

# Add "日记配置" before diary folder section
if "日记配置" not in text and "日记文件夹" in text:
    idx = text.find('Text("日记文件夹"')
    # Find end of previous block and insert header
    prev_block_end = text.rfind("\n", 0, idx - 50)
    if prev_block_end > 0:
        insert_pos = text.find("\n", prev_block_end) + 1
        text = text[:insert_pos] + '        Text("日记配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)\n' + text[insert_pos:]
        changes += 1
        print("Added: 日记配置")

# Add "小部件设置" in WidgetsTab
if "小部件设置" not in text:
    idx = text.find("private fun WidgetsTab(")
    if idx > 0:
        body = text.find("Column(", idx)
        if body > 0:
            col_start = text.find("\n", body) + 1
            text = text[:col_start] + '        Text("小部件设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)\n' + text[col_start:]
            changes += 1
            print("Added: 小部件设置")

open(fp, "w", encoding="utf-8").write(text)
print(f"\nTotal changes: {changes}")
