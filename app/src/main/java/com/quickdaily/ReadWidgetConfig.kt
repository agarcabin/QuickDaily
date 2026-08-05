package com.quickdaily

import android.content.Context
import com.quickdaily.util.DateUtil
import java.io.File

enum class ReadWidgetTarget(
    val key: String,
    val label: String,
) {
    TODAY("today", "今日日记"),
    CUSTOM("custom", "自定义页面");

    companion object {
        fun fromKey(value: String?): ReadWidgetTarget =
            entries.firstOrNull { it.key == value } ?: TODAY
    }
}

data class ReadWidgetConfig(
    val target: ReadWidgetTarget = ReadWidgetTarget.TODAY,
    val customRelativePath: String = "",
)

/** Per-widget target state for the diary/read widget. */
object ReadWidgetConfigStore {
    private const val PREFS = "QuickDaily"
    private const val KEY_PREFIX = "read_widget_"
    private const val TARGET_SUFFIX = "_target"
    private const val PATH_SUFFIX = "_path"

    fun load(context: Context, widgetId: Int): ReadWidgetConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rawTarget = prefs.getString(targetKey(widgetId), null)
        if (rawTarget == null) {
            val migrated = ReadWidgetConfig()
            save(context, widgetId, migrated)
            return migrated
        }
        val config = ReadWidgetConfig(
            target = ReadWidgetTarget.fromKey(rawTarget),
            customRelativePath = prefs.getString(pathKey(widgetId), "").orEmpty().trim(),
        )
        if (config.target == ReadWidgetTarget.CUSTOM && config.customRelativePath.isNotBlank()) {
            TaskWidgetConfigStore.recordCustomPage(context, config.customRelativePath)
        }
        return config
    }

    /** Read an existing instance without creating a default/migration entry. */
    internal fun peek(context: Context, widgetId: Int): ReadWidgetConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ReadWidgetConfig(
            target = ReadWidgetTarget.fromKey(prefs.getString(targetKey(widgetId), null)),
            customRelativePath = prefs.getString(pathKey(widgetId), "").orEmpty().trim(),
        )
    }

    fun save(context: Context, widgetId: Int, config: ReadWidgetConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(targetKey(widgetId), config.target.key)
            .putString(pathKey(widgetId), config.customRelativePath.trim())
            .apply()
        if (config.target == ReadWidgetTarget.CUSTOM && config.customRelativePath.isNotBlank()) {
            TaskWidgetConfigStore.recordCustomPage(context, config.customRelativePath)
        }
        BetaLogger.log(
            "ReadWidgetConfig",
            "save widgetId=$widgetId target=${config.target.key} path=${config.customRelativePath}",
        )
    }

    fun clear(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(targetKey(widgetId))
            .remove(pathKey(widgetId))
            .apply()
        BetaLogger.log("ReadWidgetConfig", "clear widgetId=$widgetId")
    }

    fun storageKeys(widgetId: Int): Pair<String, String> =
        targetKey(widgetId) to pathKey(widgetId)

    fun displayName(config: ReadWidgetConfig): String =
        if (config.target == ReadWidgetTarget.TODAY) config.target.label
        else TaskWidgetConfigStore.displayName(config.customRelativePath).ifBlank { config.target.label }

    fun customFilePath(context: Context, config: ReadWidgetConfig): String? {
        if (config.target != ReadWidgetTarget.CUSTOM || config.customRelativePath.isBlank()) return null
        return TaskWidgetConfigStore.customFilePath(
            context,
            TaskWidgetConfig(TaskWidgetScope.CUSTOM, config.customRelativePath),
        )
    }

    fun targetFilePath(context: Context, config: ReadWidgetConfig): String? {
        if (config.target == ReadWidgetTarget.CUSTOM) return customFilePath(context, config)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val vault = prefs.getString("vault_path", "").orEmpty().trim()
        if (vault.isBlank()) return null
        val diaryFolder = prefs.getString("diary_folder", "Daily").orEmpty().trim().ifBlank { "Daily" }
        val dateFormat = prefs.getString("date_format", "YYYY-MM-DD").orEmpty().ifBlank { "YYYY-MM-DD" }
        return "${vault.trimEnd('/')}/${diaryFolder.trimEnd('/')}/${DateUtil.todayStr(dateFormat)}.md"
    }

    fun recentCustomPaths(context: Context): List<String> =
        TaskWidgetConfigStore.recentCustomPaths(context)

    fun removeCustomPage(context: Context, path: String) =
        TaskWidgetConfigStore.removeCustomPage(context, path)

    fun filePathFromUri(context: Context, uri: android.net.Uri): String? =
        TaskWidgetConfigStore.filePathFromUri(context, uri)

    fun isCustomAvailable(context: Context, config: ReadWidgetConfig): Boolean =
        config.target == ReadWidgetTarget.CUSTOM &&
            customFilePath(context, config)?.let { File(it).isFile } == true

    private fun targetKey(widgetId: Int): String = "$KEY_PREFIX${widgetId}$TARGET_SUFFIX"
    private fun pathKey(widgetId: Int): String = "$KEY_PREFIX${widgetId}$PATH_SUFFIX"
}
