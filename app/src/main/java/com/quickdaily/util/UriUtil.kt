package com.quickdaily.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * 从 SAF (Storage Access Framework) 的 content URI 提取实际文件路径。
 * 仅支持主存储 (primary)，用于 Obsidian vault 这种典型场景。
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

    private fun docIdToPath(docId: String): String? {
        if (docId.startsWith("raw:", ignoreCase = true)) {
            return docId.substringAfter(':').takeIf { it.isNotBlank() }
        }
        val split = docId.split(":", limit = 2)
        if (split.size != 2) return null

        val (storage, subPath) = split
        return when (storage.lowercase()) {
            "primary" -> "/storage/emulated/0/$subPath"
            else -> "/storage/$storage/$subPath"  // SD 卡等外部存储
        }
    }
}
