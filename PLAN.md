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

# Plan: QuickDaily 1.9.3-beta 设置页与提示音更新
_Locked via grill - by Codex + user_

## Goal
在保留当前脏工作树已有改动的前提下，完成 1.9.3-beta 设置页的下拉框、锚点文本、Monet、透明度、小部件、路径配置和折叠状态调整；将任务完成提示音替换为可再分发的本地 CC0 音效并支持选择试听；以本地 1.9.2-beta APK 审计设置迁移情况。保持 1.9.3-beta，只做代码、测试和本地构建验证。

## Approach
1. 以 `C:\Download\互传\QuickDaily-1.9.2-beta.apk`、当前 `SettingsScreen.kt`、`DiaryConfig`、SharedPreferences 及主题/小部件偏好为依据，输出设置迁移清单；区分保留、改名、移动、条件隐藏和真正删除。
2. 统一所有下拉菜单的左侧对齐和右侧选中勾；将锚点文本改为带多行输入、取消/保存/重置按钮的弹窗，允许空文本且重置待保存。
3. 调整 Monet 自定义颜色的条件显示、小部件卡片与文案、背景默认值、透明度说明、路径配置标题、其他页顺序和默认展开状态；保留所有既有偏好键。
4. 从 OpenGameArt CC0 UI 音效包中选取短的 Attention/Notification 类素材，记录来源和许可，放入 `res/raw`；保留 `urgent`/`classic`/`silent` 键，统一任务完成播放和设置选择试听。
5. 更新相关策略测试和配置默认值测试，执行 JVM 单测、lint、Debug 构建和 `git diff --check`；不执行 ADB、APK 交接、Git 提交或远端发布。

## Key decisions & tradeoffs
- 1.9.2-beta 是迁移基线；已有用户的 `system` 小部件背景选择不被升级覆盖，新用户缺少偏好时默认使用现有 `dark`/`#202124`。
- 锚点文本允许为空；取消丢弃草稿；重置只修改草稿，保存后才持久化。
- Monet 开启且系统支持时隐藏整个自定义颜色区域；关闭 Monet 后恢复，不删除预设选择。
- 两个透明度滑条统一使用“更透明 — 更深色”。
- 音效只使用来源和许可可确认的本地 CC0 资源，不运行时下载，也不回退到系统音效。

## Risks / open questions
- 本机 `codex.exe` 之前因 Windows Access Denied 无法启动，Act 2 没有产生第二模型 verdict；本次不声称计划获得 `VERDICT: APPROVED`。
- 当前工程没有 Compose UI 测试依赖，因此设置页运行时视觉和试听仍不属于本轮已验证证据。

## Out of scope
- 不覆盖或恢复当前已有未提交文件。
- 不改变既有 SharedPreferences 键名，不提交、推送、打 tag、创建 PR/Release，不复制 APK，不安装或操作 Android 设备。
