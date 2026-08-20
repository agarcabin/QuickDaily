package com.quickdaily

import java.time.LocalDate

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
    val isDateHeader: Boolean = false,
    val isFirstDateHeader: Boolean = false,
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

/** Adds date headers without changing the order of tasks already loaded by the widget. */
internal object TaskWidgetDateGrouping {
    private val dateParts = Regex("(\\d{1,4})\\D+(\\d{1,2})\\D+(\\d{1,2})")

    fun withHeaders(items: List<TaskWidgetItem>, todayDate: String?): List<TaskWidgetItem> {
        if (items.isEmpty() || todayDate.isNullOrBlank()) return items
        val datedItems = items.filter { !it.isDateHeader && !it.date.isNullOrBlank() }
        if (datedItems.isEmpty() || datedItems.none { it.date != todayDate }) return items

        val groups = linkedMapOf<String, MutableList<TaskWidgetItem>>()
        items.forEach { item ->
            val key = item.date.orEmpty()
            groups.getOrPut(key) { mutableListOf() }.add(item)
        }
        var firstDateHeader = true
        return buildList {
            groups.forEach { (date, group) ->
                if (date.isNotBlank()) {
                    add(dateHeader(date, isFirstDateHeader = firstDateHeader))
                    firstDateHeader = false
                }
                addAll(group)
            }
        }
    }

    fun labelFor(date: String): String {
        val match = dateParts.find(date) ?: return date
        val year = match.groupValues[1].toIntOrNull()
        val month = match.groupValues[2].toIntOrNull()
        val day = match.groupValues[3].toIntOrNull()
        if (month == null || day == null) return date

        val weekday = year?.let { parsedYear ->
            runCatching {
                val normalizedYear = if (parsedYear < 100) parsedYear + 2000 else parsedYear
                val dayOfWeek = LocalDate.of(normalizedYear, month, day).dayOfWeek.value
                listOf("一", "二", "三", "四", "五", "六", "日")[dayOfWeek - 1]
            }.getOrNull()
        }
        return if (weekday == null) {
            "${month}月${day}日"
        } else {
            "${month}月${day}日，周$weekday"
        }
    }

    private fun dateHeader(
        date: String,
        isFirstDateHeader: Boolean,
    ): TaskWidgetItem = TaskWidgetItem(
        text = labelFor(date),
        sourcePath = "",
        date = date,
        lineIndex = -1,
        rawLine = "",
        checked = false,
        indentLevel = 0,
        rootLineIndex = -1,
        isDateHeader = true,
        isFirstDateHeader = isFirstDateHeader,
    )
}
