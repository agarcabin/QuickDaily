package com.quickdaily

import android.content.Context
import android.net.Uri
import com.quickdaily.util.ContentUtil
import com.quickdaily.util.DateUtil
import com.quickdaily.util.DiaryAppendUtil
import com.quickdaily.util.FileUtil
import com.quickdaily.util.ImageUtil
import com.quickdaily.util.RecentTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface FloatingNoteSaveResult {
    data object Saved : FloatingNoteSaveResult
    data object NoContent : FloatingNoteSaveResult
    data class Failed(val message: String) : FloatingNoteSaveResult
}

/** The single save path used by NoteEditActivity and FloatingNoteService. */
class FloatingNoteSaveUseCase(private val context: Context) {
    suspend fun save(
        text: String,
        selectedImages: List<Uri>,
        pendingAttachments: List<Uri>
    ): FloatingNoteSaveResult = withContext(Dispatchers.IO) {
        if (!hasRealContent(text) && selectedImages.isEmpty() && pendingAttachments.isEmpty()) {
            return@withContext FloatingNoteSaveResult.NoContent
        }

        val prefs = context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
        val vaultPath = prefs.getString("vault_path", "").orEmpty()
        if (vaultPath.isBlank()) return@withContext FloatingNoteSaveResult.Failed("请先设置仓库路径")

        val diaryFolder = prefs.getString("diary_folder", "Daily").orEmpty()
        val dateFormat = prefs.getString("date_format", "YYYY-MM-DD").orEmpty()
        val path = "${vaultPath.trimEnd('/')}/${diaryFolder.trimEnd('/')}/${DateUtil.todayStr(dateFormat)}.md"
        val anchor = prefs.getString("anchor_text", "").orEmpty().trim()
        val timestampFormat = prefs.getString("timestamp_format", "list_time").orEmpty()
        val addAnchorIfMissing = prefs.getBoolean("add_anchor_if_missing", true)
        val timestampOrder = prefs.getString("timestamp_order", "above").orEmpty()

        val trimmedText = text.trim()
        val isTask = trimmedText.startsWith("- [ ]") ||
            trimmedText.startsWith("- [x]", ignoreCase = true)
        val line = if (isTask) {
            val wasChecked = trimmedText.startsWith("- [x]", ignoreCase = true)
            val taskDesc = trimmedText
                .removePrefix("- [ ]")
                .removePrefix("- [x]")
                .removePrefix("- [X]")
                .trim()
            val marker = if (wasChecked) "- [x]" else "- [ ]"
            when (timestampFormat) {
                "none", "list", "ordered" -> "$marker $taskDesc"
                "time_only" -> "$marker ${DateUtil.nowTimeStr()} $taskDesc"
                "time_only_seconds" -> "$marker ${DateUtil.nowTimeSecondsStr()} $taskDesc"
                "list_time" -> "$marker ${DateUtil.nowTimeStr()} $taskDesc"
                "list_time_seconds" -> "$marker ${DateUtil.nowTimeSecondsStr()} $taskDesc"
                else -> "$marker $taskDesc"
            }
        } else {
            when (timestampFormat) {
                "none" -> trimmedText
                "time_only" -> "${DateUtil.nowTimeStr()} $trimmedText"
                "time_only_seconds" -> "${DateUtil.nowTimeSecondsStr()} $trimmedText"
                "list" -> "- $trimmedText"
                "ordered" -> "1. $trimmedText"
                "list_time" -> "- ${DateUtil.nowTimeStr()} $trimmedText"
                "list_time_seconds" -> "- ${DateUtil.nowTimeSecondsStr()} $trimmedText"
                else -> trimmedText
            }
        }

        val existing = FileUtil.read(path)
        var parsed = ContentUtil.parseFrontmatter(existing)
        var body = if (parsed.hasFrontmatter) parsed.body else existing

        if (existing.isEmpty() || (parsed.hasFrontmatter && parsed.body.isBlank())) {
            val templatePath = prefs.getString("template_path", "").orEmpty()
            if (templatePath.isNotBlank()) {
                val template = if (templatePath.startsWith("/")) templatePath
                else "${vaultPath.trimEnd('/')}/$templatePath"
                FileUtil.readOrNull(template)?.takeIf { it.isNotEmpty() }?.let {
                    parsed = ContentUtil.parseFrontmatter(it)
                    body = if (parsed.hasFrontmatter) parsed.body else it
                }
            }
        }

        if (anchor.isNotEmpty() && !body.contains(anchor) && addAnchorIfMissing) {
            body = if (body.isNotEmpty() && !body.endsWith("\n")) "$body\n$anchor\n" else "$body$anchor\n"
        }

        var resolvedLine = line
        for (uri in pendingAttachments) {
            val attachStoragePath = prefs.getString("image_storage_path", "").orEmpty()
            val relPath = runCatching {
                ImageUtil.copyToVault(context, uri, vaultPath, attachStoragePath, "original", "obsidian_wikilink")
            }.getOrNull()
            if (relPath.isNullOrBlank()) {
                return@withContext FloatingNoteSaveResult.Failed("附件保存失败，请检查存储路径权限")
            }
            resolvedLine = resolvedLine.replace(uri.toString(), relPath)
        }

        val imageLinks = if (selectedImages.isNotEmpty()) {
            val links = runCatching {
                ImageUtil.processImages(
                    context,
                    selectedImages,
                    vaultPath,
                    prefs.getString("image_storage_path", "").orEmpty(),
                    prefs.getString("image_naming_format", "timestamp_ext").orEmpty(),
                    prefs.getString("image_link_format", "described").orEmpty(),
                    prefs.getString("image_custom_naming_format", "").orEmpty()
                )
            }.getOrDefault(emptyList())
            if (links.isEmpty()) return@withContext FloatingNoteSaveResult.Failed("图片保存失败，请检查存储路径权限")
            links
        } else emptyList()

        val entryLines = buildList {
            add(resolvedLine)
            addAll(imageLinks)
        }
        val workingContent = body
        val newContent = if (timestampOrder == "below") {
            DiaryAppendUtil.appendAtAnchorSectionEnd(workingContent, anchor, entryLines)
        } else if (anchor.isNotEmpty() && workingContent.contains(anchor)) {
            val index = workingContent.indexOf(anchor) + anchor.length
            workingContent.substring(0, index) + "\n" + entryLines.joinToString("\n") + workingContent.substring(index)
        } else if (workingContent.isEmpty()) {
            entryLines.joinToString("\n") + "\n"
        } else if (workingContent.endsWith("\n")) {
            workingContent + entryLines.joinToString("\n") + "\n"
        } else {
            workingContent + "\n" + entryLines.joinToString("\n") + "\n"
        }
        val output = if (parsed.hasFrontmatter) {
            ContentUtil.reconstructWithFrontmatter(parsed.frontmatter, newContent)
        } else newContent

        FileUtil.write(path, output)
        RecentTags.recordFromText(context, text)
        WidgetRefreshHelper.refreshAll(context)
        FloatingNoteSaveResult.Saved
    }
}
