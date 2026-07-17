# -*- coding: utf-8 -*-
# Update version in build.gradle.kts
with open('app/build.gradle.kts', 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('versionName = \"1.5.7-beta\"', 'versionName = \"1.5.8-beta\"')
content = content.replace('versionCode = 34', 'versionCode = 35')
with open('app/build.gradle.kts', 'w', encoding='utf-8') as f:
    f.write(content)

# Update version.json (read with utf-8-sig for BOM)
import json
with open('version.json', 'r', encoding='utf-8-sig') as f:
    v = json.load(f)
v['version'] = '1.5.8-beta'
v['body'] = 'QuickDaily 1.5.8-beta\n- 悬浮窗图片选择器样式同步\n- 工具栏新增附件按钮\n- 从OB配置读取图片链接格式\n- 日志开关+分享按钮联动\n- 修复图片储存目录选择器bug'
with open('version.json', 'w', encoding='utf-8') as f:
    json.dump(v, f, ensure_ascii=False, indent=4)
print('Version updated to 1.5.8-beta')
