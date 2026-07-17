fp = r'C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt'
with open(fp, 'r', encoding='utf-8') as f:
    lines = f.readlines()

missing_imports = [
    'import androidx.compose.material.icons.filled.Widgets',
    'import androidx.compose.material.icons.filled.AddAPhoto',
    'import androidx.compose.material.icons.filled.Shortcut',
    'import androidx.compose.material.icons.filled.EditNote',
    'import androidx.compose.material.icons.filled.Update',
    'import androidx.compose.material.icons.filled.AccessibilityNew',
    'import androidx.compose.material.icons.filled.BugReport',
    'import androidx.compose.material.icons.filled.Description',
    'import androidx.compose.material.icons.filled.Refresh',
    'import androidx.compose.material.icons.filled.PhotoLibrary',
    'import androidx.compose.material.icons.filled.Storage',
    'import androidx.compose.foundation.clickable',
    'import androidx.compose.ui.graphics.asImageBitmap',
]

# Find the last import line
last_import = -1
for i, line in enumerate(lines):
    if line.startswith('import ') or line.startswith('package '):
        last_import = i

# Add missing imports after the last import
existing = ''.join(lines)
to_add = []
for imp in missing_imports:
    if imp + '\n' not in existing:
        to_add.append(imp + '\n')

insert = last_import + 1
result = ''.join(lines[:insert]) + ''.join(to_add) + ''.join(lines[insert:])
with open(fp, 'w', encoding='utf-8') as f:
    f.write(result)
print(f'Added {len(to_add)} missing imports')
