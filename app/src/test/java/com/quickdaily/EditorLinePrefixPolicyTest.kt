package com.quickdaily

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorLinePrefixPolicyTest {
    @Test
    fun clickingAnotherBlockPrefixClearsTheExistingPrefixFirst() {
        val fence = Char(96).toString().repeat(3)
        assertEquals(
            "- task",
            EditorLinePrefixPolicy.apply("- [ ] task", TextRange(7), EditorToolbarAction.LIST).text,
        )
        assertEquals(
            "# heading",
            EditorLinePrefixPolicy.apply("> heading", TextRange(8), EditorToolbarAction.HEADING).text,
        )
        assertEquals(
            "> quote",
            EditorLinePrefixPolicy.apply("1. quote", TextRange(7), EditorToolbarAction.QUOTE).text,
        )
        assertEquals(
            "1. code",
            EditorLinePrefixPolicy.apply(fence + "code", TextRange(7), EditorToolbarAction.ORDERED_LIST).text,
        )
    }

    @Test
    fun taskHeadingAndSamePrefixActionsKeepTheirExistingToggleSemantics() {
        assertEquals(
            "- [ ] task",
            EditorLinePrefixPolicy.apply("task", TextRange(4), EditorToolbarAction.TASK).text,
        )
        assertEquals(
            "- [x] task",
            EditorLinePrefixPolicy.apply("- [ ] task", TextRange(10), EditorToolbarAction.TASK).text,
        )
        assertEquals(
            "task",
            EditorLinePrefixPolicy.apply("- [x] task", TextRange(10), EditorToolbarAction.TASK).text,
        )
        assertEquals(
            "### heading",
            EditorLinePrefixPolicy.apply("## heading", TextRange(10), EditorToolbarAction.HEADING).text,
        )
        assertEquals(
            "heading",
            EditorLinePrefixPolicy.apply("### heading", TextRange(11), EditorToolbarAction.HEADING).text,
        )
    }

    @Test
    fun orderedListStartsAtOneAndIncrementsFromThePreviousOrderedLine() {
        assertEquals(
            "1. first",
            EditorLinePrefixPolicy.apply("first", TextRange(5), EditorToolbarAction.ORDERED_LIST).text,
        )
        assertEquals(
            "4. first\n5. second",
            EditorLinePrefixPolicy.apply(
                "4. first\nsecond",
                TextRange("4. first\nsecond".length),
                EditorToolbarAction.ORDERED_LIST,
            ).text,
        )
        assertEquals(
            "4. first\nsecond",
            EditorLinePrefixPolicy.apply(
                "4. first\n5. second",
                TextRange("4. first\n5. second".length),
                EditorToolbarAction.ORDERED_LIST,
            ).text,
        )
    }

    @Test
    fun codeBlockIsAlsoMutuallyExclusiveButInlineActionsAreNot() {
        val fence = Char(96).toString().repeat(3)
        assertEquals(
            "# body",
            EditorLinePrefixPolicy.apply(fence + "body", TextRange(7), EditorToolbarAction.HEADING).text,
        )
        assertEquals(
            "body",
            EditorLinePrefixPolicy.apply(fence + "body", TextRange(7), EditorToolbarAction.CODE_BLOCK).text,
        )
        val inline = EditorLinePrefixPolicy.apply("- [ ] body", TextRange(2), EditorToolbarAction.BOLD)
        assertEquals("- [ ] body", inline.text)
        assertEquals(TextRange(2), inline.selection)
    }

    @Test
    fun collapsedCursorMapsAfterPrefixForEmptyAndMiddlePositions() {
        val empty = EditorLinePrefixPolicy.apply("", TextRange(0), EditorToolbarAction.LIST)
        assertEquals("- ", empty.text)
        assertEquals(TextRange(2), empty.selection)

        val middle = EditorLinePrefixPolicy.apply("body", TextRange(2), EditorToolbarAction.QUOTE)
        assertEquals("> body", middle.text)
        assertEquals(TextRange(4), middle.selection)

        val end = EditorLinePrefixPolicy.apply("body", TextRange(4), EditorToolbarAction.TASK)
        assertEquals("- [ ] body", end.text)
        assertEquals(TextRange(10), end.selection)
    }

    @Test
    fun multilineSelectionPreservesLogicalRangeAndRemovesExistingPrefixesWhenSwitching() {
        val text = "- [ ] one\n> two"
        val result = EditorLinePrefixPolicy.apply(text, TextRange(text.length, 0), EditorToolbarAction.LIST)
        assertEquals("- one\n- two", result.text)
        assertEquals(TextRange(result.text.length, 2), result.selection)

        val removed = EditorLinePrefixPolicy.apply("- one\n- two", TextRange(0, 11), EditorToolbarAction.LIST)
        assertEquals("one\ntwo", removed.text)
        assertEquals(TextRange(0, 7), removed.selection)
    }

    @Test
    fun reversedSelectionKeepsDirectionAfterAddingPrefixes() {
        val result = EditorLinePrefixPolicy.apply("one\ntwo", TextRange(7, 0), EditorToolbarAction.QUOTE)
        assertEquals("> one\n> two", result.text)
        assertEquals(TextRange(11, 2), result.selection)
    }
}
