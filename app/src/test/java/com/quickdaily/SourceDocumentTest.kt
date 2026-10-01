package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceDocumentTest {
    @Test
    fun splitLinesPreservesCrLfCrLfAndFinalEmptyLine() {
        val records = SourceDocument.splitLines("a\r\nb\rc\n")

        assertEquals(
            listOf(
                SourceLineRecord(0, "a", "\r\n"),
                SourceLineRecord(1, "b", "\r"),
                SourceLineRecord(2, "c", "\n"),
                SourceLineRecord(3, "", ""),
            ),
            records,
        )
    }

    @Test
    fun frontmatterOffsetsAndReplacementKeepOriginalSeparators() {
        val source = "---\r\ntitle: Today\r\n---\r\n- [ ] task\r\nnext"
        val document = SourceDocument.from(source)

        assertEquals(3, document.bodyStartLine)
        assertEquals("- [ ] task\r\nnext", document.bodyTextPreservingSeparators())
        assertEquals(
            "---\r\ntitle: Today\r\n---\r\n- [x] task\r\nnext",
            document.replaceLine(3, "- [x] task"),
        )
    }

    @Test
    fun frontmatterAtEndDoesNotBecomeBodyWhenThereIsNoFinalNewline() {
        val document = SourceDocument.from("---\nempty: true\n---")

        assertEquals(3, document.bodyStartLine)
        assertEquals("", document.bodyTextPreservingSeparators())
    }

    @Test
    fun taskParserCarriesAbsoluteLineAndSeparatorFromBodyOffset() {
        val items = TaskWidgetTaskParser.parse(
            body = "- [ ] one\r\n- [ ] two",
            sourcePath = "/vault/page.md",
            lineIndexOffset = 3,
        )

        assertEquals(listOf(3, 4), items.map { it.lineIndex })
        assertEquals(listOf("\r\n", ""), items.map { it.lineSeparator })
    }
}
