package com.quickdaily.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UriUtilTest {
    @Test
    fun primaryDocumentIdWithRelativePathResolvesToPrimaryStorage() {
        assertEquals(
            "/storage/emulated/0/1111/Task.md",
            UriUtil.docIdToPath("primary:1111/Task.md"),
        )
    }

    @Test
    fun primaryDocumentIdWithAbsolutePathDoesNotDuplicatePrimaryStorage() {
        assertEquals(
            "/storage/emulated/0/1111/Task.md",
            UriUtil.docIdToPath("primary:/storage/emulated/0/1111/Task.md"),
        )
    }

    @Test
    fun rawDocumentIdKeepsItsAbsolutePath() {
        assertEquals(
            "/storage/emulated/0/1111/Task.md",
            UriUtil.docIdToPath("raw:/storage/emulated/0/1111/Task.md"),
        )
    }
}
