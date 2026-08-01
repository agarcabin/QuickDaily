# Plan: QuickDaily 1.7.2-beta 悬浮窗启动与回车响应优化

## 目标

解决 Xiaomi Android 16 真机上的两类体感延迟：

1. 悬浮窗已经出现后，输入法约晚一秒才弹出。
2. 输入法回车触发保存后，悬浮窗约晚一秒才关闭。

目标是消除明显的一秒级停顿，同时保持当前的草稿恢复、保存可靠性、附件处理和 Activity 兼容路径。

本计划只针对已验证的 Xiaomi 22041211AC / Android 16 / SDK 36 行为，不推断 API 26-30 或鸿蒙设备表现。

## 当前判断

### 输入法启动延迟

当前启动链路为：

```text
WindowManager.addView
    -> 外层 FloatingNoteComposeView.requestFocus
    -> Compose 首次组合
    -> LaunchedEffect(Unit)
    -> BasicTextField FocusRequester.requestFocus
    -> InputConnection 建立
    -> MIUI 输入法显示
```

主要疑点：

- 外层 View 先获得焦点，随后 BasicTextField 再抢焦点，发生了两次焦点切换。
- 当前只调用 `FocusRequester.requestFocus()`，没有显式调用 `SoftwareKeyboardController.show()`。
- `SOFT_INPUT_ADJUST_RESIZE` 只控制键盘出现后的窗口调整，不负责主动显示键盘。
- `BasicTextField`、ComposeView、WindowManager Overlay 和 MIUI 输入法之间的异步 InputConnection 建链可能放大上述时序问题。

### 回车关闭延迟

当前 `enterToSave` 依赖 `BasicTextField.onValueChange()` 检测新增换行符，再调用保存：

```text
IME 提交换行
    -> onValueChange
    -> 识别新增 '\\n'
    -> FloatingNoteService.saveDraft
    -> FloatingNoteSaveUseCase 完成
    -> disposeComposition/removeView
```

保存完成前不会移除窗口。保存用例可能读取当天日记、读取模板、解析 Frontmatter、处理图片/附件、写回 Markdown 并记录标签。Widget 刷新已有异步 debounce 机制，预计不是主要阻塞源，但需要用时间戳确认。

## 实施阶段

### 阶段 0：增加可量化基线

使用 `SystemClock.elapsedRealtime()` 增加单调时钟日志，避免使用墙上时间判断耗时。

至少记录以下事件：

- `service_start`
- `window_add_start` / `window_add_end`
- `compose_content`
- `focus_request`
- `focus_acquired`
- `ime_show_request`
- `ime_visible`
- `enter_down`
- `save_start`
- `save_use_case_done`
- `hide_start` / `hide_end`

真机保留：

- `dumpsys window windows`
- `dumpsys activity activities`
- 应用日志与 `logcat -b crash`
- 窗口出现、键盘出现、回车关闭三个阶段的截图

先确认延迟发生在“焦点/IME”“回车事件”还是“保存/销毁窗口”，再决定是否进入高风险优化。

### 阶段 1：修复启动焦点与 IME 时序

优先采用低风险方案：

1. 不再让外层 Overlay 根 View 主动调用 `requestFocus()` 抢占输入焦点。
2. 让 BasicTextField 成为唯一输入焦点目标。
3. 在 Compose 首帧完成后调用 `FocusRequester.requestFocus()`。
4. 焦点确认后调用 `SoftwareKeyboardController.show()`。
5. 如果 Overlay 首帧尚未 attach，使用 `awaitFrame()` 或 View `post {}` 等待下一帧，不使用固定 1 秒延迟。
6. 增加一次性幂等保护，避免重复请求焦点或重复唤起输入法。

可选 A/B：

- `SOFT_INPUT_STATE_ALWAYS_VISIBLE or ADJUST_RESIZE`
- 当前 `SOFT_INPUT_ADJUST_RESIZE`

只有实测证明系统参数有效时才保留，避免不同输入法下强制弹键盘造成副作用。

### 阶段 2：改为显式处理回车动作

新增统一的 `saveOrClose()` 入口，所有保存触发都调用同一个函数：

- 保存按钮
- IME `KeyboardActions.onDone`
- 物理键盘 Enter
- ADB `KEYCODE_ENTER`

具体方案：

1. 为输入框配置合适的 `KeyboardOptions` 和 `ImeAction.Done`。
2. 使用 `KeyboardActions(onDone = { saveOrClose() })` 处理软键盘动作。
3. 使用 `onPreviewKeyEvent` 或 `onKeyEvent` 处理物理 Enter/小键盘 Enter。
4. 保留标签联想优先级：有标签候选时，回车先选中候选；没有候选时才保存。
5. `enterToSave=false` 时继续允许普通换行。
6. 使用 `isSaving` 或一次性事件保护，防止 IME action 与 key event 双重触发保存。

不再把“是否新增换行符”作为主要回车判断依据。

### 阶段 3：降低保存完成到窗口关闭的延迟

先保持当前“保存完成后关闭”的数据安全语义，再根据阶段 0 的测量结果选择优化：

低风险优化：

- 为 `FloatingNoteSaveUseCase` 增加分段耗时日志。
- 确认当天日记、模板读取是否重复或过慢。
- 无图片/附件时走纯文本快速路径。
- 保持 Widget 刷新在后台异步执行，不让刷新结果阻塞窗口关闭。
- 保持草稿清理发生在保存成功之后。

高风险可选方案：

```text
先持久化草稿
    -> 立即关闭 Overlay
    -> ApplicationScope/WorkManager 后台完成保存
    -> 成功后清理草稿
    -> 失败则保留草稿并通知用户
```

只有当实测确认文件写入或附件处理确实造成主要延迟时，才采用该方案。不能在未建立可靠后台任务前直接关闭并停止 Service，否则可能造成保存任务被取消或草稿状态不一致。

## 验收矩阵

### 启动与输入法

- 桌面 Launcher 冷启动：窗口先显示，输入框自动获得焦点，键盘无明显一秒级停顿。
- 系统侧边栏启动：同上。
- 已有 Overlay 重复点击：只聚焦已有窗口，不创建第二个窗口，不覆盖草稿。
- 输入法未安装、切换输入法、输入法拒绝显示：窗口仍可编辑，不能崩溃。
- `dumpsys window` 确认窗口类型仍为 `TYPE_APPLICATION_OVERLAY`。

### 回车与关闭

- 软键盘 Done/Enter 能进入统一保存路径。
- 物理 Enter 和 ADB Enter 能进入统一保存路径。
- 标签联想打开时，回车优先选择标签而不是保存。
- 空内容回车关闭但不创建日记内容。
- 非空内容回车保存成功后关闭。
- 保存失败时窗口不丢失内容，草稿仍可恢复。
- 硬件 Back 关闭 Overlay 并保留草稿。

### 数据与兼容性

- 文本、图片、附件、标签、撤销/重做保持现有行为。
- 保存成功后 `floating_draft_text`、图片和附件草稿键被清理。
- Service 被回收后重新启动可以恢复草稿。
- 分享、Widget、快捷方式等非 Launcher 路径继续保持 Activity 兼容。
- 权限撤销后进入 MainActivity 授权提示，不循环跳转。

### 交付

- `:app:testDebugUnitTest`
- `:app:assembleRelease`
- 校验 `versionName=1.7.2-beta`、`versionCode=59`
- Release APK 本地安装并完成上述 Xiaomi ADB 验收
- 复制 `QuickDaily-1.7.2-beta.apk` 到本地交付目录
- 不提交、不推送、不创建 GitHub Release

## 风险与回滚

- IME 显示属于系统和输入法实现，必须以实机时序证据为准，不能只凭 Compose 代码判断。
- 强制关闭窗口再后台保存会增加数据一致性风险，默认不采用。
- `KeyboardActions` 与多行 BasicTextField 的组合需要同时验证软键盘、物理键盘和标签联想。
- 如果阶段 1 或阶段 2 引入回归，保留当前 `NoteEditDialog` 作为可回滚基线，只回滚焦点/回车入口，不回退已经稳定的 Overlay Service、草稿和保存架构。

## 当前边界

本计划已进入实现与真机验证阶段。现有工作区脏改动、`PLAN.md`、`PLAN-REVIEW-LOG.md` 及既有版本化计划文件保持不变。

## 执行记录（2026-07-24）

已完成：

- 阶段 0：在 `FloatingNoteService` 增加基于 `SystemClock.elapsedRealtime()` 的单调时钟埋点，覆盖 Service、窗口、焦点、IME 请求、保存和关闭阶段。
- 阶段 1：移除 Overlay 根 View 的主动抢焦点；由 `BasicTextField` 在首轮 Compose 组合提交后单次请求焦点，并在焦点确认后立即调用 `SoftwareKeyboardController.show()`。
- 阶段 2：增加统一 `saveOrClose()`，接入 `KeyboardActions(onDone)`、物理/ADB Enter 和 IME 换行提交；保留标签候选优先，并增加保存中的幂等保护。
- 阶段 3：按时序证据暂不拆分保存与关闭；Widget 刷新已确认发生在窗口关闭之后，不是主要阻塞源。

小米 22041211AC / Android 16 / SDK 36 真机证据：

- 第一次优化后：`window_add_end=94620656`，`focus_request=94621007`，间隔 351 ms；焦点后 0 ms 请求 IME。该轮截图确认键盘已显示，动作键为“完成”。
- 第二次优化后：`window_add_end=94825650`，`focus_request=94825899`，间隔 249 ms；`focus_acquired=94825907`，`ime_show_request=94825907`。
- 第三次冷启动复测：`window_add_end=94858808`，`focus_request=94859041`，间隔 233 ms；焦点后 8 ms 内完成 IME 显示请求。
- ADB Enter 测试：`enter_action=94749684`，`save_use_case_done=94749703`，`hide_end=94749735`；保存开始到窗口关闭约 49 ms，Widget 刷新在其后异步执行。
- UI 树确认输入框为 `focused=true`，窗口仍为 `TYPE_APPLICATION_OVERLAY`；截图证据为 `adb-ime-optimized-after.png`。

验证结果：

- `:app:assembleDebug`：通过。
- `:app:testDebugUnitTest`：通过（当前单元测试 Java 任务为 `NO-SOURCE`）。
- Debug APK 已安装到上述小米真机；桌面 Launcher 入口可启动 Overlay，输入框自动聚焦并弹出键盘。

Release 交付已完成：

- `:app:assembleRelease`：通过；`versionName=1.7.2-beta`、`versionCode=59`。
- Release APK 已安装到小米真机；`dumpsys window` 确认 `ty=APPLICATION_OVERLAY`，且 `imeInputTarget/imeControlTarget` 指向 QuickDaily。
- Release UI 树确认输入框 `focused=true`；视觉证据为 `adb-overlay-release-final.png`，结构证据为 `adb-ui-release-final.xml`。
- Release 包发送 ADB Enter 后 Overlay 消失；`logcat -b crash` 未发现 QuickDaily 崩溃。
- 交付包：`C:\Download\互传\QuickDaily-1.7.2-beta.apk`，SHA-256=`6A160D54D772FBDD1925AA916BC8331F54FD2405675B7F5AA9A1CF18A9F5A61C`。
- 未提交、未推送、未创建 GitHub Release；鸿蒙设备仍未验证。

设置开关更新后的 Release 交付：

- 重新执行 `:app:assembleRelease`：通过；Release APK 已重新安装到小米真机。
- 版本仍为 `versionName=1.7.2-beta`、`versionCode=59`。
- 交付包已覆盖更新到 `C:\Download\互传\QuickDaily-1.7.2-beta.apk`，SHA-256=`2D64ACE987DA539E13AB95E20383AA7743311CADDA41AE04C40FBD661752F7AC`。
- Release 安装后在关闭开关状态下，桌面 Launcher 仍进入 `NoteEditActivity`，窗口为 `BASE_APPLICATION`。

## 缺陷回归与发版记录（2026-07-24）

- Release Lint：通过，无阻断 issue。
- 小米 Android 16 / SDK 36：开关开启后 Launcher 创建单个 `TYPE_APPLICATION_OVERLAY` 窗口，输入框自动聚焦；重复启动后窗口数仍为 1。
- 撤销 `SYSTEM_ALERT_WINDOW` 后启动 Launcher：正确进入 `MainActivity` 并显示一次“需要悬浮窗权限”提示，没有循环跳转；随后已恢复 AppOps 为 allow。
- 开关关闭后最终状态：桌面 Launcher 进入 `NoteEditActivity`，未发现 QuickDaily 悬浮窗；快捷方式关闭路径也回归 `NoteEditActivity`。
- Release `logcat -b crash` 未发现 QuickDaily 崩溃；`git diff --check` 无错误（仅有既有换行格式提示）。
- 本地 Release APK 已保持为 `2D64ACE987DA539E13AB95E20383AA7743311CADDA41AE04C40FBD661752F7AC`，未提交、未推送、未发布 GitHub。

## 设置开关执行记录（2026-07-24）

针对桌面图标路径比系统侧边栏明显更慢的问题，增加“系统侧边栏悬浮窗支持”开关，默认关闭。设置值写入 `QuickDaily` SharedPreferences 的 `system_sidebar_support`。

- 开关关闭：桌面 Launcher、快捷方式和 Quick Settings Tile 走 `NoteEditActivity` 旧版快速编辑路径；不申请或启动悬浮窗，关闭后回到原系统界面。
- 开关开启：桌面 Launcher、快捷方式和 Quick Settings Tile 在悬浮窗权限可用时走 `FloatingNoteService`；窗口保持 `TYPE_APPLICATION_OVERLAY`，已有窗口只聚焦，不覆盖草稿。
- 权限不可用：快捷入口安全降级到 `NoteEditActivity`；桌面 Launcher 保留 MainActivity 授权提示流程，避免循环跳转。
- 设置文案明确标注：开启侧边栏支持可能使启动和关闭增加约 0.5 秒；无侧边栏需求建议关闭。

### 小米真机双模式验收

设备：`22041211AC`，Android 16 / SDK 36，ADB serial `6XYPYTKF6DBIIB4L`。

- 开关开启后通过桌面 Launcher 启动：`dumpsys window` 确认 `pkg = com.quickdaily` 且 `ty=APPLICATION_OVERLAY`；UI 树确认 Compose 输入框 `focused=true`。
- 开启状态下输入框可见并自动获得焦点；发送 ADB Enter 后悬浮窗消失，日志记录 `hide reason=close`，Service 随后销毁。
- 开关关闭后重新通过桌面 Launcher 启动：顶层 Activity 为 `com.quickdaily/.NoteEditActivity`，窗口为 `ty=BASE_APPLICATION fmt=TRANSPARENT`；没有 QuickDaily 的 `APPLICATION_OVERLAY` 残留。
- 设置 UI 树确认开关从 `checked=true` 切换为 `checked=false`，SharedPreferences 确认 `system_sidebar_support=false`。
- 当前 Debug 回归：`:app:testDebugUnitTest`、`:app:assembleDebug` 均通过，Debug APK 已安装到真机；未重新执行 Release 发版（既有本地 Release 交付保持不变）。
