# QuickDaily / Codex 重启对接手册

本文用于 Codex Desktop 重启后新任务的接续。新会话应先验证 Windows apply_patch 沙箱是否恢复，再决定是否继续修改代码。

## 1. 项目与工作区

- 仓库：C:\Users\Ivan\Documents\QuickDaily
- 当前工作区包含用户尚未提交的 QuickDaily 1.8.8-beta 改动，全部视为有效工作。
- 开始时先执行 git status --short，不要执行 git reset、git checkout、git clean 或删除未知文件。
- 不提交、不推送、不打 tag、不发布 GitHub、不自动复制 APK。

## 2. 第一优先级：验证 apply_patch

先不要重新检查全部功能，也不要先跑完整 ADB 流程。只做以下环境验证：

1. 确认当前目录是 C:\Users\Ivan\Documents\QuickDaily。
2. 读取本手册。
3. 使用 apply_patch 把下面这一行：

   apply_patch 探针状态：待新会话填写

   改为：

   apply_patch 探针状态：通过（新会话已验证）

4. 如果 apply_patch 成功，再执行 git diff --check。
5. 如果失败，记录完整错误，尤其是是否仍然出现：

   windows unelevated restricted-token sandbox cannot enforce split writable root sets directly

   此时不要继续改源码。报告 Codex Desktop 当前会话仍然挂载了多个可写根，无法在仓库内解决。

判定标准：

- 成功：apply_patch 能修改本文件，且 git diff --check 没有 whitespace error。
- 失败：仍出现 Windows restricted-token / split writable root sets 错误。

同时报告新会话可见的 writable roots。如果仍包含仓库目录、visualizations 目录和其他系统临时目录，说明单根沙箱没有生效。

## 3. 当前功能状态

上一会话已经完成 QuickDaily 1.8.8-beta 相关改动，包括：

- 全屏速录重写为编辑页风格。
- 全屏页顶栏为：左侧「编辑页」，中间居中标题，右侧下>、缩回悬浮窗、保存。
- 全屏页中 ?? 占位文本已替换为中文文案。
- 标题重复的“速录”后缀会被规范化，只保留一个。
- 右下角工具箭头会在关闭键盘、第二页工具、第一页工具之间切换，并使用编辑页一致的动画策略。
- 悬浮窗标题栏按钮已紧凑排列，文件名居中。
- App 图标恢复固定蓝色图标，莫奈取色不再控制 App 图标。
- 便签小部件独立目标页面、下>入口和任务小部件页面回归修复已在工作区中。
- 首页入口为「悬浮窗速录 / 全屏速录 / 编辑页面」，默认已恢复为编辑页面。

主要全屏实现文件：

- app/src/main/java/com/quickdaily/NoteEditActivity.kt

不要因为工作区很脏就回滚或清理；这些改动属于当前任务。

## 4. 已完成的验证证据

- :app:assembleDebug：通过。
- :app:testDebugUnitTest：通过。
- :app:lintDebug：通过；XML 报告统计为 0 errors / 53 warnings。
- git diff --check：通过。
- 有线 ADB 已安装 Debug APK，设备序列号：6XYPYTKF6DBIIB4L。
- 全屏页设备 UI 树已确认：
  - 标题边界为 [477,128][603,197]，中心 x=540。
  - 左侧显示「编辑页」。
  - 右侧显示「选择记录页面 / 缩回悬浮窗 / 保存」。
  - 输入区显示「写点什么...」。
  - 右下角状态可从「关闭键盘」切换到「打开第二页工具」。
  - 未发现 ?? 文本。
  - QuickDaily 崩溃缓冲区无 FATAL / AndroidRuntime。
- 设备最后已恢复 home_entry_mode=editor，并发送 Home 返回桌面。

除非用户再次要求，不要为了确认这些已经完成的内容而重复整套开发流程。

## 5. 有线 ADB 参考

ADB 路径：

C:\APP\投屏APP\Escrcpy\resources\extra\win\scrcpy\adb.exe

设备检查：

~~~powershell
$adb = 'C:\APP\投屏APP\Escrcpy\resources\extra\win\scrcpy\adb.exe'
$serial = '6XYPYTKF6DBIIB4L'
& $adb devices -l
& $adb -s $serial shell run-as com.quickdaily cat shared_prefs/QuickDaily.xml | Select-String 'home_entry_mode'
~~~

启动 App 首页：

~~~powershell
& $adb -s $serial shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.quickdaily/.LauncherActivity
~~~

不要直接启动非 exported 的 NoteEditActivity；应通过 LauncherActivity 或用户界面进入。

如果为了测试临时切换首页入口，结束前必须通过设置界面恢复：

home_entry_mode=editor

然后发送：

~~~powershell
& $adb -s $serial shell input keyevent 3
~~~

## 6. 新会话最终报告格式

新会话只需先报告：

1. apply_patch：通过或失败。
2. 失败时的完整错误，以及可见 writable roots。
3. 是否执行了任何源码修改。
4. 若继续做 ADB，报告设备是否仍为有线连接。

本手册的第一目标是确认“单可写根沙箱”是否生效，不是重新开发 QuickDaily。
