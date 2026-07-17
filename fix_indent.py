path = r'C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    s = line.strip()
    if s.startswith('Button(onClick = onSave'):
        lines[i] = '        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {\n'
    elif s.startswith('Text(') and '保存并返回' in line:
        lines[i] = '            Text("保存并返回")\n'
    elif s == '}' and i > 0 and 'Button' in lines[i-1]:
        lines[i] = '        }\n'
with open(path, 'w', encoding='utf-8') as f:
    f.writelines(lines)
print('Done')
