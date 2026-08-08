package com.quickdaily.markdown

import android.graphics.BitmapFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickdaily.WidgetContentLoader
import com.quickdaily.TagHighlightPolicy

// ── Renderer ────────────────────────────────────────────

@Composable
fun MdRenderer(
    text: String,
    vaultBasePath: String? = null,
    imageStoragePath: String? = null,
    onToggleCheckbox: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lines = remember(text) { parseLines(text) }
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        lines.forEach { line ->
            when (line) {
                is MdLine.Blank -> Spacer(Modifier.height(8.dp))
                is MdLine.Heading -> {
                    val scale = when (line.level) {
                        1 -> 1.5f; 2 -> 1.3f; 3 -> 1.15f
                        4 -> 1.1f; else -> 1.0f
                    }
                    TagHighlightedBasicText(
                        raw = line.text,
                        style = LocalTextStyle.current.copy(
                            fontSize = (MaterialTheme.typography.bodyLarge.fontSize * scale),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = (20 * scale).sp
                        ),
                        modifier = Modifier.padding(top = if (line.level == 1) 8.dp else 4.dp)
                    )
                }
                is MdLine.Task -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = onToggleCheckbox != null) {
                                onToggleCheckbox?.invoke(line.index)
                            }
                            .padding(
                                start = (line.indentLevel * WidgetContentLoader.SUBTASK_INDENT_DP).dp,
                                top = 2.dp,
                                bottom = 2.dp,
                            )
                    ) {
                        Icon(
                            imageVector = if (line.checked) Icons.Outlined.CheckBox
                                else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = if (line.checked) "已勾选" else "未勾选",
                            tint = if (line.checked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        TagHighlightedBasicText(
                            raw = line.text,
                            style = LocalTextStyle.current.copy(
                                color = if (line.checked)
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
                is MdLine.Bullet -> {
                    Row(modifier = Modifier.padding(start = 8.dp, top = 1.dp, bottom = 1.dp)) {
                        BasicText(
                            text = AnnotatedString("•"),
                            style = LocalTextStyle.current.copy(
                                color = colors.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        TagHighlightedBasicText(
                            raw = line.text,
                            style = LocalTextStyle.current.copy(color = colors.onSurface)
                        )
                    }
                }
                is MdLine.Image -> {
                    val paths = remember(line.path, vaultBasePath, imageStoragePath) {
                        val primary = resolveImagePath(line.path, vaultBasePath)
                        val fallback = if (imageStoragePath != null && !line.path.startsWith("/")) {
                            resolveImagePath("${imageStoragePath.trimEnd('/')}/${line.path.trimStart('/')}", vaultBasePath)
                        } else null
                        Pair(primary, fallback)
                    }
                    val bitmap = remember(paths) {
                        try {
                            BitmapFactory.decodeFile(paths.first)
                                ?: paths.second?.let { BitmapFactory.decodeFile(it) }
                        } catch (_: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = line.alt,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        BasicText(
                            text = AnnotatedString(line.alt.ifEmpty { "[图片: ${line.path}]" }),
                                style = LocalTextStyle.current.copy(color = colors.onSurfaceVariant),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                is MdLine.Plain -> {
                    TagHighlightedBasicText(
                        raw = line.text,
                        style = LocalTextStyle.current.copy(color = colors.onSurface),
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

// ── Inline Parser ────────────────────────────────────────

@Composable
private fun buildAnnotated(raw: String): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    val annotated = buildAnnotatedString {
        var i = 0
        while (i < raw.length) {
            when {
                // **粗体**
                raw.startsWith("**", i) -> {
                    val end = raw.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(raw.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(raw[i]); i++
                    }
                }
                // *斜体* / _斜体_
                (raw.startsWith("*", i) && !raw.startsWith("**", i)) || raw.startsWith("_", i) -> {
                    val marker = if (raw[i] == '*') "*" else "_"
                    val end = raw.indexOf(marker, i + 1)
                    if (end > i && end - i > 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(raw.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(raw[i]); i++
                    }
                }
                // ~~删除线~~
                raw.startsWith("~~", i) -> {
                    val end = raw.indexOf("~~", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(color = colors.onSurfaceVariant)) {
                            append(raw.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(raw[i]); i++
                    }
                }
                // [链接](url)
                raw.startsWith("[", i) -> {
                    val bracketEnd = raw.indexOf("](", i)
                    val parenEnd = if (bracketEnd > i) raw.indexOf(")", bracketEnd + 2) else -1
                    if (bracketEnd > i && parenEnd > bracketEnd) {
                        val linkText = raw.substring(i + 1, bracketEnd)
                        withStyle(SpanStyle(
                            color = colors.primary,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        )) {
                            append(linkText)
                        }
                        i = parenEnd + 1
                    } else {
                        append(raw[i]); i++
                    }
                }
                else -> {
                    append(raw[i]); i++
                }
            }
        }
    }
    return AnnotatedString.Builder(annotated).apply {
        TagHighlightPolicy.ranges(annotated.text).forEach { range ->
            addStyle(
                SpanStyle(color = colors.primary),
                range.start,
                range.end,
            )
        }
    }.toAnnotatedString()
}

@Composable
private fun TagHighlightedBasicText(
    raw: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val annotated = buildAnnotated(raw)
    BasicText(
        text = annotated,
        style = style,
        modifier = modifier,
    )
}

// ── Line Model ───────────────────────────────────────────

sealed class MdLine {
    data object Blank : MdLine()
    data class Heading(val level: Int, val text: String) : MdLine()
    data class Image(val path: String, val alt: String) : MdLine()
    data class Task(val index: Int, val checked: Boolean, val text: String, val indentLevel: Int = 0) : MdLine()
    data class Bullet(val text: String) : MdLine()
    data class Plain(val text: String) : MdLine()
}

// ── Parser ───────────────────────────────────────────────

private val ORDERED_LIST_RE = Regex("^\\d+\\.\\s.*")
private val IMAGE_INLINE_RE = Regex("""^!\[([^\]]*)\]\(([^)]+)\)\s*$""")
private val TOGGLE_CHECK_RE = Regex("- \\[x\\]", RegexOption.IGNORE_CASE)
private val IMAGE_WIKI_RE = Regex("""^!\[\[([^\]|]+)(?:\|([^\]]*))?\]\]\s*$""")

fun parseLines(markdown: String): List<MdLine> {
    val lines = markdown.split("\n")
    val result = mutableListOf<MdLine>()
    var taskIndex = 0
    val taskIndentStack = ArrayDeque<TaskIndentEntry>()

    for (line in lines) {
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> result.add(MdLine.Blank)
            // 图片行 ![](path) 或 ![alt](path)
            trimmed.matches(IMAGE_INLINE_RE) -> {
                val match = IMAGE_INLINE_RE.find(trimmed)!!
                result.add(MdLine.Image(match.groupValues[2], match.groupValues[1]))
            }
            // ![[filename]] wikilink 图片格式
            trimmed.matches(IMAGE_WIKI_RE) -> {
                val match = IMAGE_WIKI_RE.find(trimmed)!!
                val path = match.groupValues[1]
                val alt = match.groupValues.getOrElse(2) { "" }
                result.add(MdLine.Image(path, alt))
            }
            // 标题
            trimmed.matches(Regex("^#{1,6} .*")) -> {
                val level = trimmed.takeWhile { it == '#' }.length
                val text = trimmed.drop(level).trimStart()
                result.add(MdLine.Heading(level.coerceIn(1, 6), text))
            }
            // 任务勾选（必须在普通列表之前判断）
            trimmed.startsWith("- [ ]") || trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]") -> {
                val leadingWhitespace = line.takeWhile { it == ' ' || it == '\t' }
                val indentColumns = leadingWhitespace.fold(0) { total, char ->
                    total + if (char == '\t') 4 else 1
                }
                while (taskIndentStack.isNotEmpty() && taskIndentStack.last().indentColumns >= indentColumns) {
                    taskIndentStack.removeLast()
                }
                val indentLevel = taskIndentStack.lastOrNull()?.let { it.level + 1 } ?: 0
                val checked = trimmed[3] == 'x' || trimmed[3] == 'X'
                val text = trimmed.drop(6).trimStart()
                result.add(MdLine.Task(taskIndex++, checked, text, indentLevel))
                taskIndentStack.addLast(TaskIndentEntry(indentColumns, indentLevel))
            }
            // 无序列表
            trimmed.startsWith("- ") -> {
                result.add(MdLine.Bullet(trimmed.drop(2).trimStart()))
            }
            // 有序列表
            trimmed.matches(ORDERED_LIST_RE) -> {
                val text = trimmed.dropWhile { it != ' ' }.trimStart()
                result.add(MdLine.Bullet(text))
            }
            // 普通文本
            else -> result.add(MdLine.Plain(trimmed))
        }
    }
    return result
}

private data class TaskIndentEntry(
    val indentColumns: Int,
    val level: Int,
)

// ── 编辑器中切换勾选 ────────────────────────────────────

/** 解析 Markdown 图片路径为本地文件系统绝对路径 */
private fun resolveImagePath(path: String, vaultBasePath: String?): String {
    if (vaultBasePath == null) return path
    // 已经是绝对路径或 URI
    if (path.startsWith("/") || path.startsWith("file:") || Regex("^[A-Za-z]:").containsMatchIn(path)) {
        return path
    }
    // 相对路径，拼接 vault 根路径
    return "${vaultBasePath.trimEnd('/')}/${path.trimStart('/')}"
}

/** 在原始文本中切换指定位置的任务勾选状态，返回新文本 */
fun toggleTaskCheck(text: String, taskIndex: Int): String {
    val lines = text.split("\n")
    var count = 0
    val newLines = lines.map { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("- [ ]") || trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]")) {
            if (count == taskIndex) {
                count++
                if (trimmed.startsWith("- [ ]")) {
                    line.replaceFirst("- [ ]", "- [x]")
                } else {
                    line.replaceFirst(TOGGLE_CHECK_RE, "- [ ]")
                }
            } else {
                count++
                line
            }
        } else line
    }
    return newLines.joinToString("\n")
}
