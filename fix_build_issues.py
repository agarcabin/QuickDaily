# Fix remaining build issues in SettingsScreen.kt
fp = r'C:\Users\Ivan\Documents\QuickDaily\app\src\main\java\com\quickdaily\ui\SettingsScreen.kt'
with open(fp, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
dupes = set()
for line in lines:
    # Skip import lines that come from gen_settings.py's own imports
    # Keep only the HEAD imports (already added at top)
    if line.startswith('import com.quickdaily.ui.theme.LocalAppDimensions'):
        if 'LocalAppDimensions' in dupes:
            continue  # Skip duplicate
        dupes.add('LocalAppDimensions')
    elif line.startswith('import com.quickdaily.QuickDailyWidget'):
        continue  # This class doesn't exist
    elif line.startswith('import kotlinx.coroutines.launch'):
        if 'launch' in dupes:
            continue
        dupes.add('launch')
    
    new_lines.append(line)

result = ''.join(new_lines)

# Add missing imports for pager and requestPinShortcut
missing = [
    '\nimport androidx.compose.foundation.pager.HorizontalPager',
    '\nimport androidx.compose.foundation.pager.rememberPagerState',
]
for imp in missing:
    if imp.strip() not in result:
        # Insert after the last import
        last_import = result.rfind('\nimport ')
        insert_at = result.index('\n', last_import + 1)
        result = result[:insert_at] + imp + result[insert_at:]

# Fix @Composable issue - check if SettingsScreen has @Composable annotation
# The gen_settings.py might have split the annotation across lines
result = result.replace('fun SettingsScreen(', '@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)\n@Composable\nfun SettingsScreen(')

# The SettingsScreen function may already have @OptIn but missing @Composable
# Find the SettingsScreen function and ensure it has @Composable
import re
# Check if SettingsScreen already has @Composable
if '@Composable\nfun SettingsScreen' not in result and '@Composable fun SettingsScreen' not in result:
    # Find the function definition and add @Composable before it
    result = result.replace(
        '\nfun SettingsScreen(',
        '\n@Composable\nfun SettingsScreen('
    )

with open(fp, 'w', encoding='utf-8') as f:
    f.write(result)
print('Fixed build issues')
