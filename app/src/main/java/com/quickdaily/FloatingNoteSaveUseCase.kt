package com.quickdaily

import android.content.Context
import android.net.Uri
import com.quickdaily.util.ContentUtil
import com.quickdaily.util.DateUtil
import com.quickdaily.util.DiaryAppendUtil
import com.quickdaily.util.FileUtil
import com.quickdaily.util.FileSaveResult
import com.quickdaily.util.ImageUtil
import com.quickdaily.util.RecentTags
import com.quickdaily.util.VaultPathUtil
import com.quickdaily.util.VaultStoragePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface FloatingNoteSaveResult {
    data object Saved : FloatingNoteSaveResult
    data object NoContent : FloatingNoteSaveResult
    data class Failed(
        val message: UiText,
        val saveResult: FileSaveResult? = null,
    ) : FloatingNoteSaveResult
}

/** The single save path used by NoteEditActivity and FloatingNoteService. */
class FloatingNoteSaveUseCase(private val context: Context) {
    internal suspend fun save(
        text: String,
        selectedImages: List<Uri>,
        pendingAttachments: List<Uri>,
        targetRelativePath: String? = null,
        request: FloatingNoteSaveRequest? = null,
    ): FloatingNoteSaveResult = withContext(Dispatchers.IO) {
        if (!hasRealContent(text) && selectedImages.isEmpty() && pendingAttachments.isEmpty()) {
            return@withContext FloatingNoteSaveResult.NoContent
        }

        val prefs = context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
        val vaultPath = request?.vault ?: VaultStoragePrefs.current(context).rootPath
        if (vaultPath.isBlank() && targetRelativePath.isNullOrBlank()) {
            return@withContext FloatingNoteSaveResult.Failed(UiText.Resource(R.string.qd_editor_vault_path_required))
        }

        val rules = request?.rules ?: ObsidianConfigSync.rules(context, vaultPath)
        val diaryFolder = rules.diaryFolder
        val dateFormat = rules.dateFormat
        val path = request?.path ?: if (targetRelativePath.isNullOrBlank()) {
            VaultPathUtil.resolve(vaultPath, VaultPathUtil.dailyRelativePath(diaryFolder, DateUtil.todayStr(dateFormat)))
                ?: return@withContext FloatingNoteSaveResult.Failed(UiText.Resource(R.string.qd_editor_target_unavailable))
        } else {
            VaultPathUtil.resolveTarget(vaultPath, targetRelativePath)
                ?: return@withContext FloatingNoteSaveResult.Failed(UiText.Resource(R.string.qd_editor_target_unavailable))
        }
        val anchor = prefs.getString("anchor_text", "").orEmpty().trim()
        val sourceNote = ObsidianConfigSync.sourceNote(context, vaultPath, path, rules)
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
                "date_time" -> "$marker ${DateUtil.nowDateTimeChineseStr()} $taskDesc"
                "list_date_time" -> "$marker ${DateUtil.nowDateTimeChineseStr()} $taskDesc"
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
                "date_time" -> "${DateUtil.nowDateTimeChineseStr()} $trimmedText"
                "list_date_time" -> "- ${DateUtil.nowDateTimeChineseStr()} $trimmedText"
                else -> trimmedText
            }
        }

        val mutationGuard = FileUtil.acquirePathMutation(path)
        try {
        val signature = request?.signature
        val receipts = FloatingNoteSaveReceipts(context)
        if (signature != null) when (receipts.reconcile(path, signature)) {
            ReceiptDecision.SAVED -> return@withContext FloatingNoteSaveResult.Saved
            ReceiptDecision.UNCERTAIN -> return@withContext FloatingNoteSaveResult.Failed(UiText.Resource(R.string.qd_save_unconfirmed))
            ReceiptDecision.READY -> Unit
        }
        val initialFingerprint = FileUtil.fingerprint(path)
            ?: return@withContext FloatingNoteSaveResult.Failed(UiText.Resource(R.string.qd_save_backup_failed))
        val existing = when (val read = FileUtil.readResult(path)) {
            is com.quickdaily.util.ReadResult.Success -> read.content
            com.quickdaily.util.ReadResult.NotFound -> ""
            is com.quickdaily.util.ReadResult.Error -> return@withContext FloatingNoteSaveResult.Failed(UiText.Resource(R.string.qd_widget_read_failed))
        }
        var parsed = ContentUtil.parseFrontmatter(existing)
        var body = if (parsed.hasFrontmatter) parsed.body else existing

        if (existing.isEmpty() || (parsed.hasFrontmatter && parsed.body.isBlank())) {
            val templatePath = rules.templatePath
            if (templatePath.isNotBlank()) {
                val template = VaultPathUtil.resolveTarget(vaultPath, templatePath) ?: templatePath
                val templateContent = when (val read = FileUtil.readResult(template)) {
                    is com.quickdaily.util.ReadResult.Success -> read.content
                    else -> return@withContext FloatingNoteSaveResult.Failed(UiText.Resource(R.string.qd_save_template_failed))
                }
                parsed = ContentUtil.parseFrontmatter(templateContent)
                body = if (parsed.hasFrontmatter) parsed.body else templateContent
            }
        }

        if (anchor.isNotEmpty() && !body.contains(anchor) && addAnchorIfMissing) {
            body = if (body.isNotEmpty() && !body.endsWith("\n")) "$body\n$anchor\n" else "$body$anchor\n"
        }

        var resolvedLine = line
        for (uri in pendingAttachments) {
            val attachStoragePath = rules.attachmentFolder
            val relPath = runCatching {
                ImageUtil.copyToVault(context, uri, vaultPath, attachStoragePath, "original", "obsidian_wikilink", sourceNote = sourceNote)
            }.getOrNull()
            if (relPath.isNullOrBlank()) {
                return@withContext FloatingNoteSaveResult.Failed(UiText.Resource(R.string.qd_editor_attachment_save_failed))
            }
            val link = ObsidianLinkPolicy.link(sourceNote, relPath, rules, knownPaths = ObsidianFileIndex.paths(vaultPath))
            resolvedLine = resolvedLine.replace("![[${uri}]]", link).replace(uri.toString(), relPath)
        }

        val imageLinks = if (selectedImages.isNotEmpty()) {
            val links = runCatching {
                ImageUtil.processImages(
                    context,
                    selectedImages,
                    vaultPath,
                    rules.attachmentFolder,
                    prefs.getString("image_naming_format", "timestamp_ext").orEmpty(),
                    if (rules.markdownLinks) "described" else "obsidian_wikilink",
                    prefs.getString("image_custom_naming_format", "").orEmpty(),
                    sourceNote = sourceNote, rules = rules,
                )
            }.getOrDefault(emptyList())
            if (links.isEmpty()) return@withContext FloatingNoteSaveResult.Failed(UiText.Resource(R.string.qd_editor_image_save_failed))
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

        if (signature != null && !receipts.prepare(path, signature, initialFingerprint, com.quickdaily.util.FileFingerprint.fromBytes(output.toByteArray(Charsets.UTF_8)))) {
            return@withContext FloatingNoteSaveResult.Failed(UiText.Resource(R.string.qd_save_backup_failed))
        }
        val saveResult = FileUtil.saveTextResult(path, output, expectedFingerprint = initialFingerprint)
        if (!saveResult.succeeded) {
            val message = saveResult.failureMessage()
            BetaLogger.log(
                "FloatingNote/Save",
                "coordinated_save_failed status=${saveResult.status} stage=${saveResult.stage} " +
                    "targetKey=${saveResult.targetKey} backup=${saveResult.backup != null}",
            )
            return@withContext FloatingNoteSaveResult.Failed(message, saveResult)
        }
        RecentTags.recordFromText(context, text)
        WidgetRefreshHelper.refreshAll(context)
        FloatingNoteSaveResult.Saved
        } finally {
            mutationGuard.close()
        }
    }
}
