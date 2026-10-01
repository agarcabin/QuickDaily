package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Test

class TagHighlightPolicyTest {
    @Test
    fun findsSupportedTagCharactersAndKeepsSourceOffsets() {
        val text = "前文 #中文Tag_1-二级/子级 后文"

        assertEquals(
            listOf(TagHighlightRange(3, 17)),
            TagHighlightPolicy.ranges(text),
        )
        assertEquals("#中文Tag_1-二级/子级", text.substring(3, 17))
    }

    @Test
    fun findsMultipleTagsAfterWhitespaceOrPunctuation() {
        val text = "#first (#第二) /#third"

        assertEquals(
            listOf(
                TagHighlightRange(0, 6),
                TagHighlightRange(8, 11),
                TagHighlightRange(14, 20),
            ),
            TagHighlightPolicy.ranges(text),
        )
    }

    @Test
    fun ignoresHeadingsPlainHashesAndHashesInsideWords() {
        val text = "# 标题\n## 二级\n普通 #\nword#notTag _#notTag"

        assertEquals(emptyList<TagHighlightRange>(), TagHighlightPolicy.ranges(text))
    }
}
