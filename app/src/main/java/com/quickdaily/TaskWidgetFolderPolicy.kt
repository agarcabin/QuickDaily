package com.quickdaily

import com.quickdaily.util.VaultFileMetadata
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TaskWidgetFolderFile(
    val sourcePath: String,
    val relativePath: String,
    val displayName: String,
    val effectiveTime: Long,
    val usedCreationTime: Boolean,
)

/** Pure date, fallback and ordering rules for custom-folder task widgets. */
object TaskWidgetFolderPolicy {
    fun effectiveTime(
        file: VaultFileMetadata,
        onFallback: ((VaultFileMetadata) -> Unit)? = null,
    ): TaskWidgetFolderFile {
        val creation = file.creationTime?.takeIf { it > 0L }
        val usedCreationTime = creation != null
        if (!usedCreationTime) onFallback?.invoke(file)
        return TaskWidgetFolderFile(
            sourcePath = file.sourcePath,
            relativePath = file.relativePath,
            displayName = file.displayName,
            effectiveTime = creation ?: file.lastModified,
            usedCreationTime = usedCreationTime,
        )
    }

    fun selectAndSort(
        files: List<VaultFileMetadata>,
        window: TaskWidgetFolderTimeWindow,
        nowMillis: Long,
        onFallback: ((VaultFileMetadata) -> Unit)? = null,
    ): List<TaskWidgetFolderFile> {
        val candidates = files.map { effectiveTime(it, onFallback) }
        val filtered = candidates.filter { file -> isInWindow(file.effectiveTime, window, nowMillis) }
        return filtered.sortedWith(
            compareByDescending<TaskWidgetFolderFile> { it.effectiveTime }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.relativePath }
                .thenBy { it.relativePath }
                .thenBy { it.sourcePath },
        )
    }

    fun isInWindow(timeMillis: Long, window: TaskWidgetFolderTimeWindow, nowMillis: Long): Boolean {
        if (window == TaskWidgetFolderTimeWindow.ALL) return true
        if (timeMillis <= 0L) return false
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val start = today.minusDays((window.rollingDays!! - 1).toLong())
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return timeMillis in start until end
    }

    fun effectiveDate(timeMillis: Long): LocalDate? =
        timeMillis.takeIf { it > 0L }
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
}
