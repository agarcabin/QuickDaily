package com.quickdaily.util

/** 解析后的 Markdown 内容，frontmatter 与 body 分离 */
data class ParsedContent(
    val frontmatter: String,
    val body: String,
    val hasFrontmatter: Boolean
)

object ContentUtil {

    private val frontmatterRegex = Regex("""^---\n(.*?)\n---\n?""", RegexOption.DOT_MATCHES_ALL)

    /**
     * 解析 Markdown 内容，分离 YAML frontmatter 和正文。
     *
     * 格式：文件以 `---\n` 开头，中间是 frontmatter 内容，
     * 之后以 `\n---` 结尾（后面可能跟换行符和正文）。
     *
     * 如果没有 frontmatter，返回的 body = 原始内容。
     */
    fun parseFrontmatter(content: String): ParsedContent {
        val normalized = content.replace("\r\n", "\n")
        val match = frontmatterRegex.find(normalized)
        return if (match != null) {
            ParsedContent(
                frontmatter = match.groupValues[1].trimEnd(),
                body = normalized.substring(match.value.length),
                hasFrontmatter = true
            )
        } else {
            ParsedContent("", normalized, false)
        }
    }

    /**
     * 如果有 frontmatter，将 frontmatter 和 body 重组为完整 Markdown 内容。
     * 如果 frontmatter 为空，直接返回 body。
     */
    fun reconstructWithFrontmatter(frontmatter: String, body: String): String {
        if (frontmatter.isEmpty()) return body
        return "---\n$frontmatter\n---\n$body"
    }

    /**
     * 从原始内容中移除 frontmatter 前缀（仅当存在时），
     * 返回纯 body。用于桌面便签等只读展示场景。
     */
    fun stripFrontmatter(content: String): String {
        val parsed = parseFrontmatter(content)
        return if (parsed.hasFrontmatter) parsed.body else parsed.body
    }

    /**
     * 读取文件并解析 frontmatter，返回 (frontmatter, body, hasFrontmatter)。
     * 文件不存在或读取失败时返回 ("", "", false)。
     */
    fun readWithFrontmatter(path: String): ParsedContent {
        val content = FileUtil.read(path)
        if (content.isEmpty()) return ParsedContent("", "", false)
        return parseFrontmatter(content)
    }
}
