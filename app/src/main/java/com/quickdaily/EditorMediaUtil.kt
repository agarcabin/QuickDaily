package com.quickdaily

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.quickdaily.util.ImageUtil
import com.quickdaily.util.VaultStoragePrefs
import java.io.File

object EditorMediaUtil {
    fun imageLink(context: Context, uri: Uri, targetPath: String? = null): String? {
        val prefs = context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
        val vault = VaultStoragePrefs.current(context).rootPath
        val rules = ObsidianConfigSync.rules(context, vault)
        return ImageUtil.processImages(
            context = context,
            uris = listOf(uri),
            vaultPath = VaultStoragePrefs.current(context).rootPath,
            storagePath = prefs.getString("image_storage_path", "").orEmpty(),
            namingFormat = prefs.getString("image_naming_format", "timestamp_original").orEmpty(),
            linkFormat = prefs.getString("image_link_format", "described").orEmpty(),
            customNamingFormat = prefs.getString("image_custom_naming_format", "").orEmpty(),
            sourceNote = ObsidianConfigSync.sourceNote(context, vault, targetPath, rules),
            rules = rules,
        ).singleOrNull()
    }

    fun audioLink(context: Context, file: File, targetPath: String? = null): String? {
        val prefs = context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
        val vault = VaultStoragePrefs.current(context).rootPath
        val rules = ObsidianConfigSync.rules(context, vault)
        val source = ObsidianConfigSync.sourceNote(context, vault, targetPath, rules)
        val relativePath = ImageUtil.copyLocalFileToVault(
            sourceFile = file,
            vaultPath = VaultStoragePrefs.current(context).rootPath,
            storagePath = rules.attachmentFolder,
            namingFormat = prefs.getString("image_naming_format", "timestamp_original").orEmpty(),
            customNamingFormat = prefs.getString("image_custom_naming_format", "").orEmpty(),
            sourceNote = source,
        ) ?: return null
        return ObsidianLinkPolicy.link(source, relativePath, rules, knownPaths = ObsidianFileIndex.paths(vault))
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

    fun insertLinks(text: String, selection: TextRange, links: List<String>): TextFieldValue =
        links.fold(TextFieldValue(text, selection)) { current, link ->
            insertLink(current.text, current.selection, link)
        }
}
