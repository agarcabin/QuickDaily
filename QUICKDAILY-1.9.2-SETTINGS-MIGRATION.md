# QuickDaily 1.9.2-beta → 1.9.3-beta 设置迁移清单

## 审计基线与证据

- 基线 APK：`C:\Download\互传\QuickDaily-1.9.2-beta.apk`
- `aapt2 dump badging`：`package=com.quickdaily`、`versionCode=74`、`versionName=1.9.2-beta`
- 基线 SHA-256：`A9D3037BA86A574D50C5DA6EECD1BC6C39763A9DC4D7E957E6FF9E319459FD0D`
- APK 证据：只读提取 `classes.dex`，检查其中的设置文案和 SharedPreferences 键；没有安装 APK、连接设备或执行真机 UI 验收。
- 当前代码证据：`app/src/main/java/com/quickdaily/ui/SettingsScreen.kt`、`app/src/main/java/com/quickdaily/AppState.kt`、`app/src/main/java/com/quickdaily/WidgetAppearance.kt`、`app/src/main/java/com/quickdaily/ui/theme/Theme.kt` 及相关策略类。

DEX 字符串可以确认基线编译进了哪些设置文案和键，但不能单独证明运行时的视觉顺序。因此“位置/默认展开状态”以当前 `SettingsScreen.kt` 的实际调用顺序为准。

## 仍存在且功能与持久化保持

以下设置仍有对应的当前读取/保存路径，旧用户的偏好键没有改名：

- 路径配置中的仓库路径、Obsidian 配置文件、日记文件夹、日期格式、模板路径、附件目录、图片命名格式和图片链接格式；对应 `vault_path`、`obsidian_config_uri`、`use_custom_obsidian_config_path`、`diary_folder`、`date_format`、`template_path`、`image_storage_path`、`image_naming_format`、`image_link_format`、`image_custom_naming_format`。
- 编辑器中的 Frontmatter 过滤、时间戳格式、时间戳插入顺序、无锚点时自动添加、双链和标签索引刷新；`anchor_text` 也继续读取和保存，只是编辑入口改成弹窗。
- 悬浮窗的回车触发保存、保存后拉起 Obsidian、退出时保存草稿、系统侧边启动器支持和悬浮窗透明度。保存后拉起 Obsidian 的 1.9.2-beta 偏好仍由 `open_obsidian_after_floating_save` 读取。
- 小部件完成时间戳、显示已完成任务、显示任务所有内容、快速添加桌面小部件/桌面图标，以及小部件背景色、背景透明度和自定义图标。
- Monet、预设强调色、夜间模式和暗色背景亮度；对应主题偏好仍由 `QuickDailyThemePreferences` 管理。
- 更新检查、权限申请、日志反馈、关于和支持内容。

## 仍存在但改名或文案归一化

| 1.9.2-beta 基线文案/语义 | 1.9.3-beta 当前文案 | 迁移结论 |
| --- | --- | --- |
| 仓库设置/仓库配置 | 路径配置 | 只改显示名称，路径字段和偏好键不变。注意：本份 APK 的 DEX 已同时包含“路径配置”，所以该 APK 不能作为“仓库设置”旧文案仍在最终编译产物中的证据。 |
| 背景样式 | 小部件背景色 | 当前下拉入口与卡片标题统一为“小部件背景色”；`widget_style`、`widget_background_color` 不变。基线 DEX 已包含“小部件背景色”，未发现“背景样式”字符串。 |
| 小部件自定义图标 | 速录小部件自定义图标 | 只改显示名称；`widget_image_uri` 不变。 |
| 背景不透明度/小部件透明度 | 小部件背景透明度 | 只统一文案；`widget_opacity` 不变，滑条两端增加“更透明”“更深色”。 |
| 任务完成声音布尔开关 | 完成提示音：紧促/经典/静音 | 新键为 `task_completion_sound_mode`；旧 `task_completion_sound` 仍读取并参与迁移，旧用户的开关状态不会丢失。 |

## 仍存在但移动、合并或改变默认展开状态

- 锚点文本仍使用 `anchor_text`，但从直接编辑框改为“编辑锚点文本”弹窗。取消不保存，重置只改变草稿，保存空文本也有效。
- 自定义图标和小部件背景色/透明度合并到同一张“桌面小部件外观”卡片，使用分隔线保留层次；旧的“自定义图标同时应用于……”独立说明不再显示。
- 权限申请位于更新设置上方；更新设置、权限申请、反馈默认收起，关于和支持默认展开。路径配置继续默认收起。
- 任务显示时间段的旧全局 `task_period` 键仍保留，并作为首次渲染小部件时的迁移来源；当前任务小部件范围由每个小部件的 `TaskWidgetConfigActivity` 配置，范围选项仍为今日/本周/本月/自定义页面任务，不属于删除。
- 任务完成提示音由旧开关变为下拉选择。选择紧促或经典立即试听，静音不试听；小部件只有任务写入成功、且是从未完成变为完成时播放。

## 仅在 Monet 开启且系统支持时隐藏

“自定义颜色”区域（预设强调色选择）不是删除项：

- Android 12 及以上且 Monet 开启时，完整区域隐藏。
- 关闭 Monet 后恢复显示；已选择的预设仍保留。
- 不支持 Monet 的系统仍显示预设区域，并提示当前系统不支持。
- `theme_accent_preset` 等主题偏好继续读取。

## 确认没有继续迁移或已经删除的选项

以下是审计中确认不再作为当前活动设置继续提供的项目：

| 选项/键 | 1.9.2-beta 证据 | 1.9.3-beta 当前状态 | 结论 |
| --- | --- | --- | --- |
| 小部件背景模糊 | 基线 DEX 含 `widget_background_blur` | `DiaryConfig` 不再有该字段；`AppState.loadConfig()` 会清理旧键 | 已删除。旧模糊偏好不再影响小部件。 |
| 悬浮窗背景模糊 | 基线 DEX 含 `floating_background_blur` | `DiaryConfig` 不再有该字段；`AppState.loadConfig()` 会清理旧键 | 已删除。旧模糊偏好不再影响悬浮窗。 |
| 标签自动补全开关 | 基线 DEX 含 `tag_autocomplete` 及“标签自动补全”文案 | 当前活动的结构化编辑器不再显示独立开关；标签补全作为内置能力固定启用，读写时规范化为 `true` | 设置项删除，但功能没有删除。 |
| 双链自动补全开关 | 基线 DEX 含 `wikilink_autocomplete` 及“双链自动补全”文案 | 当前活动的结构化编辑器不再显示独立开关；双链补全作为内置能力固定启用，读写时规范化为 `true` | 设置项删除，但功能没有删除。 |

除此之外，没有发现“旧键仍存在但新版本完全不再读取”的路径/主题/小部件核心设置。`anchor_text`、`task_period`、`task_completion_sound`、`widget_style`、`widget_background_color`、`widget_opacity` 和 `floating_note_opacity` 均有当前读取或迁移逻辑。

## 默认值结论

- 缺少 `widget_style` 偏好的新用户使用 `dark`，对应现有黑色 `#202124`。
- 已有用户的显式 `system`、`light`、`dark` 或 `custom` 不会被默认值覆盖；`WidgetAppearance.resolveStyle("system")` 保持 `system`。
- 本清单只审计设置迁移；没有把真机截图、弹窗交互、试听实际出声或 TalkBack 结果列为已验证。
