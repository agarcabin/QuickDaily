# Plan: QuickDaily 1.6.2-beta 四项修复
_Locked via grill - by Codex + user_

## Goal
将 QuickDaily 更新到 1.6.2-beta，修复日记模板文件选择器的路径归一化、初始目录与 Markdown 过滤问题；让悬浮速记窗和主编辑器的工具栏 Markdown 文本操作进入撤销/重做历史；移除更新按钮上方多余的分隔线；并把外部 Beta 日志目录统一改为 `Documents`。

## Approach
1. **模板选择器（SettingsScreen.kt）**
   - 用可携带初始 URI 的 `ACTION_OPEN_DOCUMENT` 打开当前 Obsidian vault 目录。
   - MIME 类型优先限制为 Markdown，并在回调中再次检查选中文件名/路径必须以 `.md` 结尾；拒绝其他文件并保留原值。
   - 将选择结果相对于 vault 路径归一化，使用带目录边界的前缀判断，避免把完整路径重复拼接或误判；保留可手动输入相对路径的兼容性。
   - 继续沿用设置页的保存流程，选择器结果写入当前输入状态，点击保存后持久化到 `DiaryConfig`/SharedPreferences。
2. **撤销/重做（NoteEditActivity.kt、EditorScreen.kt、AppState.kt）**
   - 悬浮窗抽取统一的文本变更记录逻辑；输入框变更继续按时间合并，工具栏的任务、标题、列表、加粗、标签补全等 Markdown 变更强制写入一个撤销点并清空重做栈。
   - 主编辑器对图片/附件链接、标签补全和所有 Markdown 工具栏操作使用强制撤销点，避免 1.5 秒防抖吞掉工具栏变更。
   - `AppState` 支持强制撤销点，撤销/重做后触发自动保存，使历史操作既能回退界面内容也能落盘。
3. **更新设置 UI（SettingsScreen.kt）**
   - 删除“启动时自动检查更新”与“检查更新”按钮之间的那条独立 `HorizontalDivider`，保留卡片和其他分组结构。
4. **Beta 日志（BetaLogger.kt）**
   - `init` 与 `configure` 的外部日志目录统一使用 `Environment.DIRECTORY_DOCUMENTS`，即 `/storage/emulated/0/Documents`；内部日志行为不变。
5. **版本与验证**
   - 将 `versionName` 从当前 `1.6.1-beta` 升为 `1.6.2-beta`，`versionCode` 递增为 55，并同步 `version.json`。
   - 运行针对性静态检查、`git diff --check` 与 `:app:assembleRelease`；只在构建成功后交付本地 APK，不进行 GitHub 或 Git 操作。

## Key decisions & tradeoffs
- 选择器采用自定义 `ACTION_OPEN_DOCUMENT` Intent，因为 `OpenDocument` contract 不能同时注入 `DocumentsContract.EXTRA_INITIAL_URI`。
- MIME 过滤和 `.md` 后缀校验双保险：兼容某些文档提供器未正确声明 Markdown MIME，但最终仍不接受非 `.md` 文件。
- 工具栏操作强制独立成撤销点；普通连续输入仍保留防抖，减少每个字符都进入历史造成的噪声。
- 版本码按当前工作区的 `1.6.1-beta`/54 顺序递增为 55；保留工作区已有的三处无关未提交修改。

## Risks / open questions
- 某些第三方文件提供器可能不支持 `EXTRA_INITIAL_URI`，此时选择器会回退到其默认位置，但仍执行 `.md` 校验。
- 物理路径转换依赖当前 `UriUtil` 支持的外部存储路径；非 `primary` 存储仍按现有路径逻辑处理。

## Out of scope
- 不改变模板内容加载格式、Obsidian 配置读取规则或其他设置项的保存语义。
- 不重构附件复制、图片裁剪、小部件和已有的三处未提交修改。
- 不提交、推送、打 tag、创建 GitHub Release 或 Pull Request。
