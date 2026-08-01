package com.quickdaily

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.quickdaily.util.ImageUtil
import java.io.File

object EditorMediaUtil {
    fun imageLink(context: Context, uri: Uri): String? {
        val prefs = context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
        return ImageUtil.processImages(
            context = context,
            uris = listOf(uri),
            vaultPath = prefs.getString("vault_path", "").orEmpty(),
            storagePath = prefs.getString("image_storage_path", "").orEmpty(),
            namingFormat = prefs.getString("image_naming_format", "timestamp_original").orEmpty(),
            linkFormat = prefs.getString("image_link_format", "described").orEmpty(),
            customNamingFormat = prefs.getString("image_custom_naming_format", "").orEmpty(),
        ).singleOrNull()
    }

    fun audioLink(context: Context, file: File): String? {
        val prefs = context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
        val relativePath = ImageUtil.copyLocalFileToVault(
            sourceFile = file,
            vaultPath = prefs.getString("vault_path", "").orEmpty(),
            storagePath = prefs.getString("image_storage_path", "").orEmpty(),
            namingFormat = prefs.getString("image_naming_format", "timestamp_original").orEmpty(),
            customNamingFormat = prefs.getString("image_custom_naming_format", "").orEmpty(),
        ) ?: return null
        return audioLinkForRelativePath(relativePath)
    }

    fun audioLinkForRelativePath(relativePath: String): String =
        "![[${relativePath.replace('\\', '/')}]]"

    fun insertLink(text: String, selection: TextRange, link: String): TextFieldValue {
        val start = minOf(selection.start, selection.end).coerceIn(0, text.length)
        val end = maxOf(selection.start, selection.end).coerceIn(start, text.length)
        val insertion = "$link\n"
        val nextText = text.substring(0, start) + insertion + text.substring(end)
        val cursor = start + insertion.length
        return TextFieldValue(nextText, TextRange(cursor))
    }
}
