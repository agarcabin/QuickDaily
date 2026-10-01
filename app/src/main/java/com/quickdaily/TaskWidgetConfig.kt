package com.quickdaily

import android.content.Context
import android.net.Uri
import com.quickdaily.util.UriUtil
import com.quickdaily.util.SafDocumentPath
import com.quickdaily.util.SafVirtualPath
import com.quickdaily.util.VaultPathUtil
import com.quickdaily.util.VaultStoragePrefs
import org.json.JSONArray
import java.io.File

enum class TaskWidgetScope(
    val key: String,
    val label: String,
    val labelRes: Int,
) {
    TODAY("today", "今日任务", R.string.qd_widget_scope_today),
    WEEK("week", "本周任务", R.string.qd_widget_scope_week),
    MONTH("month", "本月任务", R.string.qd_widget_scope_month),
    CUSTOM("custom", "自定义页面任务", R.string.qd_widget_scope_custom),
    CUSTOM_FOLDER("custom_folder", "自定义文件夹任务", R.string.qd_widget_scope_custom_folder);

    companion object {
        fun fromKey(value: String?): TaskWidgetScope =
            entries.firstOrNull { it.key == value } ?: TODAY
    }
}

enum class TaskWidgetFolderTimeWindow(
    val key: String,
    val label: String,
    val labelRes: Int,
    val rollingDays: Int?,
) {
    TODAY("today", "今日任务", R.string.qd_widget_scope_today, 1),
    WEEK("week", "本周任务", R.string.qd_widget_scope_week, 7),
    MONTH("month", "本月任务", R.string.qd_widget_scope_month, 30),
    ALL("all", "所有任务", R.string.qd_widget_window_all, null);

    companion object {
        fun fromKey(value: String?): TaskWidgetFolderTimeWindow =
            entries.firstOrNull { it.key == value } ?: MONTH
    }
}

data class TaskWidgetConfig(
    val scope: TaskWidgetScope = TaskWidgetScope.TODAY,
    val customRelativePath: String = "",
    val customFolderUri: String = "",
    val customFolderName: String = "",
    val customFolderGrantFlags: Int = 0,
    val folderTimeWindow: TaskWidgetFolderTimeWindow = TaskWidgetFolderTimeWindow.MONTH,
    val folderIncludeSubfolders: Boolean = false,
)

object TaskWidgetConfigStore {
    private const val PREFS = "QuickDaily"
    private const val KEY_PREFIX = "task_widget_"
    private const val SCOPE_SUFFIX = "_scope"
    private const val PATH_SUFFIX = "_path"
    private const val FOLDER_URI_SUFFIX = "_folder_uri"
    private const val FOLDER_NAME_SUFFIX = "_folder_name"
    private const val FOLDER_GRANT_FLAGS_SUFFIX = "_folder_grant_flags"
    private const val FOLDER_WINDOW_SUFFIX = "_folder_window"
    private const val FOLDER_RECURSIVE_SUFFIX = "_folder_recursive"
    private const val CUSTOM_HISTORY_KEY = "task_widget_custom_pages"

    fun load(context: Context, widgetId: Int): TaskWidgetConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedScope = prefs.getString(scopeKey(widgetId), null)
        val storedPath = prefs.getString(pathKey(widgetId), "").orEmpty()
        if (storedScope != null) {
            val config = TaskWidgetConfig(
                scope = TaskWidgetScope.fromKey(storedScope),
                customRelativePath = storedPath,
                customFolderUri = prefs.getString(folderUriKey(widgetId), "").orEmpty(),
                customFolderName = prefs.getString(folderNameKey(widgetId), "").orEmpty(),
                customFolderGrantFlags = prefs.getInt(folderGrantFlagsKey(widgetId), 0),
                folderTimeWindow = TaskWidgetFolderTimeWindow.fromKey(
                    prefs.getString(folderWindowKey(widgetId), TaskWidgetFolderTimeWindow.MONTH.key),
                ),
                folderIncludeSubfolders = prefs.getBoolean(folderRecursiveKey(widgetId), false),
            )
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

    fun save(context: Context, widgetId: Int, config: TaskWidgetConfig): Boolean {
        val committed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(scopeKey(widgetId), config.scope.key)
            .putString(pathKey(widgetId), config.customRelativePath.trim())
            .putString(folderUriKey(widgetId), config.customFolderUri.trim())
            .putString(folderNameKey(widgetId), config.customFolderName.trim())
            .putInt(folderGrantFlagsKey(widgetId), config.customFolderGrantFlags)
            .putString(folderWindowKey(widgetId), config.folderTimeWindow.key)
            .putBoolean(folderRecursiveKey(widgetId), config.folderIncludeSubfolders)
            .commit()
        if (config.scope == TaskWidgetScope.CUSTOM && config.customRelativePath.isNotBlank()) {
            recordCustomPage(context, config.customRelativePath)
        }
        BetaLogger.log(
            "TaskWidgetConfig",
            "save widgetId=$widgetId scope=${config.scope.key} path=${config.customRelativePath} applied=$committed",
        )
        return committed
    }

    fun clear(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(scopeKey(widgetId))
            .remove(pathKey(widgetId))
            .remove(folderUriKey(widgetId))
            .remove(folderNameKey(widgetId))
            .remove(folderGrantFlagsKey(widgetId))
            .remove(folderWindowKey(widgetId))
            .remove(folderRecursiveKey(widgetId))
            .apply()
        BetaLogger.log("TaskWidgetConfig", "clear widgetId=$widgetId")
    }

    /** Read an existing instance without creating a default/migration entry. */
    internal fun peek(context: Context, widgetId: Int): TaskWidgetConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedScope = prefs.getString(scopeKey(widgetId), null)
        val scope = TaskWidgetScope.fromKey(storedScope ?: prefs.getString("task_period", "today"))
        return TaskWidgetConfig(
            scope = scope,
            customRelativePath = prefs.getString(pathKey(widgetId), "").orEmpty().trim(),
            customFolderUri = prefs.getString(folderUriKey(widgetId), "").orEmpty().trim(),
            customFolderName = prefs.getString(folderNameKey(widgetId), "").orEmpty().trim(),
            customFolderGrantFlags = prefs.getInt(folderGrantFlagsKey(widgetId), 0),
            folderTimeWindow = TaskWidgetFolderTimeWindow.fromKey(
                prefs.getString(folderWindowKey(widgetId), TaskWidgetFolderTimeWindow.MONTH.key),
            ),
            folderIncludeSubfolders = prefs.getBoolean(folderRecursiveKey(widgetId), false),
        )
    }

    fun customFilePath(context: Context, config: TaskWidgetConfig): String? {
        if (config.scope != TaskWidgetScope.CUSTOM || config.customRelativePath.isBlank()) return null
        if (!isMarkdownPath(config.customRelativePath)) return null
        val vaultPath = VaultStoragePrefs.current(context).rootPath
        val resolved = VaultPathUtil.resolveTarget(vaultPath, config.customRelativePath)
        BetaLogger.log(
            "TaskWidgetConfig",
            "resolve custom scope=${config.scope.key} relative=${config.customRelativePath} vault=$vaultPath resolved=${resolved.orEmpty()}",
        )
        return resolved
    }

    /** Return the selected absolute filesystem path; it may be outside the vault. */
    fun filePathFromUri(context: Context, uri: Uri): String? {
        val vaultPath = VaultStoragePrefs.current(context).rootPath
        val virtualPath = VaultStoragePrefs.virtualPathForDocumentUri(context, vaultPath, uri)
        if (virtualPath != null) {
            if (!isMarkdownPath(virtualPath)) {
                BetaLogger.log("PageSelection/Picker", "uri=$uri result=not_markdown virtualPath=$virtualPath")
                return null
            }
            BetaLogger.log("PageSelection/Picker", "uri=$uri result=$virtualPath storage=saf")
            return virtualPath
        }
        if (SafVirtualPath.parse(vaultPath) != null) {
            BetaLogger.log("PageSelection/Picker", "uri=$uri result=outside_saf_vault")
            return null
        }
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
        when (config.scope) {
            TaskWidgetScope.CUSTOM_FOLDER -> config.customFolderName.ifBlank { "自定义文件夹" }
            else -> displayName(config.customRelativePath)
        }

    fun displayName(context: Context, config: TaskWidgetConfig): String =
        when (config.scope) {
            TaskWidgetScope.CUSTOM_FOLDER -> config.customFolderName.ifBlank {
                LocaleController.localizedContext(context).getString(R.string.qd_task_custom_folder_title)
            }
            else -> displayName(config.customRelativePath)
        }

    fun displayName(path: String): String {
        val leaf = SafDocumentPath.leafName(path)
            ?: SafVirtualPath.parse(path)?.relativePath?.substringAfterLast('/')
            ?: File(path.trim()).name
        return if (leaf.endsWith(".md", ignoreCase = true)) leaf.dropLast(3) else leaf
    }

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
        val vaultPath = VaultStoragePrefs.current(context).rootPath
        return VaultPathUtil.resolveTarget(vaultPath, path)?.trim().orEmpty().ifBlank { path.trim() }
    }

    private fun scopeKey(widgetId: Int): String = "$KEY_PREFIX${widgetId}$SCOPE_SUFFIX"
    private fun pathKey(widgetId: Int): String = "$KEY_PREFIX${widgetId}$PATH_SUFFIX"
    private fun folderUriKey(widgetId: Int): String = "$KEY_PREFIX${widgetId}$FOLDER_URI_SUFFIX"
    private fun folderNameKey(widgetId: Int): String = "$KEY_PREFIX${widgetId}$FOLDER_NAME_SUFFIX"
    private fun folderGrantFlagsKey(widgetId: Int): String = "$KEY_PREFIX${widgetId}$FOLDER_GRANT_FLAGS_SUFFIX"
    private fun folderWindowKey(widgetId: Int): String = "$KEY_PREFIX${widgetId}$FOLDER_WINDOW_SUFFIX"
    private fun folderRecursiveKey(widgetId: Int): String = "$KEY_PREFIX${widgetId}$FOLDER_RECURSIVE_SUFFIX"
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
