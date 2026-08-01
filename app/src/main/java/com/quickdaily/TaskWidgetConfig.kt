package com.quickdaily

import android.content.Context
import android.net.Uri
import com.quickdaily.util.UriUtil
import com.quickdaily.util.VaultPathUtil
import org.json.JSONArray
import java.io.File

enum class TaskWidgetScope(
    val key: String,
    val label: String,
) {
    TODAY("today", "今日任务"),
    WEEK("week", "本周任务"),
    MONTH("month", "本月任务"),
    CUSTOM("custom", "自定义页面任务");

    companion object {
        fun fromKey(value: String?): TaskWidgetScope =
            entries.firstOrNull { it.key == value } ?: TODAY
    }
}

data class TaskWidgetConfig(
    val scope: TaskWidgetScope = TaskWidgetScope.TODAY,
    val customRelativePath: String = "",
)

object TaskWidgetConfigStore {
    private const val PREFS = "QuickDaily"
    private const val KEY_PREFIX = "task_widget_"
    private const val SCOPE_SUFFIX = "_scope"
    private const val PATH_SUFFIX = "_path"
    private const val CUSTOM_HISTORY_KEY = "task_widget_custom_pages"

    fun load(context: Context, widgetId: Int): TaskWidgetConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedScope = prefs.getString(scopeKey(widgetId), null)
        val storedPath = prefs.getString(pathKey(widgetId), "").orEmpty()
        if (storedScope != null) {
            val config = TaskWidgetConfig(TaskWidgetScope.fromKey(storedScope), storedPath)
            if (config.scope == TaskWidgetScope.CUSTOM && config.customRelativePath.isNotBlank()) {
                recordCustomPage(context, config.customRelativePath)
            }
            BetaLogger.log(
                "TaskWidgetConfig",
                "load widgetId=$widgetId source=stored scope=${config.scope.key} path=${config.customRelativePath}",
            )
            return config
        }

        // Existing installations only had a global period. Snapshot it the first
        // time each widget is rendered so later changes do not affect this widget.
        val migratedScope = TaskWidgetScope.fromKey(prefs.getString("task_period", "today"))
        val migrated = TaskWidgetConfig(migratedScope)
        save(context, widgetId, migrated)
        BetaLogger.log(
            "TaskWidgetConfig",
            "load widgetId=$widgetId source=migrated scope=${migrated.scope.key} path=${migrated.customRelativePath}",
        )
        return migrated
    }

    fun save(context: Context, widgetId: Int, config: TaskWidgetConfig) {
        val committed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(scopeKey(widgetId), config.scope.key)
            .putString(pathKey(widgetId), config.customRelativePath.trim())
            .commit()
        if (config.scope == TaskWidgetScope.CUSTOM && config.customRelativePath.isNotBlank()) {
            recordCustomPage(context, config.customRelativePath)
        }
        BetaLogger.log(
            "TaskWidgetConfig",
            "save widgetId=$widgetId scope=${config.scope.key} path=${config.customRelativePath} committed=$committed",
        )
    }

    fun clear(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(scopeKey(widgetId))
            .remove(pathKey(widgetId))
            .apply()
        BetaLogger.log("TaskWidgetConfig", "clear widgetId=$widgetId")
    }

    fun customFilePath(context: Context, config: TaskWidgetConfig): String? {
        if (config.scope != TaskWidgetScope.CUSTOM || config.customRelativePath.isBlank()) return null
        if (!isMarkdownPath(config.customRelativePath)) return null
        val vaultPath = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("vault_path", "")
            .orEmpty()
        val resolved = VaultPathUtil.resolveTarget(vaultPath, config.customRelativePath)
        BetaLogger.log(
            "TaskWidgetConfig",
            "resolve custom scope=${config.scope.key} relative=${config.customRelativePath} vault=$vaultPath resolved=${resolved.orEmpty()}",
        )
        return resolved
    }

    /** Return the selected absolute filesystem path; it may be outside the vault. */
    fun filePathFromUri(context: Context, uri: Uri): String? {
        val selectedPath = UriUtil.documentUriToPath(context, uri)
        if (selectedPath == null) {
            BetaLogger.log("PageSelection/Picker", "uri=$uri result=unresolved")
            return null
        }
        if (!isMarkdownPath(selectedPath)) {
            BetaLogger.log("PageSelection/Picker", "uri=$uri result=not_markdown path=$selectedPath")
            return null
        }
        val canonical = runCatching {
            File(selectedPath).canonicalFile.takeIf { it.isFile }?.path
        }.getOrNull()
        BetaLogger.log("PageSelection/Picker", "uri=$uri result=${canonical.orEmpty()} path=$selectedPath")
        return canonical
    }

    fun displayName(config: TaskWidgetConfig): String =
        displayName(config.customRelativePath)

    fun displayName(path: String): String =
        File(path.trim()).nameWithoutExtension

    /** Pages selected from any task widget, newest first. */
    fun recentCustomPaths(context: Context): List<String> =
        readCustomHistory(context)

    fun removeCustomPage(context: Context, path: String) {
        val normalized = normalizeHistoryPath(context, path)
        val remaining = readCustomHistory(context).filterNot { it == normalized || it == path }
        writeCustomHistory(context, remaining)
        BetaLogger.log(
            "PageSelection/History",
            "removed path=$path normalized=$normalized remaining=${remaining.joinToString("|")}",
        )
    }

    internal fun isMarkdownPath(path: String): Boolean =
        path.trim().isNotBlank() && path.trim().endsWith(".md", ignoreCase = true)

    internal fun storageKeys(widgetId: Int): Pair<String, String> =
        scopeKey(widgetId) to pathKey(widgetId)

    internal fun recordCustomPage(context: Context, path: String) {
        if (!isMarkdownPath(path)) return
        val normalized = normalizeHistoryPath(context, path)
        val updated = TaskWidgetPageHistory.remember(readCustomHistory(context), normalized)
        writeCustomHistory(context, updated)
        BetaLogger.log(
            "PageSelection/History",
            "recorded path=$path normalized=$normalized history=${updated.joinToString("|")}",
        )
    }

    private fun readCustomHistory(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CUSTOM_HISTORY_KEY, null)
            ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (index in 0 until json.length()) {
                    val path = json.optString(index).trim()
                    if (isMarkdownPath(path)) add(path)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeCustomHistory(context: Context, paths: List<String>) {
        val json = JSONArray()
        TaskWidgetPageHistory.normalize(paths).forEach { json.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CUSTOM_HISTORY_KEY, json.toString())
            .apply()
    }

    private fun normalizeHistoryPath(context: Context, path: String): String {
        val vaultPath = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("vault_path", "")
            .orEmpty()
        return VaultPathUtil.resolveTarget(vaultPath, path)?.trim().orEmpty().ifBlank { path.trim() }
    }

    private fun scopeKey(widgetId: Int): String = "$KEY_PREFIX${widgetId}$SCOPE_SUFFIX"
    private fun pathKey(widgetId: Int): String = "$KEY_PREFIX${widgetId}$PATH_SUFFIX"
}

internal object TaskWidgetPageHistory {
    fun remember(paths: List<String>, path: String): List<String> =
        normalize(listOf(path) + paths)

    fun normalize(paths: List<String>): List<String> =
        paths.map { it.trim() }
            .filter { it.isNotBlank() && it.endsWith(".md", ignoreCase = true) }
            .distinct()

    fun remove(paths: List<String>, path: String): List<String> =
        normalize(paths).filterNot { it == path }
}
