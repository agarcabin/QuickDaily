package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.text.TextRange

class EditorMediaUtilTest {
    @Test
    fun audioRelativePathUsesObsidianEmbedSyntax() {
        assertEquals(
            "![[attachments/recording.m4a]]",
            EditorMediaUtil.audioLinkForRelativePath("attachments\\recording.m4a"),
        )
    }

    @Test
    fun insertLinkAtEmptyTextDoesNotPrefixANewline() {
        val result = EditorMediaUtil.insertLink("", TextRange(0), "![[photo.png]]")
        assertEquals("![[photo.png]]\n", result.text)
        assertEquals(TextRange(15), result.selection)
    }

    @Test
    fun insertLinkReplacesTheCurrentSelection() {
        val result = EditorMediaUtil.insertLink(
            "before selected after",
            TextRange(7, 15),
            "![[photo.png]]",
        )
        assertEquals("before ![[photo.png]]\n after", result.text)
        assertEquals(TextRange(22), result.selection)
    }
}
