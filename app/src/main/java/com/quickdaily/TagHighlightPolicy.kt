package com.quickdaily

internal data class TagHighlightRange(
    val start: Int,
    val end: Int,
)

internal object TagHighlightPolicy {
    fun ranges(text: String): List<TagHighlightRange> = buildList {
        var index = 0
        while (index < text.length) {
            val isBoundary = index == 0 || text[index - 1].let {
                !it.isLetterOrDigit() && it != '_'
            }
            if (text[index] != '#' || !isBoundary) {
                index++
                continue
            }

            var end = index + 1
            while (end < text.length && text[end].isTagCharacter()) end++
            if (end > index + 1) {
                add(TagHighlightRange(index, end))
                index = end
            } else {
                index++
            }
        }
    }

    private fun Char.isTagCharacter(): Boolean =
        isLetterOrDigit() || this == '_' || this == '-' || this == '/'
}
