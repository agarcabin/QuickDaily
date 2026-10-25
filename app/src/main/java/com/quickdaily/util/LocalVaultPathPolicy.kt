package com.quickdaily.util

/** Only the system external-storage provider's primary IDs denote known physical paths. */
internal object LocalVaultPathPolicy {
    fun fromTreeId(authority: String?, documentId: String): String? {
        if (authority != "com.android.externalstorage.documents") return null
        return normalize(UriUtil.docIdToPath(documentId).orEmpty())
    }

    fun normalize(path: String): String? {
        val clean = path.trim().replace('\\', '/').trimEnd('/')
        return clean.takeIf {
            it.startsWith('/') && it.isNotEmpty() && !VaultStoragePrefs.isLegacyHomePath(it) &&
                it.split('/').none { segment -> segment == ".." || segment == "." } && '\u0000' !in it
        }
    }
}
