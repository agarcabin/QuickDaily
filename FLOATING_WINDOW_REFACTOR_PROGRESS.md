# QuickDaily 悬浮窗重构进度表

版本基线：`1.7.2-beta`（`versionCode=58`）  
文档用途：交接给负责悬浮窗重构的开发者，记录已验证事实、当前边界和验收状态。  
更新时间：2026-07-23

## 总体结论

目标“从小米/鸿蒙侧边栏启动 QD，但速录界面不被系统包成黑色自由窗口”无法通过现有 Activity 的 Manifest 属性可靠解决。

当前设备证据：

- 小米侧边栏要求 QD 的 `LauncherActivity` 支持可调整大小，否则 QD 会从侧边栏应用名单消失。
- 将 `NoteEditActivity` 单独声明为不可调整大小，仍然会以小窗显示。
- 设备窗口信息同时显示 `fmt=TRANSPARENT` 和 `mWindowingMode=freeform`；黑色来自 MIUI 自由窗口容器/Surface，而不是 Compose 根 `Surface` 未设置透明。
- 真正绕开该容器，需要把速录 UI 承载到 `TYPE_APPLICATION_OVERLAY` 的悬浮窗服务中。

## 进度快照

| 阶段 | 状态 | 当前结果 | 交接出口 |
|---|---|---|---|
| 1. 问题复现与可行性评估 | 已完成 | 已在小米 `22041211AC` 上复现侧边栏 freeform 小窗及黑色背景 | 保留本表中的设备证据 |
| 2. 现有启动链路梳理 | 已完成 | `LauncherActivity → NoteEditActivity`，普通首页入口已分流 | 以对接手册的现状章节为准 |
| 3. Manifest/Activity 绕过实验 | 已完成 | 禁止调整大小不能阻止 MIUI freeform，且可能导致 QD 从侧边栏消失 | 不再继续堆 Activity 属性 |
| 4. Overlay 架构设计 | 待开始 | 需要确定 Service、OverlayHost、草稿状态和权限降级模型 | 输出设计评审结论 |
| 5. 编辑业务与承载层解耦 | 待开始 | 当前编辑状态仍主要位于 `NoteEditActivity`/Compose 作用域 | 抽成可被 Activity 和 Service 复用的状态层 |
| 6. `TYPE_APPLICATION_OVERLAY` 原型 | 待开始 | 未实现 | 能显示透明 UI、可输入、可关闭、可保存 |
| 7. 小米真实设备回归 | 待开始 | 需验证权限、键盘、侧边栏、进程回收 | 通过本手册验收矩阵 |
| 8. 鸿蒙/Android 兼容回归 | 待开始 | 需在实际目标设备确认厂商策略 | 不以构建成功替代设备验证 |
| 9. 1.7.2 beta 打包 | 未开始 | 当前不应把未完成 Overlay 重构混入稳定包 | 单独授权后再本地发版 |

## 当前稳定基线

当前已恢复并安装的稳定行为：

- `LauncherActivity` 保持 `android:resizeableActivity="true"`，保证 QD 仍能出现在小米侧边栏。
- `NoteEditActivity` 保持现有 Activity 速录实现，不增加 `resizeableActivity=false`。
- 现有编辑业务、保存、附件、标签、撤销/重做等逻辑不应在 Overlay 重构中改变语义。
- 透明实验代码已撤回；当前代码中的深色编辑面板仍为 `FloaterColors.background = Color(0xEE1B1B2B)`。

## 交接时必须说明

本项目当前是脏工作区，已有其他 1.7.2 beta 修改。重构开发者不得使用 `git reset --hard`、`git checkout --` 或清理整个工作区来“还原基线”。应只提交自己负责的文件和变更。

