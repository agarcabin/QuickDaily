package com.quickdaily.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 图片处理工具 */
object ImageUtil {

    private val TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")

    /**
     * 根据命名格式生成图片文件名。
     * @param format 命名格式: timestamp_ext | timestamp_original | original
     * @param displayName 原始文件名（不含扩展名）
     * @param extension 扩展名（含点号，如 .jpg）
     */
    fun generateFileName(format: String, displayName: String, extension: String, customPattern: String = ""): String {
        val ts = LocalDateTime.now().format(TIMESTAMP_FMT)
        return when (format) {
            "timestamp_ext" -> "$ts$extension"
            "timestamp_original" -> "${ts}_${displayName}$extension"
            "original" -> "${displayName}$extension"
            "custom" -> {
                if (customPattern.isBlank()) "${ts}_${displayName}$extension"
                else {
                    var result = customPattern
                    // Replace timestamp pattern tokens with actual values
                    val now = LocalDateTime.now()
                    result = result.replace("yyyy", now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy")))
                    result = result.replace("MM", now.format(java.time.format.DateTimeFormatter.ofPattern("MM")))
                    result = result.replace("dd", now.format(java.time.format.DateTimeFormatter.ofPattern("dd")))
                    result = result.replace("HH", now.format(java.time.format.DateTimeFormatter.ofPattern("HH")))
                    result = result.replace("mm", now.format(java.time.format.DateTimeFormatter.ofPattern("mm")))
                    result = result.replace("ss", now.format(java.time.format.DateTimeFormatter.ofPattern("ss")))
                    result = result.replace("SSS", now.format(java.time.format.DateTimeFormatter.ofPattern("SSS")))
                    // Replace placeholders
                    result = result.replace("{filename}", displayName)
                    result = result.replace("{ext}", extension)
                    result
                }
            }
            else -> "$ts$extension"
        }
    }

    /**
     * Generate preview of the filename with given format.
     */
    fun generateFileNamePreview(format: String, displayName: String, extension: String, customPattern: String = ""): String {
        return generateFileName(format, displayName, extension, customPattern)
    }

    /**
     * Generate markdown link preview.
     */
    fun markdownLinkPreview(relativePath: String, format: String): String {
        return markdownLink(relativePath, format)
    }

    /**
     * 生成 Markdown 图片引用。
     * @param relativePath 相对于 vault 的路径（含文件名）
     * @param format "bare" 或 "described"
     */
    fun markdownLink(relativePath: String, format: String): String {
        return when (format) {
            "bare" -> "![]($relativePath)"
           "described" -> "![${relativePath.substringAfterLast("/").substringBeforeLast(".")}]($relativePath)"
            "obsidian_wikilink" -> "![[${relativePath.substringAfterLast("/")}]]"
            else -> "![]($relativePath)"
        }
    }

    /**
     * 从 content URI 获取文件扩展名。
     */
    fun getExtension(context: Context, uri: Uri): String {
        var name: String? = null
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) { name = it.getString(idx) }
                }
            }
        } catch (_: Exception) {}
        val n = name
        if (n != null && n.contains(".")) {
            return "." + n.substringAfterLast(".")
        }
        val mimeType = context.contentResolver.getType(uri) ?: return ".bin"
        return when {
            mimeType.contains("png") -> ".png"
            mimeType.contains("gif") -> ".gif"
            mimeType.contains("webp") -> ".webp"
            mimeType.contains("bmp") -> ".bmp"
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> ".jpg"
            mimeType.contains("pdf") -> ".pdf"
            mimeType.contains("msword") || mimeType.contains("word") -> ".doc"
            mimeType.contains("spreadsheet") || mimeType.contains("excel") || mimeType.contains("sheet") -> ".xls"
            mimeType.contains("presentation") || mimeType.contains("powerpoint") || mimeType.contains("ppt") -> ".ppt"
            mimeType.contains("text") -> ".txt"
            mimeType.contains("html") -> ".html"
            mimeType.contains("json") -> ".json"
            mimeType.contains("zip") -> ".zip"
            mimeType.contains("rar") -> ".rar"
            mimeType.contains("octet-stream") -> ".bin"
            mimeType.contains("video") -> ".mp4"
            mimeType.contains("audio") -> ".mp3"
            else -> ".bin"
        }
    }

    /**
     * 获取图片的原始文件名（不含扩展名）。
     */
    fun getDisplayName(context: Context, uri: Uri): String {
        var name = "image"
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        name = it.getString(idx) ?: "image"
                        name = name.substringBeforeLast(".")
                    }
                }
            }
        } catch (_: Exception) {}
        return name
    }

    /**
     * 将图片从 content URI 复制到 vault 目录。
     * @return 相对于 vault 的文件路径（如 "assets/20260708_143021.jpg"），失败返回 null
     */
    fun copyToVault(
        context: Context,
        sourceUri: Uri,
        vaultPath: String,
        storagePath: String,
        namingFormat: String,
        linkFormat: String,
        customNamingFormat: String = ""
    ): String? {
        return try {
            val ext = getExtension(context, sourceUri)
            val displayName = getDisplayName(context, sourceUri)
            val fileName = generateFileName(namingFormat, displayName, ext, customNamingFormat)
            // 确定存储目录
            val dir = if (storagePath.isBlank()) "" else storagePath.trim('/')
            val destDir = if (dir.isNotEmpty()) "${vaultPath.trimEnd('/')}/$dir" else vaultPath.trimEnd('/')
            File(destDir).mkdirs()
            val destFile = File(destDir, fileName)
            // 复制
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            // 返回相对路径
            if (dir.isNotEmpty()) "$dir/$fileName" else fileName
        } catch (e: Exception) {
            android.util.Log.e("QuickDaily", "复制图片失败: ${e.message}")
            null
        }
    }

    /**
     * 批量处理图片并生成 Markdown 引用列表。
     */
    fun processImages(
        context: Context,
        uris: List<Uri>,
        vaultPath: String,
        storagePath: String,
        namingFormat: String,
        linkFormat: String,
        customNamingFormat: String = ""
    ): List<String> {
        return uris.mapNotNull { uri ->
            val relPath = copyToVault(context, uri, vaultPath, storagePath, namingFormat, linkFormat, customNamingFormat)
            relPath?.let { markdownLink(it, linkFormat) }
        }
    }
}
