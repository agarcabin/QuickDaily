import re

# Fix build.gradle.kts encoding
fp = r"C:\Users\Ivan\Documents\QuickDaily\app\build.gradle.kts"
raw = open(fp, "rb").read()
# Check for UTF-8 corruption
try:
    raw.decode("utf-8")
    print("build.gradle.kts: UTF-8 OK")
except:
    # Try to fix - read as cp1252 and write back as UTF-8
    text = raw.decode("cp1252")
    open(fp, "w", encoding="utf-8").write(text)
    print("build.gradle.kts: Fixed encoding")
    
# Now fix version
raw2 = open(fp, "rb").read()
m = re.search(rb'versionName = "([^"]+)"', raw2)
if m:
    print(f"Version: {m.group(1).decode('utf-8')}")

# Fix SettingsScreen.kt - add missing section headers
fp2 = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
text = open(fp2, "r", encoding="utf-8").read()

changes = 0

# Add "附件配置" before "图片设置"
if "附件配置" not in text and "图片设置" in text:
    text = text.replace(
        'Text("图片设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)',
        'Text("附件配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)\n        Text("图片设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)',
        1
    )
    changes += 1
    print("Added: 附件配置")

# Add "日记配置" - need to find where diary folder section starts
# Look for Text("日记文件夹")
if "日记配置" not in text and "日记文件夹" in text:
    idx = text.find('Text("日记文件夹"')
    if idx > 0:
        # Find the start of the surrounding Column in the card
        # We need to insert before the diary folder Text
        insert = text.rfind('\n', 0, idx) + 1
        text = text[:insert] + '        Text("日记配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)\n' + text[insert:]
        changes += 1
        print("Added: 日记配置")

# Add "小部件设置" in the Widgets tab
if "小部件设置" not in text:
    # Find the WidgetsTab function and add header
    idx = text.find("private fun WidgetsTab(")
    if idx > 0:
        # Add header inside the function body after Column
        body_start = text.find("\n        ) {", idx)
        if body_start > 0:
            insert = text.find("\n", body_start) + 1
            text = text[:insert] + '\n        Text("小部件设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)\n' + text[insert:]
            changes += 1
            print("Added: 小部件设置")

open(fp2, "w", encoding="utf-8").write(text)
print(f"\nTotal changes: {changes}")
