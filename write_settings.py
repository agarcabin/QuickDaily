# -*- coding: utf-8 -*-
import os
filepath = 'C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt'

content = open(os.path.join(os.path.dirname(__file__), 'SettingsScreen_content.txt'), 'r', encoding='utf-8').read()

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
print('SettingsScreen.kt written from content file')
