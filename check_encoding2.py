# -*- coding: utf-8 -*-
path = r"C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "shareLog" in line:
        # Show this line and the next 3
        for j in range(i, min(i+4, len(lines))):
            print(f"L{j}: {repr(lines[j])}")
        break
