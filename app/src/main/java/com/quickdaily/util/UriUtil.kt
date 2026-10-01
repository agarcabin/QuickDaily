package com.quickdaily.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Legacy URI helpers retained for non-vault import compatibility.
 *
 * SAF tree/document URIs are storage identities, not filesystem paths. New
 * vault code keeps the original content URI and uses [VaultStorage]. These
 * helpers intentionally return null for provider IDs such as `home:` instead
 * of fabricating `/storage/home/...` paths.
 */
object UriUtil {

    /**
     * 从 tree URI（文件夹选择器返回）提取路径
     * 例: content://...tree/primary%3ADocuments%2FVault → /storage/emulated/0/Documents/Vault
     */
    fun treeUriToPath(uri: Uri): String? {
        val docId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (_: Exception) {
            return null
        }
        return docIdToPath(docId)
    }

    /**
     * 从 document URI（文件选择器返回）提取路径
     * 例: content://...document/primary%3ADocuments%2Ffile.md → /storage/emulated/0/Documents/file.md
     */
    fun documentUriToPath(uri: Uri): String? {
        val docId = try {
            DocumentsContract.getDocumentId(uri)
        } catch (_: Exception) {
            return null
        }
        return docIdToPath(docId)
    }

    /**
     * Resolves a document URI to a filesystem path, including vendor file pickers
     * that do not expose a DocumentsContract document ID (for example HyperOS).
     */
    fun documentUriToPath(context: Context, uri: Uri): String? {
        documentUriToPath(uri)?.let { return it }
        if (uri.scheme.equals("file", ignoreCase = true)) return uri.path

        return try {
            context.contentResolver.query(
                uri,
                arrayOf("_data"),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf {
                        it.isNotBlank() && !VaultStoragePrefs.isLegacyHomePath(it)
                    }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun docIdToPath(docId: String): String? {
        if (docId.startsWith("raw:", ignoreCase = true)) {
            return docId.substringAfter(':')
                .takeIf { it.isNotBlank() && !VaultStoragePrefs.isLegacyHomePath(it) }
        }
        val split = docId.split(":", limit = 2)
        if (split.size != 2) return null

        val (storage, subPath) = split
        val normalizedSubPath = subPath.trim()
        return when (storage.lowercase()) {
            "primary" -> {
                // Some vendor document providers return an already absolute path
                // as the primary document ID, e.g. primary:/storage/emulated/0/foo.md.
                // Do not prepend the primary root a second time in that case.
                val primaryRoot = "/storage/emulated/0"
                if (
                    normalizedSubPath.equals(primaryRoot, ignoreCase = true) ||
                    normalizedSubPath.startsWith("$primaryRoot/", ignoreCase = true)
                ) {
                    normalizedSubPath
                } else {
                    "$primaryRoot/${normalizedSubPath.trimStart('/')}"
                }.takeUnless(VaultStoragePrefs::isLegacyHomePath)
            }
            // A provider-specific ID cannot be safely mapped to a physical
            // path. In particular, mapping `home:` to /storage/home is wrong.
            else -> null
        }
    }
}
