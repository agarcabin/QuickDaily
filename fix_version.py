path = r'C:\Users\Ivan\Documents\QuickDaily\app\build.gradle.kts'
import pathlib
import re
text = pathlib.Path(path).read_text(encoding='utf-8')
text = re.sub(r'versionCode = \d+', 'versionCode = 38', text)
text = re.sub(r'versionName = "([^"]*)"', 'versionName = "1.5.11-beta"', text)
pathlib.Path(path).write_text(text, encoding='utf-8')
print('Updated to 1.5.11-beta')
