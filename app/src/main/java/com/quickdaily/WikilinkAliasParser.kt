package com.quickdaily

/** Small dependency-free parser for the common Obsidian alias frontmatter forms. */
object WikilinkAliasParser {
    private val keyPattern = Regex("^([A-Za-z][A-Za-z0-9_-]*)\\s*:\\s*(.*)$")

    fun parse(frontmatter: String): List<String> {
        val result = linkedSetOf<String>()
        var readingList = false

        frontmatter.replace("\r\n", "\n").lines().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach

            if (readingList && trimmed.startsWith("-")) {
                addValue(result, trimmed.removePrefix("-").trim())
                return@forEach
            }

            val match = keyPattern.matchEntire(trimmed)
            readingList = false
            if (match == null) return@forEach

            val key = match.groupValues[1].lowercase()
            if (key != "alias" && key != "aliases") return@forEach

            val rawValue = match.groupValues[2].trim()
            if (rawValue.isEmpty()) {
                readingList = true
            } else if (rawValue.startsWith("[") && rawValue.endsWith("]")) {
                splitInlineList(rawValue.substring(1, rawValue.length - 1)).forEach {
                    addValue(result, it)
                }
            } else {
                addValue(result, rawValue)
            }
        }

        return result.toList()
    }

    private fun splitInlineList(value: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null

        fun flush() {
            val item = current.toString().trim()
            if (item.isNotEmpty()) result += item
            current.clear()
        }

        value.forEach { char ->
            when {
                quote != null && char == quote -> quote = null
                quote == null && (char == '\'' || char == '"') -> quote = char
                quote == null && char == ',' -> flush()
                else -> current.append(char)
            }
        }
        flush()
        return result
    }

    private fun addValue(result: MutableSet<String>, rawValue: String) {
        var value = rawValue.trim()
        if (value.isEmpty()) return
        if (value.startsWith("#")) return

        value = value.substringBefore(" #").trim()
        if (value.length >= 2) {
            val first = value.first()
            val last = value.last()
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                value = value.substring(1, value.length - 1)
            }
        }
        value = value.replace("\\\\\"", "\"").trim()
        if (value.isNotEmpty()) result += value
    }
}
