package com.quickdaily

data class WikilinkTrigger(
    val start: Int,
    val replaceEnd: Int,
    val query: String,
)

object WikilinkPolicy {
    fun trigger(text: String, cursor: Int): WikilinkTrigger? {
        val caret = cursor.coerceIn(0, text.length)
        val previousLineBreak = if (caret > 0) text.lastIndexOf('\n', caret - 1) else -1
        val lineStart = if (previousLineBreak < 0) 0 else previousLineBreak + 1
        if (lineStart > caret) return null
        val beforeCaret = text.substring(lineStart, caret)
        val openOffset = beforeCaret.lastIndexOf("[[")
        if (openOffset < 0) return null

        val open = lineStart + openOffset
        val closeBeforeOpen = text.lastIndexOf("]]", caret - 1)
        if (closeBeforeOpen >= open) return null

        val query = text.substring(open + 2, caret)
        if (query.any { it == '\n' || it == ']' }) return null

        val closing = text.indexOf("]]", caret)
        val replaceEnd = if (closing >= 0 && text.substring(caret, closing).isBlank()) {
            closing + 2
        } else {
            caret
        }
        return WikilinkTrigger(open, replaceEnd, query)
    }

    fun filterCandidates(entries: List<String>, query: String, limit: Int = 20): List<String> {
        val prefix = query.trim()
        return entries
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .sortedWith(compareBy<String> { it.length }.thenBy(String.CASE_INSENSITIVE_ORDER) { it })
            .take(limit)
    }

    fun filterWikilinkCandidates(
        candidates: List<WikilinkCandidate>,
        query: String,
        limit: Int = WikilinkCandidatePolicy.DEFAULT_LIMIT,
    ): List<WikilinkCandidate> = WikilinkCandidatePolicy.filter(candidates, query, limit)
}
