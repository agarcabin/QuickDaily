package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedDocumentClassifierTest {
    @Test
    fun lockedOfficeExtensionsAreSupported() {
        val expected = setOf(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
            "txt", "md", "rtf", "csv", "odt", "ods", "odp",
            "wps", "wpt", "et", "dps",
        )
        assertEquals(expected, SharedDocumentClassifier.supportedExtensions)
    }

    @Test
    fun unsafeGenericAndMediaTypesAreRejected() {
        assertFalse(SharedDocumentClassifier.isSupported("application/octet-stream", "archive.zip"))
        assertFalse(SharedDocumentClassifier.isSupported("audio/mpeg", "voice.mp3"))
        assertFalse(SharedDocumentClassifier.isSupported("video/mp4", "clip.mp4"))
        assertTrue(SharedDocumentClassifier.isSupported("application/pdf", "shared"))
    }

    @Test
    fun namesAreSanitizedAndCollisionsNeverOverwrite() {
        assertEquals("report_.pdf", SharedDocumentClassifier.sanitizeFileName("../report?.pdf", "application/pdf"))
        val directory = createTempDir(prefix = "quickdaily-share-")
        try {
            File(directory, "report.pdf").writeText("existing")
            assertEquals("report (1).pdf", SharedDocumentClassifier.availableFile(directory, "report.pdf").name)
        } finally {
            directory.deleteRecursively()
        }
    }
}
