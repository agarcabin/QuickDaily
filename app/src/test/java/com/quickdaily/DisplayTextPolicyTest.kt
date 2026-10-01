package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayTextPolicyTest {
    @Test
    fun wikilinksUseOneOpaqueThemeRangeAndAliasesKeepWhitespace() {
        val result = InlineDisplayPolicy.render(
            "[[page]] [[page|  Alias  ]] [[page|**literal**]]",
            InlineSurface.WIDGET,
        )

        assertEquals("[[page]] [[  Alias  ]] [[**literal**]]", result.text)
        assertEquals(
            listOf(
                DisplayStyleRange(0, 8, DisplayStyleKind.WIKILINK),
                DisplayStyleRange(9, 22, DisplayStyleKind.WIKILINK),
                DisplayStyleRange(23, 38, DisplayStyleKind.WIKILINK),
            ),
            result.styles,
        )
    }

    @Test
    fun invalidAndUnclosedWikilinksRemainLiteral() {
        val raw = "[[ ]] [[|alias]] [[page|   ]] [[page"
        val result = InlineDisplayPolicy.render(raw, InlineSurface.EDITOR)

        assertEquals(raw, result.text)
        assertTrue(result.styles.isEmpty())
    }

    @Test
    fun imageWikilinksStayOpaqueAndDoNotBecomeTextWikilinks() {
        val result = InlineDisplayPolicy.render(
            "![[photo.png]] [[Page|Alias]]",
            InlineSurface.WIDGET,
        )

        assertEquals("![[photo.png]] [[Alias]]", result.text)
        assertEquals(
            listOf(DisplayStyleRange(15, 24, DisplayStyleKind.WIKILINK)),
            result.styles,
        )
    }

    @Test
    fun widgetCodeAndOrdinaryLinksKeepTheirExistingVisibleTransforms() {
        val result = InlineDisplayPolicy.render(
            "`code` [label](https://example.test)",
            InlineSurface.WIDGET,
        )

        assertEquals("code label", result.text)
        assertEquals(
            listOf(
                DisplayStyleRange(0, 4, DisplayStyleKind.CODE),
                DisplayStyleRange(5, 10, DisplayStyleKind.LINK),
            ),
            result.styles,
        )
    }
}
