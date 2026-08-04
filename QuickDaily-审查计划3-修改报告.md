# QuickDaily 审查计划 3 修改报告

报告日期：2026-08-02  
工作区：`C:\Users\Ivan\Documents\QuickDaily`  
验证设备：`6XYPYTKF6DBIIB4L`，Android SDK 36，QuickDaily `1.8.6 - beta`

## 一、执行范围与约束

本次按 `QuickDaily-审查计划3.md` 执行，保留了工作区原有修改和未跟踪文件。未执行 `git reset --hard`、`git checkout -- .`、`git clean -fd`，未提交、推送、创建 PR、打 Tag 或发布 APK。

API 26–28 兼容验证按用户要求跳过。今日测试数据允许写入设备上的 `2026-08-02.md`，因此该文件保留了本次 QA 标记和附件链接。

## 二、代码修改摘要

### P1 问题组

1. 编辑器异步重载竞态
   - 在 `AppState.kt` 增加重载快照、generation、路径和修改时间校验。
   - 过期重载结果不再覆盖当前编辑内容。
   - 增加 `EditorReloadPolicyTest.kt`。

2. 设置页 Obsidian 配置读取竞态
   - 在 `SettingsScreen.kt` 增加请求 generation、可取消 Job 和结果校验。
   - 旧请求返回时不会覆盖新配置。
   - 增加 `SettingsConfigReadPolicyTest.kt`。

3. 悬浮速记录音状态
   - 在 `FloatingNoteService.kt` 增加录音、停止和收尾状态保护。
   - 避免重复收尾、重复关闭或异常状态下的错误写入。
   - 扩展 `FloatingNotePolicyTest.kt`。

4. 编辑器光标和缩略图处理
   - `EditorScreen.kt` 增加光标有效性保护。
   - `NoteEditActivity.kt` 将 API 29+ 与 API 26–28 的缩略图路径分开处理；API 26–28 本次只保留代码测试，不做设备兼容验证。
   - 增加 `EditorCursorPolicyTest.kt`、`EditorThumbnailPolicyTest.kt`。

5. Widget 异步文件操作
   - `TaskToggleUseCase.kt` 增加 `WidgetAsyncWorkRunner`，保证 Widget 文件操作在 IO 线程运行并在 finally 中结束 `goAsync()`。
   - `TaskWidget.kt`、`QuickDailyReadWidget.kt` 接入异步刷新。
   - 增加 `WidgetAsyncWorkRunnerTest.kt`。

6. 文件冲突处理
   - `AppState.kt` 增加磁盘冲突模型、dirty/contentVersion 和人工选择策略。
   - `EditorScreen.kt` 增加“采用磁盘版本”和“保留本地并覆盖磁盘”两个内联操作。
   - 增加 `EditorConflictPolicyTest.kt`。

7. 文件写入并发和原子性
   - `FileUtil.kt` 增加按 canonical path 的互斥锁和临时文件加原子移动，保留回退写入路径。
   - 接入 `TaskToggleUseCase.kt`、`FloatingNoteSaveUseCase.kt`、`MainActivity.kt` 和 `AppState.kt` 的相关写入路径。
   - 增加 `FileMutationCoordinatorTest.kt`。

8. Android 兼容边界
   - `QuickTileService.kt` 对 API 34+ 使用直接 SDK 分支，旧版本保留兼容调用。
   - `AndroidManifest.xml` 将相机声明为非必需硬件。
   - 增加 `QuickTileLaunchPolicyTest.kt`。

### P2 问题组

9. 多图插入顺序
   - `EditorTextActionPolicy.kt` 增加 `EditorImageInsertPolicy`。
   - `EditorScreen.kt` 对多选图片按选择顺序逐张完成 IO，再按同一顺序更新编辑器内容。
   - 增加 `EditorImageInsertPolicyTest.kt`。

10. QuickNote Widget 图片解码
    - `QuickNoteWidget.kt` 的 `onUpdate`、`onAppWidgetOptionsChanged` 和 `updateAllWidgets` 改为后台执行。
    - 图片解码、裁剪、缩放和圆角 Bitmap 处理不再阻塞 Widget 广播主线程。
    - 复用 `WidgetAsyncWorkRunner`，并使用 `goAsync()` 正确结束广播生命周期。
    - 回归测试先在实现前因缺少策略而失败，补实现后通过。

11. 自动化证据
    - 已补齐设置配置读取、编辑器重载 generation、Widget 异步收尾和录音状态相关单测证据。

## 三、本地验证结果

每个已实现问题组均按要求执行回归测试；最终执行：

```text
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

结果：

- 单元测试：通过
- lint：0 errors，47 warnings
- Debug 编译：通过
- `git diff --check`：无空白错误；仅有工作区既有的 LF/CRLF 提示
- 最终工作区改动已复核；未清理、覆盖或还原用户文件

最终 `git diff --stat` 为 35 个已跟踪文件、2845 insertions、650 deletions。该统计包含执行前已存在的用户修改，不能全部归因于本次审查。

## 四、真实设备验证

已完成并留有设备证据的流程：

- 人工磁盘冲突选择：分别验证采用磁盘版本、保留本地覆盖磁盘。
- Task Widget：完成任务后 Markdown 状态更新并从 Widget 消失。
- Widget 文件操作：验证任务项更新、进程强制停止后文件仍可读。
- 编辑器压力输入：连续输入 50 个标记，全部写入今日文件，无崩溃。
- 录音：真实开始、计时、停止并写入音频链接；重复启停后回到空闲状态。
- QuickNote Widget：真实打开悬浮速记并保存到今日文件。
- EDT-03 三图顺序：在照片选择器按以下顺序选择三张图片：
  `84648 → 84647 → 84646`

今日文件中对应链接顺序为：

```markdown
![2026-08-02_224038_84648](附件/2026-08-02_224038_84648.jpg)
![2026-08-02_224038_84647](附件/2026-08-02_224038_84647.jpg)
![2026-08-02_224038_84646](附件/2026-08-02_224038_84646.jpg)
```

三图流程完成后：

- QuickDaily 进程仍存活
- crash buffer 文件大小为 0
- 今日文件中顺序与选择顺序一致

## 五、性能证据与边界

WDG-02 已采集 focused Perfetto、gfxinfo 和 framestats 文件：

`C:\Users\Ivan\.codex\visualizations\2026\08\02\019fc287-55ef-7d90-9289-041d93839134`

由于本机没有可用的 trace processor，且 gfxinfo 样本包含历史帧，不能形成严格的前后对照。因此本报告只确认：

- 源码路径已移出 Widget 广播主线程。
- 单元测试验证了后台线程调度。
- 真实设备更新流程无崩溃。

不宣称具体帧率、耗时或性能提升百分比。

## 六、仍未完成的验证

- API 26–28 兼容验证：按用户要求跳过。
- `QuickDailyReadWidget`：设备桌面上没有可操作的 ReadWidget 实例。
- Quick Settings Tile：系统快捷设置面板中没有可操作的 QuickDaily Tile。
- 录音三次独立附件写入和 IO-02 完全并发场景的证据仍不充分，因此不作满覆盖率结论。

## 七、测试数据和恢复情况

用户授权的设备测试文件：

`/storage/emulated/0/Documents/.Obsidian库/Obsidian库/日记/2026-08-02.md`

该文件保留 QA 标记、任务项、压力输入、录音链接、悬浮速记和三张图片链接。QuickNote Widget 使用的临时图片已从应用私有目录和设备临时目录删除；`widget_image_uri` 已恢复为空，`task_completion_timestamp` 已恢复为原值 `false`。未有意修改其他日期日记文件。

