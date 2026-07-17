package com.quickdaily.util

import java.io.File

/**
 * 扫描 Obsidian vault 提取所有已存在的 tag。
 *
 * 支持以下 tag 格式：
 * - Frontmatter `tags: [tag1, tag2]` 或 `tags:\n  - tag1`
 * - 行内 `#tag` / `#tag/subtag`（排除标题 `# ` 和 `## ` 等）
 *
 * 使用进程内缓存，避免重复扫描。
 */
object TagScanner {

    private var cachedTags: List<String>? = null
    private var cachedVaultPath: String = ""
    private var cacheTime: Long = 0L

    private const val CACHE_TTL_MS = 60_000L
    private const val MAX_SCAN_DEPTH = 15
    private const val INLINE_SAMPLE_SIZE = 4096

    /** 行内 tag：前面是行首或空白，# 后跟 [\w/-] 字符 */
    private val inlineTagRegex = Regex("""(?:^|\s)#([\w/\-]+)""")

    // ── Public API ──────────────────────────────────────────

    /**
     * 获取 vault 中的 tag 列表（带缓存）。
     * 缓存有效期为 [CACHE_TTL_MS]，或 vault 路径变更时失效。
     */
    fun getTags(vaultPath: String): List<String> {
        val now = System.currentTimeMillis()
        if (vaultPath.isBlank()) return emptyList()
        if (cachedTags != null && cachedVaultPath == vaultPath && now - cacheTime < CACHE_TTL_MS) {
            return cachedTags!!
        }
        val tags = scan(vaultPath)
        cachedTags = tags
        cachedVaultPath = vaultPath
        cacheTime = now
        return tags
    }

    /** 强制清除缓存，下次调用 [getTags] 时重新扫描。 */
    fun invalidateCache() {
        cachedTags = null
    }

    // ── Scan ───────────────────────────────────────────────

    private fun scan(vaultPath: String): List<String> {
        val vaultDir = File(vaultPath)
        if (!vaultDir.isDirectory || !vaultDir.exists()) return emptyList()

        val tags = linkedSetOf<String>()
        val mdFiles = vaultDir.walkTopDown()
            .maxDepth(MAX_SCAN_DEPTH)
            .filter { file ->
                if (file.isHidden) return@filter false
                if (file.parent?.let { File(it).isHidden } == true) return@filter false
                file.isFile && file.name.endsWith(".md", ignoreCase = true)
            }
            .toList()

        for (file in mdFiles) {
            try {
                val content = file.readText(Charsets.UTF_8)
                val parsed = ContentUtil.parseFrontmatter(content)

                // Frontmatter tags
                if (parsed.hasFrontmatter) {
                    tags.addAll(parseFrontmatterTags(parsed.frontmatter))
                }

                // Inline tags from body (only scan first 4k for perf)
                val body = if (parsed.hasFrontmatter) parsed.body else content
                val sample = if (body.length <= INLINE_SAMPLE_SIZE) body else body.substring(0, INLINE_SAMPLE_SIZE)
                tags.addAll(parseInlineTags(sample))
            } catch (_: Exception) {
                // skip unreadable / binary / corrupt files
            }
        }

        return tags.toList().sorted()
    }

    // ── Frontmatter tag parser ────────────────────────────

    /**
     * 从 YAML frontmatter 中提取 `tags:` 字段的值。
     * 支持格式：
     *   tags: tag1
     *   tags: tag1, tag2
     *   tags: [tag1, tag2]
     *   tags:\n  - tag1\n  - tag2
     */
    private fun parseFrontmatterTags(frontmatter: String): List<String> {
        val tags = mutableListOf<String>()
        val lines = frontmatter.lines()
        var inTagsBlock = false

        for (line in lines) {
            val trimmed = line.trim()
            when {
                // tags: tag1, tag2 或 tags: [tag1, tag2]
                trimmed.startsWith("tags:") -> {
                    inTagsBlock = false
                    val value = trimmed.substringAfter("tags:", "").trim()
                    if (value.isNotEmpty()) {
                        tags.addAll(parseYamlTagList(value))
                    } else {
                        // tags: 后面没有内容，下一行开始是列表
                        inTagsBlock = true
                    }
                }
                //   - tag1    (YAML list item)
                inTagsBlock && trimmed.startsWith("- ") -> {
                    val tag = trimmed.removePrefix("- ").trim()
                        .removeSurrounding("\"")
                        .removeSurrounding("'")
                    if (tag.isNotBlank()) tags.add(tag)
                }
                // 新 key 结束 tags 块
                inTagsBlock && trimmed.contains(":") && !trimmed.startsWith("-") -> {
                    inTagsBlock = false
                }
            }
        }

        return tags
    }

    /**
     * 解析 `tags:` 后面的值。
     *   [tag1, tag2]  → 拆分成列表
     *   tag1, tag2    → 逗号分隔
     *   tag1          → 单值
     */
    private fun parseYamlTagList(value: String): List<String> {
        val trimmed = value.trim()
        // [tag1, tag2, "tag3"]
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed.removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                .filter { it.isNotBlank() }
        }
        // tag1, tag2, tag3
        return trimmed.split(",")
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            .filter { it.isNotBlank() }
    }

    // ── Inline tag parser ─────────────────────────────────

    private fun parseInlineTags(sample: String): List<String> {
        return inlineTagRegex.findAll(sample)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .toList()
    }
}
