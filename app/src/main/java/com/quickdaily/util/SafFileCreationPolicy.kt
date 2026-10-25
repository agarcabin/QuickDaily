package com.quickdaily.util

import java.util.Locale

internal object SafFileCreationPolicy {
    fun mimeType(fileName: String, lookup: (String) -> String?): String {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension == "md") return "application/octet-stream"
        return lookup(extension)?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
    }

    /** Never remove an unrecognised partial write or another app's changed document. */
    fun canRemoveCreated(
        created: FileFingerprint?,
        current: FileFingerprint?,
        expected: FileFingerprint,
    ): Boolean = created?.identity != null && current?.hasSameIdentityAs(created) == true &&
        (current.hasSameContentAs(created) || current.hasSameContentAs(expected))
}
