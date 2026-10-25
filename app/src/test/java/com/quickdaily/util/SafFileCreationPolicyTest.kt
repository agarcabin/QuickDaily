package com.quickdaily.util

import org.junit.Assert.*
import org.junit.Test

class SafFileCreationPolicyTest {
    @Test fun markdownAndUnknownExtensionsNeverFallBackToPlainText() {
        listOf("today.md", "note.unknown", "README").forEach { name ->
            assertEquals("application/octet-stream", SafFileCreationPolicy.mimeType(name) { null })
        }
        assertEquals("application/octet-stream", SafFileCreationPolicy.mimeType("today.MD") { "text/plain" })
    }

    @Test fun mediaMimeUsesNormalisedExtensionWithoutChangingFileName() {
        val types = mapOf("jpg" to "image/jpeg", "png" to "image/png", "m4a" to "audio/mp4", "json" to "application/json")
        types.forEach { (extension, mime) ->
            assertEquals(mime, SafFileCreationPolicy.mimeType("中文.${extension.uppercase(java.util.Locale.ROOT)}") { types[it] })
        }
    }

    @Test fun cleanupRequiresKnownIdentityAndKnownContent() {
        val empty = FileFingerprint.fromBytes(byteArrayOf(), identity = "created-id")
        val expected = FileFingerprint.fromBytes("note".toByteArray())
        assertTrue(SafFileCreationPolicy.canRemoveCreated(empty, empty, expected))
        assertTrue(SafFileCreationPolicy.canRemoveCreated(empty, expected.copy(identity = "created-id"), expected))
        assertFalse(SafFileCreationPolicy.canRemoveCreated(empty, empty.copy(identity = "other-id"), expected))
        assertFalse(SafFileCreationPolicy.canRemoveCreated(empty, FileFingerprint.fromBytes("external".toByteArray(), identity = "created-id"), expected))
        assertFalse(SafFileCreationPolicy.canRemoveCreated(null, empty, expected))
    }
}
