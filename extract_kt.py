import re
raw = open(r"C:\Users\Ivan\Documents\QuickDaily\gen_settings.py", "r", encoding="gbk").read()
lines = raw.split("\n")
kotlin_lines = []
for line in lines:
    stripped = line.strip()
    if stripped.startswith("L(") and stripped.endswith(")"):
        inner = stripped[2:-1]
        if inner == '""':
            kotlin_lines.append("")
        elif inner.startswith('"') and inner.endswith('"'):
            content = inner[1:-1]
            content = content.replace('\\"', '"')
            kotlin_lines.append(content)
result = "\n".join(kotlin_lines)
fp = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
with open(fp, "w", encoding="utf-8") as f:
    f.write(result)
print(f"Generated {len(kotlin_lines)} lines")
