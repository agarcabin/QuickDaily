package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenTextPolicyTest {
    @Test
    fun matchingIsLiteralCaseSensitiveAndMergesOverlaps() {
        assertEquals(
            listOf(DisplayRange(0, 5)),
            HiddenTextPolicy.ranges("ababa", listOf("aba", "bab")),
        )
        assertTrue(HiddenTextPolicy.ranges("ABC", listOf("abc")).isEmpty())
        assertEquals(
            listOf(DisplayRange(1, 4)),
            HiddenTextPolicy.ranges("a.*[", listOf(".*[")),
        )
    }

    @Test
    fun rulesNormalizeLineEndingsAndDropOnlyBlankRules() {
        assertEquals(
            listOf("a\nb", "a\nb"),
            HiddenTextPolicy.normalizeRules(listOf("a\r\nb", "a\rb", " \t")),
        )
    }

    @Test
    fun multilineMatchMasksOwnedCharactersButNeverSyntheticNewline() {
        val hidden = HiddenTextPolicy.apply(
            listOf(
                HiddenFlowItem(DisplayText("alpha")),
                HiddenFlowItem(DisplayText("beta")),
            ),
            listOf("alpha\nbeta"),
        )

        assertEquals(listOf(DisplayRange(0, 5)), hidden[0])
        assertEquals(listOf(DisplayRange(0, 4)), hidden[1])
    }

    @Test
    fun rawSourceMatchCanHideMarkdownSyntaxThatWasRemovedFromDisplay() {
        val flow = listOf(
                HiddenFlowItem(
                    display = DisplayText("最近修改"),
                    sourceText = "# 最近修改",
                ),
                HiddenFlowItem(
                    display = DisplayText("key: value"),
                    sourceText = "  key: value",
                ),
        )
        val hidden = HiddenTextPolicy.apply(
            flow,
            listOf("# 最近修改\n  key: value"),
        )

        assertEquals(listOf(DisplayRange(0, 4)), hidden[0])
        assertEquals(listOf(DisplayRange(0, 10)), hidden[1])
        assertEquals(
            listOf(false, true),
            HiddenTextPolicy.collapseExtraRows(flow, hidden),
        )
    }

    @Test
    fun barriersPreventCrossItemMatches() {
        val hidden = HiddenTextPolicy.apply(
            listOf(
                HiddenFlowItem(DisplayText("alpha")),
                HiddenFlowItem(
                    DisplayText("[图片]"),
                    barrierBefore = true,
                    barrierAfter = true,
                ),
                HiddenFlowItem(DisplayText("beta")),
            ),
            listOf("alpha\nbeta"),
        )

        assertEquals(listOf(emptyList<DisplayRange>(), emptyList(), emptyList()), hidden)
    }

    @Test
    fun emojiRangesUseUtf16Offsets() {
        assertEquals(
            listOf(DisplayRange(0, 2)),
            HiddenTextPolicy.ranges("🙂abc", listOf("🙂")),
        )
    }
}
