package com.quickdaily

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTextActionPolicyTest {
    @Test
    fun recommendedMarkdownActionsRemainHiddenByDefault() {
        val recommended = setOf(
            EditorToolbarAction.STRIKETHROUGH.id,
            EditorToolbarAction.INLINE_CODE.id,
            EditorToolbarAction.QUOTE.id,
            EditorToolbarAction.CODE_BLOCK.id,
            EditorToolbarAction.ORDERED_LIST.id,
            EditorToolbarAction.HORIZONTAL_RULE.id,
            EditorToolbarAction.MARKDOWN_LINK.id,
        )
        assertTrue(recommended.none { it in EditorToolbarPolicy.defaultVisible })
        assertTrue(recommended.all { it in EditorToolbarPolicy.defaultOrder.map(EditorToolbarAction::id) })
    }

    @Test
    fun delimiterWrapsSelectedTextAndTogglesAtCursor() {
        val wrapped = EditorTextActionPolicy.toggleDelimiter("hello", TextRange(0, 5), "~~")
        assertEquals("~~hello~~", wrapped.text)

        val removed = EditorTextActionPolicy.toggleDelimiter(wrapped.text, TextRange(2), "~~")
        assertEquals("hello", removed.text)
    }

    @Test
    fun quoteAndCodeBlockPreserveMarkdownShape() {
        val quoted = EditorTextActionPolicy.prefixLines("one\ntwo", TextRange(0, 7), "> ")
        assertEquals("> one\n> two", quoted.text)

        val code = EditorTextActionPolicy.codeBlock("body", TextRange(0, 4))
        assertEquals("```\nbody\n```", code.text)
    }

    @Test
    fun linkAndHorizontalRuleUseEditableInsertionPoints() {
        val link = EditorTextActionPolicy.markdownLink("title", TextRange(0, 5))
        assertEquals("[title](https://)", link.text)
        assertEquals(8, link.selection.start)

        val divider = EditorTextActionPolicy.horizontalRule("a", TextRange(1))
        assertEquals("a\n---\n", divider.text)
    }

    @Test
    fun movingTwoLinesPreservesTheirSeparator() {
        assertEquals("B\nA", EditorTextActionPolicy.moveLineUp("A\nB", TextRange(3)).text)
        assertEquals("B\nA", EditorTextActionPolicy.moveLineDown("A\nB", TextRange(0)).text)
    }

    @Test
    fun movingAtTheEdgesIsAStableNoOpAndMiddleLinesSwap() {
        assertEquals("A\nB\nC", EditorTextActionPolicy.moveLineUp("A\nB\nC", TextRange(0)).text)
        assertEquals("A\nB\nC", EditorTextActionPolicy.moveLineDown("A\nB\nC", TextRange(5)).text)
        assertEquals("B\nA\nC", EditorTextActionPolicy.moveLineUp("A\nB\nC", TextRange(3)).text)
        assertEquals("A\nC\nB", EditorTextActionPolicy.moveLineDown("A\nB\nC", TextRange(3)).text)
        assertEquals("B\nA\n", EditorTextActionPolicy.moveLineUp("A\nB\n", TextRange(3)).text)
    }

}
