package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikilinkPolicyTest {
    @Test
    fun triggerUsesNearestUnclosedLinkOnCurrentLine() {
        val trigger = WikilinkPolicy.trigger("text [[Folder/Pa", 16)
        assertEquals(5, trigger?.start)
        assertEquals("Folder/Pa", trigger?.query)
        assertEquals(16, trigger?.replaceEnd)
    }

    @Test
    fun triggerDoesNotCrossLinesOrClosedLinks() {
        assertNull(WikilinkPolicy.trigger("[[Page]]", 8))
        assertNull(WikilinkPolicy.trigger("[[Page\nmore", 11))
        assertNull(WikilinkPolicy.trigger("\ntext", 0))
        assertNull(WikilinkPolicy.trigger("", 0))
    }

    @Test
    fun existingClosingBracketsAreReplacedWithoutDuplication() {
        val trigger = WikilinkPolicy.trigger("[[Pag]]", 5)
        assertEquals(7, trigger?.replaceEnd)
    }

    @Test
    fun candidatesUseStablePrefixLengthAndAlphabeticalOrder() {
        val result = WikilinkPolicy.filterCandidates(
            listOf("Project/Plan", "Page Long", "Page", "page-two", "Other"),
            "pa",
            limit = 3,
        )
        assertEquals(listOf("Page", "page-two", "Page Long"), result)
    }

    @Test
    fun wikilinkCandidatesAreLimitedAndKeepAliasInsertionSyntax() {
        val result = WikilinkPolicy.filterWikilinkCandidates(
            listOf(
                WikilinkCandidate("Page/Long"),
                WikilinkCandidate("Page", "Alias"),
                WikilinkCandidate("Other", "Alias"),
                WikilinkCandidate("Page/Three"),
            ),
            "a",
        )
        assertEquals(2, result.size)
        assertTrue(result.any { it.insertionText == "[[Page|Alias]]" })
    }
}
