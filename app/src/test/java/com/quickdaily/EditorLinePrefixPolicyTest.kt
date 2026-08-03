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
}
