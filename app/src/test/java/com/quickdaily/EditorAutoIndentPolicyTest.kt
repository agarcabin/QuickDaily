package com.quickdaily

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorAutoIndentPolicyTest {
    @Test
    fun enterNormalizesIndentToFourSpaceUnits() {
        listOf(
            Triple(" ", "    ", 4),
            Triple("    ", "    ", 4),
            Triple("        ", "        ", 8),
            Triple("\t", "    ", 4),
            Triple("\t  ", "        ", 8),
        ).forEach { (sourceIndent, continuationIndent, continuationLength) ->
            val previous = value("${sourceIndent}item", sourceIndent.length + 4)
            val proposed = value("${sourceIndent}item\n", sourceIndent.length + 5)

            val result = EditorAutoIndentPolicy.apply(previous, proposed, null)

            assertEquals("${sourceIndent}item\n$continuationIndent", result.value.text)
            assertEquals(TextRange(proposed.selection.start + continuationLength), result.value.selection)
            assertEquals(
                EditorAutoIndentState(
                    proposed.selection.start,
                    proposed.selection.start + continuationLength,
                ),
                result.state,
            )
        }
    }

    @Test
    fun enterInLineCopiesLeadingIndentBeforeTrailingText() {
        val previous = value("  abcdef", 5)
        val proposed = value("  abc\ndef", 6)

        val result = EditorAutoIndentPolicy.apply(previous, proposed, null)

        assertEquals("  abc\n    def", result.value.text)
        assertEquals(TextRange(10), result.value.selection)
    }

    @Test
    fun enterContinuesUnorderedOrderedAndTaskPrefixes() {
        listOf(
            Triple("- item", "- item\n- ", 9),
            Triple("* item", "* item\n* ", 9),
            Triple("3. item", "3. item\n4. ", 11),
            Triple("- [x] done", "- [x] done\n- [ ] ", 17),
            Triple("- [ ] todo", "- [ ] todo\n- [ ] ", 17),
        ).forEach { (source, expected, cursor) ->
            val proposed = value("$source\n", source.length + 1)

            val result = EditorAutoIndentPolicy.apply(value(source, source.length), proposed, null)

            assertEquals(expected, result.value.text)
            assertEquals(TextRange(cursor), result.value.selection)
            assertEquals(EditorAutoIndentState(source.length + 1, cursor), result.state)
        }
    }

    @Test
    fun nestedListContinuationUsesFourSpacesBeforeRepeatedPrefix() {
        val previous = value("  - item", 8)
        val proposed = value("  - item\n", 9)

        val result = EditorAutoIndentPolicy.apply(previous, proposed, null)

        assertEquals("  - item\n    - ", result.value.text)
        assertEquals(TextRange(15), result.value.selection)
    }

    @Test
    fun immediateBackspaceRemovesTheWholeGeneratedIndentAndListPrefix() {
        val inserted = EditorAutoIndentPolicy.apply(
            previous = value("    - item", 10),
            proposed = value("    - item\n", 11),
            state = null,
        )

        val result = EditorAutoIndentPolicy.apply(
            previous = inserted.value,
            proposed = value("    - item\n    -", 16),
            state = inserted.state,
        )

        assertEquals("    - item\n", result.value.text)
        assertEquals(TextRange(11), result.value.selection)
        assertNull(result.state)
    }

    @Test
    fun typingOrMovingSelectionClearsAtomicBackspaceState() {
        val inserted = EditorAutoIndentPolicy.apply(
            previous = value("\titem", 5),
            proposed = value("\titem\n", 6),
            state = null,
        )

        val typed = EditorAutoIndentPolicy.apply(
            previous = inserted.value,
            proposed = value("\titem\n    x", 11),
            state = inserted.state,
        )
        val moved = EditorAutoIndentPolicy.apply(
            previous = inserted.value,
            proposed = inserted.value.copy(selection = TextRange(9)),
            state = inserted.state,
        )

        assertEquals("\titem\n    x", typed.value.text)
        assertNull(typed.state)
        assertNull(moved.state)
    }

    @Test
    fun unindentedEnterAndMultilinePasteRemainUnchanged() {
        val enter = value("item\n", 5)
        val paste = value("\titem\nfirst\nsecond", 19)

        val enterResult = EditorAutoIndentPolicy.apply(value("item", 4), enter, null)
        val pasteResult = EditorAutoIndentPolicy.apply(value("\titem", 5), paste, null)

        assertEquals(enter, enterResult.value)
        assertNull(enterResult.state)
        assertEquals(paste, pasteResult.value)
        assertNull(pasteResult.state)
    }

    @Test
    fun activeImeCompositionIsNeverTransformed() {
        val previous = value("\titem", 5)
        val proposed = TextFieldValue(
            text = "\titem\n",
            selection = TextRange(6),
            composition = TextRange(5, 6),
        )

        val result = EditorAutoIndentPolicy.apply(previous, proposed, null)

        assertEquals(proposed, result.value)
        assertNull(result.state)
        assertTrue(result.value.composition != null)
    }

    private fun value(text: String, cursor: Int): TextFieldValue =
        TextFieldValue(text = text, selection = TextRange(cursor))
}
