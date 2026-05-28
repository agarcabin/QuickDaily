# QuickDairy — Obsidian 秒开日记 Android App

[![Android](https://img.shields.io/badge/Android-8.0%2B-green)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-blue)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09-purple)](https://developer.android.com/compose)

专为 Obsidian 用户设计的 Android 日记速记工具。解决 Obsidian 移动端启动慢的痛点——**掏出手机 → 秒开 → 写一句话 → 放回口袋**。

## ✨ 功能

- ⚡ **秒开** — 打开即显示今天日记，冷启动 < 500ms
- 🔄 **Obsidian 无缝集成** — 读取 `.obsidian/daily-notes.json` 配置，自动对齐路径/格式/模板
- 📝 **Markdown 渲染** — 标题、任务勾选（可交互）、列表、粗斜体、链接
- 💾 **实时保存** — 编辑时防抖自动保存（500ms），切后台立即写入
- 🏠 **桌面便签小部件** — 自适应大小，显示今日日记全文
- 📌 **快速添加小部件** — 1×1，点击弹出悬浮窗，支持时间戳和锚点插入
- 🎨 **Material3 主题** — Android 15 Edge-to-Edge 适配

## 📦 下载

> 最新版本：**beta0.16**

- [GitHub Releases](../../releases)
- 酷安：[@附近的人](https://www.coolapk.com/u/400522)

## 🛠 技术栈

| 技术 | 版本 |
|------|------|
| Kotlin | 1.9.24 |
| Jetpack Compose | BOM 2024.09.00 |
| Material3 | — |
| AGP | 8.2.0 |
| Gradle | 8.4 |
| minSdk | 26 |
| targetSdk | 35 |

## 🚀 构建

```bash
git clone https://github.com/agarcabin/QuickDaily.git
cd QuickDairy
./gradlew assembleDebug
```

需要 Android SDK 35+，在 `local.properties` 中配置 `sdk.dir`。

## 📄 许可证

MIT License
