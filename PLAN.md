# Plan: QuickDaily 4项功能修复与增强
_Locked via grill - by Codex + user_

## Goal
修复锚点位置插入bug，改进首页#按钮为行首循环模式，添加Obsidian跳转按钮，升级版本号为1.5.6-beta。

## Approach
1. **锚点位置修复 (NoteEditActivity.kt)**: 重写appendToDiary()中timestampOrder=="below"的分支逻辑
2. **#按钮循环 (EditorScreen.kt + NoteEditActivity.kt)**: 改行首模式，支持#->##->###->无循环
3. **Obsidian跳转 (AndroidManifest.xml + EditorScreen.kt)**: 添加queries声明，TopAppBar加按钮，Intent打开Obsidian今日页面
4. **版本升级 (version.json + build.gradle.kts)**: 版本号改为1.5.6-beta

## Key decisions & tradeoffs
- **锚点插入位置**: "below"时找区域最后一个-段落，而不是简单追加锚点下方
- **#按钮循环**: 使用4态循环（#->##->###->plain），光标移动到行尾
- **Obsidian跳转**: 使用obsidian:// URI scheme打开今日页面，vault名从vault_path提取
- **queries声明**: Android 11+必须显式声明package查询md.obsidian

## Risks / open questions
- Obsidian URI scheme在不同版本中可能有差异，vault名含特殊字符需编码
- #按钮循环需确保与现有缩进行的兼容性
- 锚点下方若没有-段落但有数字列表，是否也支持？

## Out of scope
- UI样式/布局优化
- 非锚点相关的时间戳格式修改
- 第三方应用集成
