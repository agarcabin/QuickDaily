# QuickDaily — Obsidian 闪念速记 · Android 小部件

<p align="center">
  <img width="120" height="120" alt="QuickDaily" src="https://github.com/user-attachments/assets/a2718d12-e216-4b45-8f7a-b2b68ce0cefb" style="border-radius:24px" />
</p>

<p align="center">
  <b>掏出手机 → 小部件秒开 → 速录一键保存至ob库 → 放回口袋</b>
</p>

<p align="center">
  <a href="https://github.com/agarcabin/QuickDaily/releases/tag/v1.4">
    <img src="https://img.shields.io/badge/下载-v1.4-brightgreen?style=for-the-badge&logo=github" alt="Download v1.4" />
  </a>
  <a href="https://github.com/agarcabin/QuickDaily/releases">
    <img src="https://img.shields.io/github/v/release/agarcabin/QuickDaily?style=for-the-badge&logo=github" alt="GitHub Release" />
  </a>
  <a href="https://www.coolapk.com/u/400522">
    <img src="https://img.shields.io/badge/酷安-@附近的人-ff6900?style=for-the-badge" alt="CoolAPK" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-green?logo=android" />
  <img src="https://img.shields.io/badge/Kotlin-1.9.24-blue?logo=kotlin" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09-purple?logo=jetpackcompose" />
  <img src="https://img.shields.io/badge/License-MIT-yellow" />
</p>

<p align="center">
  <img width="240" alt="QuickDaily 录屏演示" src="https://github.com/user-attachments/assets/a2718d12-e216-4b45-8f7a-b2b68ce0cefb" />
</p>

---

## 为什么需要 QuickDaily？

**Obsidian 移动端什么都好，就是启动太慢了。** 等它加载完，灵感早飞了。

QuickDaily 只做一件事：**让你用最快的速度记下今天的一句话**。

- 它在后台直接读写 Obsidian vault 的日记文件
- 冷启动 **< 500ms**，打开就是今天的日记
- 写完了直接锁屏放回口袋，一切自动保存

---

## 功能一览

| | |
|---|---|
| **⚡ 秒开** — 打开即写，冷启动 <500ms | **🧩 Obsidian 无缝集成** — 自动读取日记配置、路径、模板 |
| **📑 Markdown 渲染** — 标题、列表、任务勾选、粗斜体、链接均支持 | **💾 实时保存** — 防抖 500ms 自动写入，切后台立即落盘 |
| **🏠 桌面便签小部件** — 自适应大小，主屏浏览今日日记全文 | **🪟 快速添加悬浮窗** — 1x1 小部件，弹出透明悬浮窗一键速记 |
| **📥 任务录入** — 悬浮窗支持任务模式，双击切换完成状态 | **🖼️ 图片录入** — 支持从悬浮窗批量导入图片到日记 |
| **📆 全日期格式支持** — 新增周数日期格式支持 | **🔍 Frontmatter 过滤** — 可选隐藏日记文件头，专注内容 |
| **🔖 速记锚点** — 自定义锚点文本，自动插入指定位置 | **🎯 今日任务小部件** — 桌面直接查看/勾选待办任务 |

---

## 更多特性

| | |
|---|---|
| 🖍️ **时间戳** — 7 种格式可配置，兼容 Thino / Knomo | ⏱️ **快捷磁贴** — 下拉通知栏一键速记 |
| 📱 **桌面快捷图标** — 长按按钮一键创建桌面图标 | 🎨 **Material3 主题** — Android 15 Edge-to-Edge |
| 🖼️ **自定义小部件图片** — 桌面便签自定义背景 | 🔄 **自动检测更新** — 启动时检测 GitHub 新版本 |

---

## 演示视频

🎬 [点击观看演示视频 (Bilibili)](https://www.bilibili.com/video/BV1smTm6wE4t/)

---

## 下载

> **最新版本：v1.4**

| 渠道 | 链接 |
|------|------|
| GitHub Release | [QuickDaily-1.4.apk](https://github.com/agarcabin/QuickDaily/releases/tag/v1.4) |
| 蓝奏云（国内分流） | [点此下载](https://github.com/agarcabin/QuickDaily/releases) (密码: fjdr) |
| 酷安社区 | [@附近的人](https://www.coolapk.com/u/400522) |
| QQ 交流群 | [1050092886](https://qm.qq.com/q/G2zLL5RpiU) — Obsidian 许愿屋 |

---


## 打赏
如果你觉得对你很有帮助的话，请我喝杯咖啡吧~
![赞赏码](https://github.com/agarcabin/QuickDaily/blob/main/%E8%B5%9E%E8%B5%8F%E7%A0%81.png)

## 技术栈

| 技术 | 版本 |
|------|------|
| Kotlin | 1.9.24 |
| Jetpack Compose | BOM 2024.09.00 |
| Material3 | Material Design 3 |
| AGP | 8.2.0 |
| Gradle | 8.4 |
| minSdk | 26 (Android 8.0) |
| targetSdk | 35 (Android 15) |
| 架构 | MVVM + Compose + Coroutines |

---

## 构建

```bash
git clone https://github.com/agarcabin/QuickDaily.git
cd QuickDaily
./gradlew assembleRelease
```

需要 Android SDK 35+，在 `local.properties` 中配置 `sdk.dir`。

---

## 许可

MIT License — 详见 [LICENSE](LICENSE)
