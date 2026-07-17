fp = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
lines = open(fp, "r", encoding="utf-8").readlines()

# Fix 1: "日记配置" is in wrong position (line 502). Remove it and re-insert before the diary folder card
# Find the current insertion
diary_config_line = None
for i, line in enumerate(lines):
    if 'Text("日记配置"' in line:
        diary_config_line = i
        break

if diary_config_line is not None:
    print(f"日记配置 found at line {diary_config_line+1}")
    # Remove the current insertion
    removed = lines.pop(diary_config_line)
    
    # Now find the right insertion point - before the diary folder card
    # Look for 'label = { Text("日记文件夹") }' and insert before it
    for i, line in enumerate(lines):
        if 'label = { Text("日记文件夹") }' in line:
            # Go back to find the start of the Card/Column section
            # We want to insert right before the OutlinedTextField starts
            # Look backward for "value = diaryFolder," which is the first property of the field
            for j in range(i, -1, -1):
                if 'value = diaryFolder' in lines[j]:
                    # Insert before this line
                    lines.insert(j, '        Text("日记配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)\n')
                    print(f"Inserted 日记配置 before line {j+1}")
                    break
            break

# Fix 2: "小部件设置" is in wrong position (after Column()
# Find and move it
widgets_config_line = None
for i, line in enumerate(lines):
    if 'Text("小部件设置"' in line:
        widgets_config_line = i
        break

if widgets_config_line is not None:
    print(f"小部件设置 found at line {widgets_config_line+1}")
    # The line is between Column( and modifier - remove and move to after )
    removed = lines.pop(widgets_config_line)
    
    # Insert after the Column opening brace (after the closing ) of Column modifier)
    for i, line in enumerate(lines):
        if 'verticalArrangement = Arrangement.spacedBy(12.dp)' in line:
            lines.insert(i+1, '        Text("小部件设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)\n')
            print(f"Inserted 小部件设置 after line {i+2}")
            break

open(fp, "w", encoding="utf-8").writelines(lines)
print("Fixed section positions")
