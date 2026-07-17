fp = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
lines = open(fp, "r", encoding="utf-8").readlines()
new = []
removed = []
for i, line in enumerate(lines):
    # Remove incorrectly placed headers
    prev = lines[i-1] if i > 0 else ""
    nxt = lines[i+1] if i+1 < len(lines) else ""
    
    # 日记配置 is between OutlinedTextField( and value = diaryFolder
    if '日记配置' in line and ('value = diaryFolder' in nxt or 'OutlinedTextField' in prev):
        removed.append((i+1, line.rstrip()))
        continue
    # 附件配置 near 图片设置 - check if it's already correct
    # 小部件设置 was already removed
    
    new.append(line)

open(fp, "w", encoding="utf-8").writelines(new)
for num, text in removed:
    print(f"Removed line {num}: {text}")
print(f"Done. Removed {len(removed)} lines")
