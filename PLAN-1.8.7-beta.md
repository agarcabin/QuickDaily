# Plan: QuickDaily 1.8.7-beta

_Locked via grill — by 赫尔墨斯 + Ivan_

## Goal

QuickDaily 1.8.7-beta 修复 4 个问题：1) 任务小部件自定义页面弹出的悬浮窗输入法延迟 ~1s（日记页面 Activity 秒出）；2) 工具栏行首符号（任务/列表/标题/引用/代码块等）点击后互相混叠；3) 行移动工具在无第三行时把两行合并；4) 新增有序列表按钮（默认隐藏）。版本更新为 `1.8.7-beta`，运行单元测试、lint、Release 构建并复制本地 APK，不发布 GitHub。

## Approach

1. **需求 1 — 悬浮窗 IME 秒出**：优化 `FloatingNoteImeController`（`FloatingNoteImeController.kt`）的 overlay 窗口 IME handoff 链路：缩短 160ms 超时兜底、尽早触发 `showSoftInput`、检查 `controlWindowInsetsAnimation` 的 fallback 是否造成额外延迟；确保从 `TaskWidget.addTask()`（自定义页面 → `FloatingNoteService` overlay → `imePolicy = OverlayInstant`）到键盘可见的耗时接近 Activity 路径。可用 `FloatingNoteTiming` 埋点对比。
2. **需求 2 — 行首符号互斥清理**：新增纯策略（如 `EditorLinePrefixPolicy` 或扩展 `EditorTextActionPolicy`），对所有行首符号类工具做互斥：TASK(`- [ ]`)、LIST(`- `)、HEADING(`# `)、QUOTE(`> `)、CODE_BLOCK(```` ``` ````)、ORDERED_LIST(`1. `)。点击某符号时，若当前行首是其他符号则先清除再应用新符号；BOLD/STRIKETHROUGH/INLINE_CODE 等行内符号不参与行首互斥（保持现状）。在 `EditorScreen.kt:653-709` 和 `NoteEditActivity.kt:912-990` 两处调用点接入，并写单元测试。
3. **需求 3 — 行移动合并 bug**：修复 `EditorTextActionPolicy.move()`（`EditorTextActionPolicy.kt:148-182`）：交换块时换行符不对称（`previousSegment` 含 `\n` 而末行 `blockSegment` 不含），导致 "A\nB" 上移 B 得 "BA\n"。修复后 "A\nB" 上移 B 应为 "B\nA"；补单元测试覆盖：两行上移/下移、首行上移（无操作）、末行下移（无操作）、多行中段移动。
4. **需求 4 — 有序列表按钮**：`EditorToolbarAction` 新增 `ORDERED_LIST("ordered_list", "有序列表")`；加入 `defaultOrder`（在 LIST 附近）但**不加入** `defaultVisible`（默认隐藏）；`CURRENT_SCHEMA_VERSION` 5→6；处理逻辑：自动递增序号（上一行是 `N. ` 则插入 `N+1. `，否则 `1. `），与 LIST 相同的互斥清理；两处调用点接入 + 单元测试。

## Key decisions & tradeoffs

- **需求 2 范围**：行首互斥包含 TASK/LIST/HEADING/QUOTE/CODE_BLOCK/ORDERED_LIST 全部；行内符号（BOLD/STRIKETHROUGH/INLINE_CODE/WIKILINK）不互斥，保持现状。
- **需求 4 格式**：有序列表自动递增（上一行 `1. ` → 本行 `2. `），非固定 `1. `；默认隐藏，用户可在设置里开启。
- **需求 1 验收**：既要悬浮窗 IME 接近秒出，也要优化 handoff 链路本身（缩短 timeout、减少 fallback 层数），不只在调用处打补丁。
- 悬浮窗内联目标菜单（`useInlineTargetMenu = true`）与任务入口均保留，不改动选择/保存语义。

## Risks / open questions

- ADB 真机验收 IME 延迟需要设备可用；若设备不可见，构建结果单独报告，设备验证不得伪造。
- `controlWindowInsetsAnimation` 的行为因 ROM 而异（MIUI/EMUI 可能不走该路径），优化需保留 fallback 且不能引入 ANR。
- 工具栏 schema 升级（5→6）需保证旧用户已保存的排序/可见性不丢失（迁移逻辑已在 `EditorToolbarPolicy` 中）。
- 当前工作树存在未提交改动（35 个文件 +2845/-650），必须保留，不覆盖、不清理、不提交，除非用户明确要求。

## Out of scope

- 不发布 GitHub Release / tag / PR。
- 不改动快速添加小部件、图片/附件/录音流程、任务勾选时间戳语义。
- 不重构 `EditorToolbarPolicy` 的迁移体系（仅新增枚举与 schema 版本递增）。
- 不记录整篇日记正文到 Beta 日志。
