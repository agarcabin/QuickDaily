package com.quickdaily

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.quickdaily.util.VaultPathUtil
import java.io.File
import java.io.FileOutputStream

internal data class SharedPayload(
    val text: String?,
    val images: List<Uri>,
    val documents: List<Uri>,
)

internal data class DocumentImportResult(
    val total: Int,
    val links: List<String>,
    val failed: Int,
)

internal object SharedDocumentClassifier {
    val supportedExtensions = setOf(
        "pdf",
        "doc", "docx",
        "ppt", "pptx",
        "xls", "xlsx",
        "txt", "md", "rtf", "csv",
        "odt", "ods", "odp",
        "wps", "wpt", "et", "dps",
    )

    val supportedMimeTypes = setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/plain",
        "text/markdown",
        "text/rtf",
        "application/rtf",
        "text/csv",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation",
        "application/vnd.ms-works",
        "application/kswps",
        "application/kwps",
        "application/vnd.kingsoft.wps",
        "application/vnd.kingsoft.writer",
        "application/vnd.kingsoft.spreadsheets",
        "application/vnd.kingsoft.presentation",
    )

    fun extension(fileName: String?): String? = fileName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        ?.takeIf(String::isNotBlank)

    fun isSupported(mimeType: String?, fileName: String?): Boolean =
        mimeType?.lowercase() in supportedMimeTypes || extension(fileName) in supportedExtensions

    fun defaultExtension(mimeType: String?): String = when (mimeType?.lowercase()) {
        "application/pdf" -> "pdf"
        "application/msword" -> "doc"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/vnd.ms-powerpoint" -> "ppt"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
        "application/vnd.ms-excel" -> "xls"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
        "text/markdown" -> "md"
        "text/rtf", "application/rtf" -> "rtf"
        "text/csv" -> "csv"
        "application/vnd.oasis.opendocument.text" -> "odt"
        "application/vnd.oasis.opendocument.spreadsheet" -> "ods"
        "application/vnd.oasis.opendocument.presentation" -> "odp"
        else -> "txt"
    }

    fun sanitizeFileName(fileName: String?, mimeType: String?): String {
        val fallbackExtension = defaultExtension(mimeType)
        val leaf = fileName.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001f<>:\"/\\\\|?*]"), "_")
            .trim()
            .trim('.')
        val candidate = leaf.ifBlank { "attachment.$fallbackExtension" }
        return if (extension(candidate) in supportedExtensions) candidate else "$candidate.$fallbackExtension"
    }

    fun availableFile(directory: File, requestedName: String): File {
        val base = requestedName.substringBeforeLast('.', requestedName)
        val extension = requestedName.substringAfterLast('.', "")
        var candidate = File(directory, requestedName)
        var index = 1
        while (candidate.exists()) {
            val suffix = if (extension.isBlank()) "" else ".$extension"
            candidate = File(directory, "$base ($index)$suffix")
            index++
        }
        return candidate
    }
}

internal object SharedPayloadParser {
    @Suppress("DEPRECATION")
    fun parse(context: Context, intent: Intent): SharedPayload? {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return null
        val streams = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        } else {
            listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        }
        val images = mutableListOf<Uri>()
        val documents = mutableListOf<Uri>()
        streams.forEach { uri ->
            val mime = context.contentResolver.getType(uri) ?: intent.type
            val name = SharedDocumentImporter.displayName(context, uri)
            when {
                mime?.startsWith("image/") == true -> images += uri
                SharedDocumentClassifier.isSupported(mime, name) -> documents += uri
            }
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf(String::isNotBlank)
        return SharedPayload(text, images, documents)
    }
}

internal object SharedDocumentImporter {
    fun import(context: Context, uris: List<Uri>, vaultPath: String, storagePath: String): DocumentImportResult {
        if (uris.isEmpty()) return DocumentImportResult(0, emptyList(), 0)
        val root = runCatching { File(vaultPath).canonicalFile }.getOrNull()
            ?: return DocumentImportResult(uris.size, emptyList(), uris.size)
        val requestedDirectory = VaultPathUtil.resolveTarget(vaultPath, storagePath)
            ?.let(::File)
            ?: root
        val directory = runCatching { requestedDirectory.canonicalFile }.getOrNull()
            ?.takeIf { it.path == root.path || it.path.startsWith(root.path + File.separator) }
            ?: return DocumentImportResult(uris.size, emptyList(), uris.size)
        if (!directory.exists() && !directory.mkdirs()) {
            return DocumentImportResult(uris.size, emptyList(), uris.size)
        }

        val links = mutableListOf<String>()
        var failed = 0
        uris.forEach { uri ->
            val mimeType = context.contentResolver.getType(uri)
            val displayName = displayName(context, uri)
            if (!SharedDocumentClassifier.isSupported(mimeType, displayName)) {
                failed++
                return@forEach
            }
            val safeName = SharedDocumentClassifier.sanitizeFileName(displayName, mimeType)
            val destination = SharedDocumentClassifier.availableFile(directory, safeName)
            val copied = runCatching {
                val input = context.contentResolver.openInputStream(uri) ?: return@runCatching false
                input.use { source ->
                    FileOutputStream(destination).use { output -> source.copyTo(output) }
                }
                true
            }.getOrDefault(false)
            if (!copied) {
                destination.delete()
                failed++
                return@forEach
            }
            val relativePath = destination.relativeTo(root).path.replace('\\', '/')
            links += "![[${relativePath}]]"
        }
        return DocumentImportResult(uris.size, links, failed)
    }

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }
    }.getOrNull()
}
