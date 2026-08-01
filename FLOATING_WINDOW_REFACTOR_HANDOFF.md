# QuickDaily 悬浮窗重构对接手册

## 1. 目标

将 QuickDaily 的速录 UI 从当前 `NoteEditActivity` 自由窗口承载，迁移为真正的系统悬浮窗 Overlay，使其可以：

1. 从小米/鸿蒙的侧边应用栏触发；
2. 显示在当前应用之上，而不是被 MIUI 放进黑色 freeform Activity 容器；
3. 保持当前速录功能和返回逻辑；
4. 在没有悬浮窗权限、服务被杀或系统限制时安全降级到完整首页；
5. 不创建第二个窗口、不清空已有草稿。

这是一项承载层重构，不是单纯修改 `resizeableActivity` 或窗口背景色。

## 2. 现状架构

### 2.1 启动入口

文件：

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/quickdaily/LauncherActivity.kt`
- `app/src/main/java/com/quickdaily/QuickLaunchPolicy.kt`

当前 Manifest 关键配置：

```xml
<activity
    android:name=".LauncherActivity"
    android:exported="true"
    android:resizeableActivity="true"
    android:theme="@style/Theme.QuickDaily.Launcher"
    android:excludeFromRecents="true"
    android:taskAffinity=""
    android:launchMode="singleTask">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

`LauncherActivity` 根据以下条件决定是否进入速录：

- `Intent.ACTION_MAIN`；
- 包含 `Intent.CATEGORY_LAUNCHER`；
- SharedPreferences 中存在非空 `vault_path`；
- Android 11+ 具备 `Environment.isExternalStorageManager()`，旧版本具备存储权限。

满足条件时启动：

```kotlin
Intent(this, NoteEditActivity::class.java)
    .putExtra(NoteEditActivity.EXTRA_RETURN_TO_HOME, false)
```

否则启动完整 `MainActivity`。

### 2.2 当前速录 Activity

Manifest 当前配置：

```xml
<activity
    android:name=".NoteEditActivity"
    android:exported="false"
    android:windowSoftInputMode="adjustResize"
    android:theme="@style/Theme.QuickDaily.Transparent"
    android:excludeFromRecents="true"
    android:taskAffinity=""
    android:launchMode="singleInstance" />
```

Activity 在 `onCreate()` 中手动设置：

- 宽度：屏幕宽度约 88%；
- 高度：屏幕高度约 35%；
- 顶部偏移：屏幕高度约 25%；
- `FLAG_NOT_TOUCH_MODAL`；
- `FLAG_WATCH_OUTSIDE_TOUCH`；
- `dimAmount=0`；
- `PixelFormat.TRANSLUCENT`；
- 清除 `FLAG_DIM_BEHIND`。

Compose 根层已经是透明 `Surface`，但编辑器面板仍使用 `FloaterColors.background` 绘制深色面板。更重要的是，在小米侧边栏 freeform 任务中，即使窗口 format 为 `TRANSPARENT`，系统仍提供黑色 freeform Surface。

### 2.3 当前编辑功能

主要代码集中在：

`app/src/main/java/com/quickdaily/NoteEditActivity.kt`

现有功能包括：

- 文本输入和即时草稿状态；
- 标签扫描与自动补全；
- 图片选择、多附件选择和持久化 URI 权限；
- 图片预览和删除；
- 模板/标签/Markdown 工具栏；
- 撤销/重做；
- 回车保存配置；
- 保存到 Obsidian vault；
- Widget 刷新、最近标签记录和保存 Toast；
- 首页、关闭、返回键和点击外部区域行为。

重构时不能把这些逻辑复制一份到 Service；应抽取共享状态和用例层。

## 3. 已验证的错误方案

### 3.1 禁止 `LauncherActivity` 调整大小

```xml
android:resizeableActivity="false"
```

结果：QD 被小米侧边工具箱从应用名单中过滤，侧边栏无法再启动 QD。该属性不能使用。

### 3.2 只禁止 `NoteEditActivity` 调整大小

结果：QD 仍然在小米 freeform 小窗中显示，不能自动切换为全屏。该属性也不能作为解决方案。

### 3.3 只将 Compose/Activity 背景设为透明

结果：当前窗口属性确实显示 `fmt=TRANSPARENT`，但小米 freeform 容器仍显示黑色。说明 Activity 透明度不足以绕过厂商任务 Surface。

## 4. 推荐目标架构

```text
侧边栏/桌面图标
        │
        ▼
LauncherActivity（保持 resizeable=true，只做分流）
        │
        ├─ 有 Overlay 权限 ──► FloatingNoteService.showOrFocus()
        │                         │
        │                         └─ WindowManager.TYPE_APPLICATION_OVERLAY
        │                              └─ ComposeView / NoteEditorHost
        │
        └─ 无权限/启动失败 ──► MainActivity + 授权提示
```

推荐原则：

- `LauncherActivity` 继续作为标准 `MAIN/LAUNCHER` 入口，保证小米侧边栏能收录 QD；
- Launcher 启动 Overlay 服务后立即结束或把自身任务移到后台，不让空的 freeform Activity 留在前台；
- Service 只负责窗口生命周期和承载；
- 编辑状态、保存用例、附件状态由共享 ViewModel/StateHolder 管理；
- 当前 Activity 速录保留为无 Overlay 权限时的兼容降级路径，或作为调试/备用入口；
- `showOrFocus()` 必须幂等：已有窗口时聚焦，不创建第二个窗口，不清空草稿。

## 5. 建议的代码边界

以下是建议接口，不要求名字完全一致，但职责必须保持分离：

```kotlin
enum class FloatingNoteSource {
    SIDEBAR,
    DESKTOP_LAUNCHER,
    HOME_AUTH_FLOW,
    SHORTCUT,
    WIDGET
}

data class FloatingNoteRequest(
    val source: FloatingNoteSource,
    val prefillText: String = "",
    val returnToHomeAfterClose: Boolean
)

interface FloatingNoteController {
    fun showOrFocus(request: FloatingNoteRequest): Boolean
    fun hide(reason: HideReason)
    fun isShowing(): Boolean
}
```

共享状态至少应包含：

- `text`；
- 选中的图片 URI；
- 待复制附件 URI；
- 当前标签补全状态；
- 撤销栈/重做栈；
- `returnToHomeAfterClose`；
- 当前来源 `FloatingNoteSource`；
- 是否已保存/正在保存；
- Overlay 权限和服务状态。

不要让这些状态只存在于 `Activity` 或某一个 Compose composition 中，否则 Service 重建、窗口隐藏/显示或进程回收时会丢稿。

## 6. Overlay 实现要求

### 6.1 权限

Manifest 需要评估并增加：

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

运行时通过 `Settings.canDrawOverlays(context)` 检查。权限缺失时：

1. 不循环弹授权页；
2. 进入完整首页并显示一次明确提示；
3. 用户授权返回后立即重试 `showOrFocus()`；
4. 用户拒绝后保持首页可用。

### 6.2 Service

建议使用 `LifecycleService` 或等价的前台 Service。项目 `targetSdk=35`，必须按当前 Android 版本要求核对：

- `startForegroundService()` 与 `startForeground()` 时限；
- 前台服务类型和对应权限；
- 通知渠道和通知文案；
- 后台启动限制；
- 小米/鸿蒙的自启动、电池优化和悬浮窗授权。

不要未经验证直接复制某个旧版本的 foreground service 配置。

### 6.3 WindowManager 参数

目标窗口一般应使用：

```kotlin
WindowManager.LayoutParams(
    width,
    height,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
    PixelFormat.TRANSLUCENT
).apply {
    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
}
```

注意：

- 编辑器窗口不能使用 `FLAG_NOT_FOCUSABLE`，否则无法正常输入；
- 不要使用 `FLAG_ALT_FOCUSABLE_IM`，它会改变窗口与输入法的层级并可能阻止正常编辑；
- Overlay 根背景和 Compose 根 `Surface` 都应透明；
- 编辑面板本身建议保留可配置的半透明背景，而不是默认完全透明，否则不同壁纸/应用下文字可能不可读；
- `WindowManager.addView()` 与 `removeView()` 必须成对，且只能由一个主线程控制。

### 6.4 Compose 承载

Service 中使用 `ComposeView` 时需要正确设置：

- `LifecycleOwner`；
- `SavedStateRegistryOwner`（如果要恢复 UI 状态）；
- `ViewTreeLifecycleOwner`；
- `ViewTreeSavedStateRegistryOwner`；
- Compose disposal 策略；
- 输入法和 WindowInsets 监听。

Service 被销毁或 Overlay 权限失效时，必须先 dispose Compose 内容，再移除窗口。

## 7. 生命周期与返回栈

### 7.1 侧边栏启动

期望行为：


```text
当前用户正在微信/桌面/其他应用
        │
        ▼ 点击侧边栏 QD
Overlay 出现并聚焦输入框
        │
        ├─ 关闭/返回键 ──► 回到原应用，不打开 MainActivity
        ├─ 首页 ─────────► 关闭 Overlay，打开 MainActivity
        └─ 保存 ─────────► 保存后按当前来源关闭或保留，不能重复创建窗口
```

### 7.2 首页授权流程启动

期望行为：

- 从首页进入授权流程后打开 Overlay；
- 关闭 Overlay 或返回键回到 `MainActivity`；
- 授权完成后只打开一个速录窗口；
- 拒绝权限不会循环跳转。

### 7.3 重复启动

连续点击侧边栏 QD 时：

- 只保留一个 Overlay；
- 现有草稿、光标、键盘状态尽量保留；
- 新请求的 `prefillText` 不能覆盖已有非空草稿，除非用户明确确认；
- 日志应记录 `show`, `focus`, `reuse`, `hide`, `permission_denied`。

## 8. 业务迁移清单

优先把以下内容从 `NoteEditActivity` 拆到共享层：

- `appendToDiary()` 及其日期/文件路径逻辑；
- 附件复制和 Markdown wikilink 生成；
- 标签扫描、补全和最近标签记录；
- 撤销/重做状态；
- 保存中状态与重复保存保护；
- Widget 刷新；
- `returnToHomeAfterClose` 和来源策略。

Overlay UI 只负责：

- 渲染状态；
- 输入事件；
- 图片/附件选择请求；
- 保存、关闭、首页按钮回调；
- WindowManager 的显示、更新和隐藏。

## 9. 验收矩阵

### 小米真实设备

- [ ] 冷启动侧边栏 QD，Overlay 显示在当前应用上方；
- [ ] Overlay 背景透明或按设计显示半透明背景，不出现 MIUI 黑色 freeform 容器；
- [ ] 输入框自动聚焦，键盘正常弹出，键盘收起后布局恢复；
- [ ] 关闭/返回键回到原应用；
- [ ] 首页按钮进入完整首页；
- [ ] 连续点击 QD 只有一个窗口，草稿不丢；
- [ ] 保存文本、图片、附件、标签、模板、撤销/重做均无回归；
- [ ] 悬浮窗权限撤销后回退首页，不白屏、不循环授权；
- [ ] 服务被系统回收后再次启动能恢复或明确丢弃状态，不残留死窗口；
- [ ] 从最近任务恢复不重复创建 Overlay；
- [ ] 侧边栏仍能找到 QD。

### 鸿蒙/其他 Android 设备

- [ ] Overlay 权限入口可达；
- [ ] 厂商侧边栏/应用栏能启动 QD；
- [ ] 全屏应用、桌面、输入法、横竖屏下均可关闭；
- [ ] 电池优化或后台限制开启时有可理解的降级行为；
- [ ] 构建成功之外必须留存真实 UI 截图和日志。

## 10. 调试命令与日志建议

设备：

```powershell
$adb = 'C:\APP\投屏APP\Escrcpy\resources\extra\win\scrcpy\adb.exe'
$serial = '6XYPYTKF6DBIIB4L'
& $adb -s $serial shell cmd package resolve-activity --brief com.quickdaily
& $adb -s $serial shell dumpsys window windows
& $adb -s $serial shell dumpsys activity activities
& $adb -s $serial logcat -d -s QuickDaily:* AndroidRuntime:E ActivityTaskManager:I WindowManager:I
```

建议统一日志 tag：

- `FloatingNote/Permission`
- `FloatingNote/Service`
- `FloatingNote/Window`
- `FloatingNote/Keyboard`
- `FloatingNote/State`
- `FloatingNote/Save`

每次测试至少记录：设备型号、系统版本、是否授权 Overlay、窗口来源、窗口显示/关闭结果、截图路径和异常日志。

## 11. 交付要求

开发者完成后应交付：

1. 变更文件清单；
2. Overlay 权限和 Service 配置说明；
3. 状态迁移说明；
4. 小米与鸿蒙真实设备测试记录；
5. 失败降级路径说明；
6. 本地 APK 及构建命令；
7. 不包含 GitHub 提交、推送或发布动作，除非另行授权。

## 12. 明确不要做的事情

- 不要把 `LauncherActivity` 设置为 `resizeableActivity=false`；
- 不要复制一套新的保存/附件逻辑到 Service；
- 不要用全局静态变量代替可恢复状态；
- 不要在 Overlay 权限失败时无限重试；
- 不要用 `FLAG_NOT_FOCUSABLE` 伪装解决键盘问题；
- 不要用 `git reset --hard` 清理当前脏工作区；
- 不要以“assembleRelease 成功”作为小米/鸿蒙 UI 验收结论。
