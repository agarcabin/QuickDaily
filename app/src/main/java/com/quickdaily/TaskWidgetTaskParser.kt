package com.quickdaily

private data class TaskStackEntry(
    val indentColumns: Int,
    val level: Int,
    val rootLineIndex: Int,
)

data class TaskWidgetItem(
    val text: String,
    val sourcePath: String,
    val date: String? = null,
    val lineIndex: Int,
    val rawLine: String,
    val checked: Boolean,
    val indentLevel: Int,
    val rootLineIndex: Int,
)

/** Markdown task parser shared by direct RemoteViews and the legacy service. */
object TaskWidgetTaskParser {
    private val taskRegex = Regex("^([ \\t]*)([-+*])\\s*\\[\\s*([xX ])\\s*]\\s*(.*)$")

    fun parseVisible(
        body: String,
        sourcePath: String,
        date: String? = null,
        showCompleted: Boolean = false,
    ): List<TaskWidgetItem> {
        val allTasks = parse(body, sourcePath, date)
        if (showCompleted) return allTasks

        var hiddenAtLevel: Int? = null
        return buildList {
            allTasks.forEach { item ->
                if (hiddenAtLevel != null && item.indentLevel <= hiddenAtLevel!!) {
                    hiddenAtLevel = null
                }
                if (hiddenAtLevel != null) return@forEach
                if (item.checked) {
                    hiddenAtLevel = item.indentLevel
                    return@forEach
                }
                add(item)
            }
        }
    }

    fun parse(
        body: String,
        sourcePath: String,
        date: String? = null,
    ): List<TaskWidgetItem> {
        val stack = ArrayDeque<TaskStackEntry>()
        val result = mutableListOf<TaskWidgetItem>()

        body.lines().forEachIndexed { lineIndex, line ->
            val match = taskRegex.matchEntire(line) ?: return@forEachIndexed
            val leadingWhitespace = match.groupValues[1]
            val indentColumns = leadingWhitespace.fold(0) { total: Int, char: Char ->
                total + if (char == '\t') 4 else 1
            }
            while (stack.isNotEmpty() && stack.last().indentColumns >= indentColumns) {
                stack.removeLast()
            }

            val parent = stack.lastOrNull()
            val level = parent?.let { it.level + 1 } ?: 0
            val rootLineIndex = parent?.rootLineIndex ?: lineIndex
            val checked = match.groupValues[3].trim().equals("x", ignoreCase = true)
            result += TaskWidgetItem(
                text = match.groupValues[4],
                sourcePath = sourcePath,
                date = date,
                lineIndex = lineIndex,
                rawLine = line,
                checked = checked,
                indentLevel = level,
                rootLineIndex = rootLineIndex,
            )
            stack.addLast(TaskStackEntry(indentColumns, level, rootLineIndex))
        }
        return result
    }

    /** Toggle only the checkbox marker, preserving indentation and task text. */
    fun toggleLine(line: String): String? {
        val match = taskRegex.matchEntire(line) ?: return null
        val openBracket = line.indexOf('[', match.range.first)
        val closeBracket = line.indexOf(']', openBracket + 1)
        if (openBracket < 0 || closeBracket < 0) return null
        val checked = match.groupValues[3].trim().equals("x", ignoreCase = true)
        val marker = if (checked) " " else "x"
        return line.substring(0, openBracket + 1) + marker + "]" + line.substring(closeBracket + 1)
    }
}
