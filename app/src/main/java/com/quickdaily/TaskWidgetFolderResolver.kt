package com.quickdaily

import android.content.Context
import android.net.Uri
import com.quickdaily.util.FileUtil
import com.quickdaily.util.ReadResult
import com.quickdaily.util.VaultStorage
import com.quickdaily.util.VaultStorageException

/** A single folder candidate as observed by one scan. */
internal data class TaskWidgetFolderCandidate(
    val file: TaskWidgetFolderFile,
    val content: String?,
    val readError: Throwable? = null,
    val writable: Boolean = false,
) {
    val isReadable: Boolean
        get() = readError == null && content != null
}

/**
 * The result of one ordered folder scan.  The candidates are already in newest-first
 * order; callers must not sort them again with a different comparator.
 */
internal data class TaskWidgetFolderSnapshot(
    val folderUri: String,
    val candidates: List<TaskWidgetFolderCandidate>,
    val scanError: UiText? = null,
    val scanException: Throwable? = null,
) {
    val hasReadableFile: Boolean
        get() = candidates.any(TaskWidgetFolderCandidate::isReadable)

    val newestReadable: TaskWidgetFolderCandidate?
        get() = candidates.firstOrNull(TaskWidgetFolderCandidate::isReadable)
}

internal sealed interface TaskWidgetFolderActionResolution {
    data class Target(
        val sourcePath: String,
        val displayName: String,
        val effectiveTime: Long,
        val writable: Boolean,
    ) : TaskWidgetFolderActionResolution

    data class Failure(val message: UiText) : TaskWidgetFolderActionResolution
}

/** Shared scanner used by the folder widget renderer and its user actions. */
internal object TaskWidgetFolderResolver {
    private const val MAX_FINAL_SCAN_ATTEMPTS = 2

    fun scan(
        context: Context,
        config: TaskWidgetConfig,
        nowMillis: Long = System.currentTimeMillis(),
    ): TaskWidgetFolderSnapshot {
        if (config.scope != TaskWidgetScope.CUSTOM_FOLDER) {
            return TaskWidgetFolderSnapshot(
                folderUri = config.customFolderUri,
                candidates = emptyList(),
                scanError = UiText.Resource(R.string.qd_widget_not_folder_mode),
            )
        }
        val folderUri = config.customFolderUri.trim()
        if (folderUri.isBlank()) {
            return TaskWidgetFolderSnapshot(
                folderUri,
                emptyList(),
                UiText.Resource(R.string.qd_widget_custom_folder_unavailable),
            )
        }
        val uri = runCatching { Uri.parse(folderUri) }
            .getOrNull()
            ?.takeIf { it.scheme.equals("content", ignoreCase = true) }
            ?: return TaskWidgetFolderSnapshot(
                folderUri,
                emptyList(),
                UiText.Resource(R.string.qd_widget_folder_uri_invalid),
            )

        val files = try {
            VaultStorage.listMarkdownFilesFromTree(
                context = context,
                treeUri = uri,
                recursive = config.folderIncludeSubfolders,
            )
        } catch (error: VaultStorageException) {
            BetaLogger.logException(
                "TaskWidget/Folder",
                "folder_scan_failed uri=$folderUri recursive=${config.folderIncludeSubfolders}",
                error,
            )
            return TaskWidgetFolderSnapshot(
                folderUri = folderUri,
                candidates = emptyList(),
                scanError = UiText.Resource(R.string.qd_widget_folder_inaccessible),
                scanException = error,
            )
        } catch (error: Exception) {
            BetaLogger.logException("TaskWidget/Folder", "folder_scan_unexpected uri=$folderUri", error)
            return TaskWidgetFolderSnapshot(
                folderUri = folderUri,
                candidates = emptyList(),
                scanError = UiText.Resource(R.string.qd_widget_folder_read_failed),
                scanException = error,
            )
        }

        val selectedFiles = TaskWidgetFolderPolicy.selectAndSort(
            files = files,
            window = config.folderTimeWindow,
            nowMillis = nowMillis,
        ) { file ->
            BetaLogger.log(
                "TaskWidget/FolderTime",
                "creation_time_unavailable path=${file.sourcePath} " +
                    "fallback=lastModified lastModified=${file.lastModified}",
            )
        }

        val candidates = selectedFiles.map { file ->
            when (val result = FileUtil.readResult(file.sourcePath)) {
                is ReadResult.Success -> TaskWidgetFolderCandidate(
                    file = file,
                    content = result.content,
                    writable = FileUtil.canWrite(file.sourcePath),
                )
                ReadResult.NotFound -> {
                    BetaLogger.log(
                        "TaskWidget/Folder",
                        "skip_unavailable path=${file.sourcePath} reason=not_found",
                    )
                    TaskWidgetFolderCandidate(
                        file = file,
                        content = null,
                        readError = IllegalStateException("文件不存在"),
                    )
                }
                is ReadResult.Error -> {
                    BetaLogger.logException(
                        "TaskWidget/Folder",
                        "skip_unavailable path=${file.sourcePath} reason=read_failed",
                        result.exception,
                    )
                    TaskWidgetFolderCandidate(
                        file = file,
                        content = null,
                        readError = result.exception,
                    )
                }
            }
        }

        BetaLogger.log(
            "TaskWidget/FolderScan",
            "uri=$folderUri window=${config.folderTimeWindow.key} " +
                "recursive=${config.folderIncludeSubfolders} files=${files.size} " +
                "selected=${candidates.size} readable=${candidates.count { it.isReadable }}",
        )
        return TaskWidgetFolderSnapshot(folderUri = folderUri, candidates = candidates)
    }

    /**
     * Resolve the target immediately before an action is launched.  A fresh scan is
     * the selection point, and a disappearing file is retried without touching any
     * editor or floating-window state.
     */
    fun resolveForAction(
        context: Context,
        config: TaskWidgetConfig,
        requireWritable: Boolean = true,
    ): TaskWidgetFolderActionResolution {
        repeat(MAX_FINAL_SCAN_ATTEMPTS) { attempt ->
            val snapshot = scan(context, config)
            snapshot.scanError?.let { return TaskWidgetFolderActionResolution.Failure(it) }
            if (snapshot.candidates.isEmpty()) {
                return TaskWidgetFolderActionResolution.Failure(
                    UiText.Resource(R.string.qd_widget_folder_no_files),
                )
            }

            for (candidate in snapshot.candidates) {
                if (!candidate.isReadable) continue
                val finalRead = FileUtil.readResult(candidate.file.sourcePath)
                if (finalRead !is ReadResult.Success) {
                    BetaLogger.log(
                        "TaskWidget/FolderAction",
                        "candidate_invalid attempt=$attempt path=${candidate.file.sourcePath}",
                    )
                    continue
                }
                val writable = FileUtil.canWrite(candidate.file.sourcePath)
                if (requireWritable && !writable) {
                    return TaskWidgetFolderActionResolution.Failure(
                        UiText.Resource(
                            R.string.qd_widget_latest_file_unwritable,
                            listOf(displayName(context, candidate.file.displayName)),
                        ),
                    )
                }
                return TaskWidgetFolderActionResolution.Target(
                    sourcePath = candidate.file.sourcePath,
                    displayName = displayName(context, candidate.file.displayName),
                    effectiveTime = candidate.file.effectiveTime,
                    writable = writable,
                )
            }
        }
        return TaskWidgetFolderActionResolution.Failure(
            UiText.Resource(R.string.qd_widget_folder_files_unavailable),
        )
    }

    fun displayName(fileName: String): String =
        if (fileName.endsWith(".md", ignoreCase = true)) {
            fileName.dropLast(3).ifBlank { "未命名页面" }
        } else {
            fileName.ifBlank { "未命名页面" }
        }

    fun displayName(context: Context, fileName: String): String {
        val displayName = displayName(fileName)
        return if (displayName == "未命名页面") {
            LocaleController.localizedContext(context).getString(R.string.qd_widget_unnamed_page)
        } else {
            displayName
        }
    }
}
