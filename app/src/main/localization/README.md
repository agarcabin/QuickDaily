# QuickDaily 文案与多语言维护

## 新增语言

1. 复制 src/main/res/values-en/ 为新的 values-xx/ 资源目录，并只修改资源值。
2. 在 LocaleController.kt 的 SupportedLanguage 注册表中添加一行语言元数据：系统语言标签、显示名称资源和资源目录对应的 tag。
3. 运行：

       .\gradlew.bat :app:verifyLocalization --offline

   发布前再运行 :app:verifyLocalizationStrict，它会要求英语资源完整；新增语言的缺译会写入 build/reports/localization/missing-translations.txt。

## 翻译约束

- 不修改资源 key、格式占位符（例如 %1$s）、换行数量和复数结构。
- UiText.Resource、UiText.Plural 用于应用拥有的可翻译文案；UiText.Raw 仅用于用户 Vault 内容、Markdown、文件路径、版本日志正文和外部错误文本。
- 不把用户自己的 Vault 内容、模板正文、Obsidian 原文或路径放进翻译资源。
- 第一阶段暂不要求版本日志、赞赏内容和社区联系方式进入翻译层。

## 硬编码检查

受保护的核心 Kotlin 入口列在 protected_sources.txt。新增用户可见文案必须进入资源层；兼容性文本、用户输入和调试日志若确实不能翻译，要在对应行使用明确的 localization-legacy 标记，并说明原因。
