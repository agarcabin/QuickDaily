package com.quickdairy.markdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Renderer ────────────────────────────────────────────

@Composable
fun MdRenderer(
    text: String,
    onToggleCheckbox: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lines = remember(text) { parseLines(text) }
    Column(modifier = modifier.fillMaxWidth()) {
        lines.forEach { line ->
            when (line) {
                is MdLine.Blank -> Spacer(Modifier.height(8.dp))
                is MdLine.Heading -> {
                    val scale = when (line.level) {
                        1 -> 1.5f; 2 -> 1.3f; 3 -> 1.15f
                        4 -> 1.1f; else -> 1.0f
                    }
                    BasicText(
                        text = buildAnnotated(line.text),
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
                            .padding(vertical = 2.dp)
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
                        BasicText(
                            text = buildAnnotated(line.text),
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
                            style = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicText(
                            text = buildAnnotated(line.text),
                            style = LocalTextStyle.current
                        )
                    }
                }
                is MdLine.Plain -> {
                    BasicText(
                        text = buildAnnotated(line.text),
                        style = LocalTextStyle.current,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

// ── Inline Parser ────────────────────────────────────────

private fun buildAnnotated(raw: String): AnnotatedString {
    return buildAnnotatedString {
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
                        withStyle(SpanStyle(color = Color.Gray)) {
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
                            color = Color(0xFF1565C0),
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
}

// ── Line Model ───────────────────────────────────────────

sealed class MdLine {
    data object Blank : MdLine()
    data class Heading(val level: Int, val text: String) : MdLine()
    data class Task(val index: Int, val checked: Boolean, val text: String) : MdLine()
    data class Bullet(val text: String) : MdLine()
    data class Plain(val text: String) : MdLine()
}

// ── Parser ───────────────────────────────────────────────

private val ORDERED_LIST_RE = Regex("^\\d+\\.\\s.*")
private val TOGGLE_CHECK_RE = Regex("- \\[x\\]", RegexOption.IGNORE_CASE)

fun parseLines(markdown: String): List<MdLine> {
    val lines = markdown.split("\n")
    val result = mutableListOf<MdLine>()
    var taskIndex = 0

    for (line in lines) {
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> result.add(MdLine.Blank)
            // 标题
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length
                val text = trimmed.drop(level).trimStart()
                result.add(MdLine.Heading(level.coerceIn(1, 6), text))
            }
            // 任务勾选（必须在普通列表之前判断）
            trimmed.startsWith("- [ ]") || trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]") -> {
                val checked = trimmed[3] == 'x' || trimmed[3] == 'X'
                val text = trimmed.drop(6).trimStart()
                result.add(MdLine.Task(taskIndex++, checked, text))
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

// ── 编辑器中切换勾选 ────────────────────────────────────

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
