# QuickDairy — Obsidian 秒开日记 Android App

## 项目定位

专为 Obsidian 用户设计的 Android 日记速记工具。解决 Obsidian 移动端（Electron 启动慢）无法实现「掏出手机→秒写一句话→放回口袋」的核心痛点。

---

## 核心需求

1. **秒开**：打开 App 立即显示今天日记，冷启动 < 500ms
2. **实时保存**：编辑时自动防抖保存（500ms），切后台立即写入
3. **Obsidian 无缝集成**：读取 `.obsidian/daily-notes.json` 配置，自动对齐路径/格式/模板
4. **Markdown 轻量渲染**：标题、任务勾选（可交互）、列表、粗斜体、链接
5. **模板支持**：新日记从模板文件生成，模板为空则空白
6. **桌面小部件**：
   - **桌面便签**：自适应大小，显示今日日记全文，点击打开 App
   - **快速添加**：1×1 小部件，点击弹出悬浮窗，可选时间戳追加到日记末尾
7. **SAF 文件选择**：安卓自带文件夹/文件选择器，选择 vault 和模板
8. **Android 15 适配**：Edge-to-Edge + 存储权限

---

## 技术栈

```
Kotlin + Jetpack Compose + Material3
minSdk: 26
compileSdk / targetSdk: 35
AGP: 8.2.0 / Gradle: 8.4 / Kotlin: 1.9.24
Compose BOM: 2024.09.00 / Compose Compiler: 1.5.14
```

---

## 项目结构

```
QuickDairy/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/quickdairy/
│       │   ├── MainActivity.kt          — 单 Activity，权限申请，生命周期管理
│       │   ├── AppState.kt              — ViewModel，全局状态，文件读写，配置管理
│       │   ├── QuickDairyApp.kt         — Application
│       │   ├── QuickDairyWidget.kt      — 桌面便签小部件
│       │   ├── QuickNoteWidget.kt       — 快速添加小部件（1×1）
│       │   ├── NoteEditActivity.kt      — 速记悬浮窗编辑 Activity
│       │   ├── ui/
│       │   │   ├── EditorScreen.kt      — 编辑主页（编辑/预览切换）
│       │   │   ├── SettingsScreen.kt    — 设置页
│       │   │   └── theme/Theme.kt       — Material3 主题
│       │   ├── markdown/
│       │   │   └── MdRenderer.kt        — Markdown 渲染引擎
│       │   └── util/
│       │       ├── FileUtil.kt           — 文件读写工具
│       │       ├── Debounce.kt           — 防抖工具
│       │       └── UriUtil.kt            — SAF URI → 文件路径转换
│       ├── res/
│       │   ├── layout/widget_diary.xml          — 桌面便签布局
│       │   ├── layout/widget_quicknote.xml      — 快速添加布局
│       │   ├── xml/quickdairy_widget_info.xml   — 便签配置
│       │   ├── xml/quicknote_widget_info.xml    — 快速添加配置
│       │   ├── drawable/widget_background.xml   — 小部件背景
│       │   ├── drawable/widget_preview.xml      — 便签预览图
│       │   ├── drawable/quicknote_preview.xml   — 快速添加预览图
│       │   └── mipmap-hdpi/ic_launcher.png     — 应用图标
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 核心架构

```
MainActivity
├── onCreate(): 权限申请 + 首次启动进设置
├── onResume(): 切回前台重读源文件
├── onPause(): 保存日记
├── onUserLeaveHint(): 退出后台自杀（externalLaunching 标志防护）
└── Navigator: EDITOR / SETTINGS 页面切换

EditorScreen
├── 编辑模式：BasicTextField + verticalScroll
├── 预览模式：MdRenderer 渲染 Markdown
└── 顶部栏：日期标题 + 编辑/预览切换 + 设置入口

SettingsScreen
├── vault 路径：输入框 + SAF 文件夹选择器
├── 从 Obsidian 读取配置按钮
├── 日记文件夹 / 日期格式 / 模板路径设置
├── 路径预览
└── 关于：版本号 + 酷安链接 + 版本日志

NoteEditActivity（悬浮窗）
├── WindowManager.LayoutParams 手工控制位置/大小
├── 透明背景（桌面透视）
├── MaterialTheme(background=Transparent)
└── 时间戳复选框（状态持久化）
```

---

## 关键实现细节

### 文件读写
- `FileUtil.read/write` 直接操作 java.io.File
- 日期格式从 Obsidian (Moment.js) 自动转换为 Java：`YYYY→yyyy, DD→dd`
- 模板路径自动补 `.md` 后缀
- 保存配置用 `commit()` 同步写入防丢失

### 权限
- Android 11+：`MANAGE_EXTERNAL_STORAGE`
- Android 13+：`READ_MEDIA_IMAGES`
- 低版本：`READ/WRITE_EXTERNAL_STORAGE`

### 生命周期
- `onUserLeaveHint` → `finishAffinity()` 退出自杀
- `externalLaunching` 标志防止 SAF 选择器触发误杀
- `onResume` → `loadToday()` 同步外部变更

### 小部件
- 桌面便签：自适应布局，点击打开 MainActivity（FLAG_ACTIVITY_CLEAR_TOP）
- 快速添加：1×1，点击打开 NoteEditActivity 悬浮窗
- 保存后通过 `QuickDairyWidget.updateAllWidgets()` 刷新

---

## 版本历程

| 版本 | 主要更新 |
|------|---------|
| beta0.1 | 秒开日记、OB配置读取、模板、MD渲染、Android 15适配 |
| beta0.2 | 首次启动进设置、SAF文件夹/文件选择器、存储权限 |
| beta0.3 | trim空格+默认值、标题改日期、模板不强制写标题 |
| beta0.4 | 桌面便签小部件、模板.md补全、应用图标 |
| beta0.5 | 隐藏文本组、酷安链接、编辑小部件 |
| beta0.6 | 键盘自适应、退出后台自杀、切前台重读 |
| beta0.7 | 去掉隐藏文本、小部件改名、悬浮窗居中、首启闪退修复 |
| beta0.8 | 代码审查高危修复、Android13图片权限 |
| beta0.9 | SAF闪退修复(externalLaunching)、悬浮窗重构、widget名分离 |
| beta0.10 | 悬浮窗半透明、键盘重构图(imePadding)、日志精简恢复 |
| beta0.11 | 自定义logo图标、键盘白条修复、悬浮窗MaterialTheme |
| beta0.12 | 键盘白条根除(statusBarsPadding)、悬浮窗透明修复 |
| beta11.0 | 悬浮窗完全透明(桌面透视)、文本滚动替代imePadding |

---

## 已知问题 & 开发约定

### 待解决
1. 悬浮窗透明在部分设备上仍然显示黑色背景（MaterialTheme 内层 Surface 覆盖）
2. `UriUtil` 仅支持 primary storage，不支持 SD 卡
3. `FileUtil.read` 无法区分「文件不存在」与「读取失败」
4. 小部件 `updateAllWidgets()` 从非主线程调用时需要 `withContext(Dispatchers.Main)`

### 开发约定
- 每次发版版本号 +1（`versionCode` 递增，`versionName` 递增）
- 发版到 `C:\Download\互传\` 目录（Syncthing 同步）
- 编译命令：`QuickDairy\gradlew.bat -p QuickDairy assembleDebug`
- 不要用 PowerShell 构建 JSON（转义问题），用 Python
- 权限对话框触发前设置 `MainActivity.externalLaunching = true`

### API 密钥（Mimo 多模态识别）
不在本文档中，请从项目记忆获取。

---

## Skills 清单（14个）

所有已安装 skills 见 `.reasonix/skills/` 目录。
