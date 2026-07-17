# -*- coding: utf-8 -*-
import sys

with open('app/src/main/java/com/quickdaily/ui/EditorScreen.kt', 'rb') as f:
    content = f.read()
lines = content.split(b'\n')

# Find lines with Undo and Redo icon references
for i, line in enumerate(lines):
    if b'Undo' in line or b'Redo' in line:
        txt = line.decode('utf-8').rstrip()
        print(f'Line {i+1}: {txt[:90]}')
