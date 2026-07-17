# -*- coding: utf-8 -*-
filepath = 'app/src/main/java/com/quickdaily/ui/SettingsScreen.kt'

with open(filepath, 'rb') as f:
    raw = f.read()
bom = raw[:3]
text = raw[3:].decode('utf-8')

# 1. Change first occurrence of imageLinkFormat in DiaryConfig
old = 'imageLinkFormat = config.imageLinkFormat'
new = 'imageLinkFormat = if (appCfg?.useMarkdownLinks == true) \"described\" else config.imageLinkFormat'
assert old in text, 'imageLinkFormat not found'
text = text.replace(old, new, 1)

# 2. Find second occurrence of imageStoragePath assignment in OB config button  
import re
matches = list(re.finditer(r'imageStoragePath = appCfg\.attachmentFolderPath', text))
print(f'Found {len(matches)} matches')
for m in matches:
    print(f'  At position {m.start()}')

# The second one (index 1) is in the button callback (position 12572)
idx = matches[1].start()
end_of_line = text.index('\n', idx)
save_code = '\n                            appState.saveConfig(config.copy(\n                                imageLinkFormat = if (appCfg.useMarkdownLinks) \"described\" else \"obsidian_wikilink\"\n                            ))'
text = text[:end_of_line] + save_code + text[end_of_line:]

with open(filepath, 'wb') as f:
    f.write(bom + text.encode('utf-8'))

print('Done!')
print('useMarkdownLinks:', 'useMarkdownLinks' in text)
