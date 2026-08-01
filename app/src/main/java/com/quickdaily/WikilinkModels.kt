package com.quickdaily

/** A page or frontmatter alias that can be inserted as an Obsidian wikilink. */
data class WikilinkCandidate(
    val targetPath: String,
    val alias: String? = null,
) {
    val queryText: String
        get() = alias ?: targetPath

    val displayText: String
        get() = alias ?: targetPath

    val insertionText: String
        get() = alias?.let { "[[$targetPath|$it]]" } ?: "[[$targetPath]]"

    val stableKey: String
        get() = if (alias == null) "page:$targetPath" else "alias:$targetPath\u001f$alias"
}

object WikilinkCandidatePolicy {
    const val DEFAULT_LIMIT = 3

    fun filter(
        candidates: List<WikilinkCandidate>,
        query: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<WikilinkCandidate> {
        val prefix = query.trim()
        return candidates
            .filter { it.queryText.startsWith(prefix, ignoreCase = true) }
            .sortedWith(
                compareBy<WikilinkCandidate> { it.queryText.length }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.queryText }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.targetPath }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.alias.orEmpty() },
            )
            .take(limit)
    }
}
