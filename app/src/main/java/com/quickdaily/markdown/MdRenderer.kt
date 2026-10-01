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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickdaily.WidgetContentLoader
import com.quickdaily.TagHighlightPolicy
import com.quickdaily.DisplayRange
import com.quickdaily.DisplayStyleKind
import com.quickdaily.DisplayText
import com.quickdaily.HiddenFlowItem
import com.quickdaily.HiddenTextPolicy
import com.quickdaily.InlineDisplayPolicy
import com.quickdaily.InlineSurface
import com.quickdaily.SourceDocument
import com.quickdaily.TaskActionRef
import com.quickdaily.util.SafVirtualPath
import com.quickdaily.util.VaultStorage

// ── Renderer ────────────────────────────────────────────

@Composable
fun MdRenderer(
    text: String,
    vaultBasePath: String? = null,
    imageStoragePath: String? = null,
    hiddenDisplayTexts: List<String> = emptyList(),
    onToggleCheckbox: ((TaskActionRef) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lines = remember(text, hiddenDisplayTexts) {
        prepareMdLines(text, hiddenDisplayTexts)
    }
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        lines.forEach { prepared ->
            if (prepared.collapseLine) return@forEach
            val line = prepared.line
            when (line) {
                is MdLine.Blank -> Spacer(Modifier.height(8.dp))
                is MdLine.Heading -> {
                    val scale = when (line.level) {
                        1 -> 1.5f; 2 -> 1.3f; 3 -> 1.15f
                        4 -> 1.1f; else -> 1.0f
                    }
                    TagHighlightedBasicText(
                        display = prepared.display,
                        hiddenRanges = prepared.hiddenRanges,
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
                                onToggleCheckbox?.invoke(line.actionRef)
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
                            contentDescription = stringResource(
                                if (line.checked) com.quickdaily.R.string.qd_common_checked
                                else com.quickdaily.R.string.qd_common_unchecked,
                            ),
                            tint = if (line.checked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        TagHighlightedBasicText(
                            display = prepared.display,
                            hiddenRanges = prepared.hiddenRanges,
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
                            display = prepared.display,
                            hiddenRanges = prepared.hiddenRanges,
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
                    val bitmap = remember(paths, context) {
                        decodeImageBitmap(context, paths.first)
                            ?: paths.second?.let { decodeImageBitmap(context, it) }
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
                            text = buildAnnotated(prepared.display, prepared.hiddenRanges),
                            style = LocalTextStyle.current.copy(color = colors.onSurfaceVariant),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                is MdLine.Plain -> {
                    TagHighlightedBasicText(
                        display = prepared.display,
                        hiddenRanges = prepared.hiddenRanges,
                        style = LocalTextStyle.current.copy(color = colors.onSurface),
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

private data class PreparedMdLine(
    val line: MdLine,
    val display: DisplayText,
    val hiddenRanges: List<DisplayRange>,
    val collapseLine: Boolean,
)

private fun prepareMdLines(
    text: String,
    hiddenDisplayTexts: List<String>,
): List<PreparedMdLine> {
    val lines = parseLines(text)
    val sourceLines = SourceDocument.splitLines(text)
    val displays = lines.map { line ->
        when (line) {
            MdLine.Blank -> DisplayText("")
            is MdLine.Image -> DisplayText(line.alt.ifEmpty { "[图片: ${line.path}]" })
            is MdLine.Heading -> InlineDisplayPolicy.render(line.text, InlineSurface.EDITOR)
            is MdLine.Task -> InlineDisplayPolicy.render(line.text, InlineSurface.EDITOR)
            is MdLine.Bullet -> InlineDisplayPolicy.render(line.text, InlineSurface.EDITOR)
            is MdLine.Plain -> InlineDisplayPolicy.render(line.text, InlineSurface.EDITOR)
        }
    }
    val flow = lines.mapIndexed { index, line ->
            HiddenFlowItem(
                display = displays[index],
                sourceText = sourceLines.getOrNull(index)?.rawLine,
                barrierBefore = line is MdLine.Image,
                barrierAfter = line is MdLine.Image,
            )
        }
    val hidden = HiddenTextPolicy.apply(flow, hiddenDisplayTexts)
    val collapsed = HiddenTextPolicy.collapseExtraRows(flow, hidden)
    return lines.indices.map { index ->
        PreparedMdLine(lines[index], displays[index], hidden[index], collapsed[index])
    }
}

// ── Inline Parser ────────────────────────────────────────

@Composable
private fun buildAnnotated(
    display: DisplayText,
    hiddenRanges: List<DisplayRange> = emptyList(),
): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return AnnotatedString.Builder().apply {
        append(display.text)
        display.styles.forEach { range ->
            addStyle(
                when (range.kind) {
                    DisplayStyleKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    DisplayStyleKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    DisplayStyleKind.STRIKETHROUGH -> SpanStyle(color = colors.onSurfaceVariant)
                    DisplayStyleKind.CODE -> SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    DisplayStyleKind.LINK -> SpanStyle(
                        color = colors.primary,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    )
                    DisplayStyleKind.WIKILINK -> SpanStyle(color = colors.primary)
                },
                range.start,
                range.end,
            )
        }
        TagHighlightPolicy.ranges(display.text).forEach { range ->
            addStyle(SpanStyle(color = colors.primary), range.start, range.end)
        }
        hiddenRanges.forEach { range ->
            addStyle(
                SpanStyle(
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    textGeometricTransform = TextGeometricTransform(scaleX = 0f),
                ),
                range.start,
                range.end,
            )
        }
    }.toAnnotatedString()
}

@Composable
private fun TagHighlightedBasicText(
    display: DisplayText,
    hiddenRanges: List<DisplayRange>,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val annotated = buildAnnotated(display, hiddenRanges)
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
    data class Task(
        val index: Int,
        val checked: Boolean,
        val text: String,
        val indentLevel: Int = 0,
        val actionRef: TaskActionRef = TaskActionRef(index, "", ""),
    ) : MdLine()
    data class Bullet(val text: String) : MdLine()
    data class Plain(val text: String) : MdLine()
}

// ── Parser ───────────────────────────────────────────────

private val ORDERED_LIST_RE = Regex("^\\d+\\.\\s.*")
private val IMAGE_INLINE_RE = Regex("""^!\[([^\]]*)\]\(([^)]+)\)\s*$""")
private val TOGGLE_CHECK_RE = Regex("- \\[x\\]", RegexOption.IGNORE_CASE)
private val IMAGE_WIKI_RE = Regex("""^!\[\[([^\]|]+)(?:\|([^\]]*))?\]\]\s*$""")

fun parseLines(markdown: String): List<MdLine> {
    val result = mutableListOf<MdLine>()
    var taskIndex = 0
    val taskIndentStack = ArrayDeque<TaskIndentEntry>()

    for (record in SourceDocument.splitLines(markdown)) {
        val line = record.rawLine
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
                result.add(
                    MdLine.Task(
                        index = taskIndex++,
                        checked = checked,
                        text = text,
                        indentLevel = indentLevel,
                        actionRef = TaskActionRef(record.index, record.rawLine, record.separator),
                    )
                )
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

private fun decodeImageBitmap(context: android.content.Context, path: String): android.graphics.Bitmap? {
    if (SafVirtualPath.isSafPath(path)) {
        val document = VaultStorage.documentForPath(context, path) ?: return null
        return runCatching {
            context.contentResolver.openInputStream(document.uri)?.use(BitmapFactory::decodeStream)
        }.getOrNull()
    }
    return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
}

/** 在原始文本中切换指定位置的任务勾选状态，返回新文本 */
fun toggleTaskCheck(text: String, taskIndex: Int): String {
    val lines = SourceDocument.splitLines(text)
    var count = 0
    var targetIndex = -1
    lines.forEach { record ->
        val trimmed = record.rawLine.trim()
        if (trimmed.startsWith("- [ ]") || trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]")) {
            if (count == taskIndex) {
                targetIndex = record.index
            }
            count++
        }
    }
    val record = lines.getOrNull(targetIndex) ?: return text
    val toggled = toggleEditorTaskLine(record.rawLine) ?: return text
    return SourceDocument.from(text).replaceLine(targetIndex, toggled)
}

fun toggleTaskCheck(text: String, actionRef: TaskActionRef): String {
    val record = SourceDocument.splitLines(text).getOrNull(actionRef.lineIndex) ?: return text
    if (record.rawLine != actionRef.rawLine || record.separator != actionRef.lineSeparator) return text
    val toggled = toggleEditorTaskLine(record.rawLine) ?: return text
    return SourceDocument.from(text).replaceLine(actionRef.lineIndex, toggled)
}

private fun toggleEditorTaskLine(line: String): String? = when {
    line.trim().startsWith("- [ ]") -> line.replaceFirst("- [ ]", "- [x]")
    line.trim().startsWith("- [x]") || line.trim().startsWith("- [X]") ->
        line.replaceFirst(TOGGLE_CHECK_RE, "- [ ]")
    else -> null
}
