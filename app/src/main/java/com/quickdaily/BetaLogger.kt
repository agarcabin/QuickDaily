package com.quickdaily

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BetaLogger {
    private const val HEADER_MARKER = "===== QuickDaily Beta Debug Log ====="
    @Volatile private var logFile: File? = null
    @Volatile private var enabled = false
    private var headerWritten = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    fun init(context: Context, source: String = "init") {
        try {
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            if (prefs.getBoolean("logging_enabled", false)) {
                configure(context, enabled = true, useExternal = true)
                logConfigSnapshot(context, source)
            } else {
                disable()
            }
        } catch (_: Exception) { enabled = false }
    }

    fun log(tag: String, message: String) {
        if (!enabled) return
        val time = dateFormat.format(Date())
        val line = "[$time] [$tag] $message"
        android.util.Log.d("QD-Beta", line)
        ioScope.launch {
            writeMutex.withLock {
                try {
                    if (!enabled) return@withLock
                    val file = logFile ?: return@withLock
                    if (!headerWritten) {
                        ensureDeviceHeader(file)
                        headerWritten = true
                    }
                    file.appendText("$line\n", Charsets.UTF_8)
                } catch (_: Exception) {}
            }
        }
    }

    fun logException(tag: String, message: String, error: Throwable) {
        log(tag, "$message\n${android.util.Log.getStackTraceString(error)}")
    }

    fun getLogContent(): String = if (!enabled) "" else try {
        logFile?.readText(Charsets.UTF_8) ?: ""
    } catch (_: Exception) { "" }

    fun configure(context: Context, enabled: Boolean, useExternal: Boolean) {
        try {
            if (!enabled) {
                disable()
                return
            }
            val file = if (useExternal) {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val docsDir = ExternalStoragePaths.diagnosticsDirectory()
                if (!docsDir.exists()) docsDir.mkdirs()
                val migration = ExternalStoragePaths.migrateLegacyLogs()
                File(docsDir, "QuickDaily_log_$date.txt").also {
                    logFile = it
                    headerWritten = hasCurrentDeviceHeader(it)
                    this.enabled = true
                    if (migration.moved > 0 || migration.skipped > 0) {
                        log("BetaLogger", "legacy logs migrated=${migration.moved} skipped=${migration.skipped}")
                    }
                }
            } else {
                File(context.filesDir, "beta_log.txt")
            }
            logFile = file
            headerWritten = hasCurrentDeviceHeader(file)
            this.enabled = true
            log("BetaLogger", "configured: external=$useExternal path=${file.absolutePath}")
            logConfigSnapshot(context, "configure")
        } catch (_: Exception) { }
    }

    fun logConfigSnapshot(context: Context, source: String) {
        if (!enabled) return
        val prefs = context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
        val customPages = TaskWidgetConfigStore.recentCustomPaths(context).joinToString("|")
        val featureStats = collectFeatureStats(context, prefs)
        log(
            "ConfigSnapshot",
            "source=$source loggingEnabled=${prefs.getBoolean("logging_enabled", false)} " +
                "vaultPath=${prefs.getString("vault_path", "").orEmpty()} " +
                "diaryFolder=${prefs.getString("diary_folder", "Daily").orEmpty()} " +
                "dateFormat=${prefs.getString("date_format", "YYYY-MM-DD").orEmpty()} " +
                "filterFrontmatter=${prefs.getBoolean("filter_frontmatter", false)} " +
                "renderMarkdown=${prefs.getBoolean("render_markdown", true)} " +
                "appVersion=${BuildConfig.VERSION_NAME} appVersionCode=${BuildConfig.VERSION_CODE} " +
                "taskCompletionTimestamp=${prefs.getBoolean(TaskCompletionTimestampPolicy.PREF_KEY, TaskCompletionTimestampPolicy.DEFAULT_ENABLED)} " +
                "taskCompletionSound=${prefs.getBoolean(TaskCompletionSoundPolicy.PREF_KEY, TaskCompletionSoundPolicy.DEFAULT_ENABLED)} " +
                "taskShowCompleted=${prefs.getBoolean(TaskWidgetDisplayPolicy.SHOW_COMPLETED_PREF_KEY, TaskWidgetDisplayPolicy.DEFAULT_SHOW_COMPLETED)} " +
                "systemSidebarSupport=${prefs.getBoolean(FloatingNoteEntryPolicy.PREF_SYSTEM_SIDEBAR_SUPPORT, FloatingNoteEntryPolicy.DEFAULT_SYSTEM_SIDEBAR_SUPPORT)} " +
                "homeEntryMode=${prefs.getString("home_entry_mode", HomeEntryMode.EDITOR.key).orEmpty()} " +
                "themeMonet=${prefs.getBoolean("theme_use_monet", true)} " +
                "themeAccent=${prefs.getString("theme_accent_preset", "blue").orEmpty()} " +
                "themeNightMode=${prefs.getString("theme_night_mode", "system").orEmpty()} " +
                "tagAutocomplete=${prefs.getBoolean("tag_autocomplete", true)} " +
                "wikilinkAutocomplete=${prefs.getBoolean("wikilink_autocomplete", true)} " +
                "customPages=$customPages " +
                "featureStats=" + LogFeatureStatsPolicy.encode(featureStats),
        )
    }

    private fun collectFeatureStats(
        context: Context,
        prefs: android.content.SharedPreferences,
    ): LogFeatureStats {
        val readIds = appWidgetIds(context, QuickDailyReadWidget::class.java)
        val readConfigs = readIds.map { ReadWidgetConfigStore.peek(context, it) }
        val taskIds = appWidgetIds(context, TaskWidget::class.java)
        val taskConfigs = taskIds.map { TaskWidgetConfigStore.peek(context, it) }
        val toolbarOrder = if (prefs.contains(EditorToolbarPolicy.PREF_ORDER)) {
            EditorToolbarPolicy.parseOrder(prefs.getString(EditorToolbarPolicy.PREF_ORDER, null))
        } else {
            EditorToolbarPolicy.defaultOrder.map { it.id }
        }
        val toolbarVisible = if (prefs.contains(EditorToolbarPolicy.PREF_VISIBLE)) {
            EditorToolbarPolicy.readVisible(
                prefs.getString(EditorToolbarPolicy.PREF_VISIBLE, null),
                prefs.getInt(EditorToolbarPolicy.PREF_SCHEMA_VERSION, 0),
            )
        } else {
            EditorToolbarPolicy.defaultVisible
        }
        val saveOnClose = FloatingNoteEntryPolicy.shouldSaveOnClose(context)
        return LogFeatureStats(
            readWidgetCount = readConfigs.size,
            readWidgetTodayCount = readConfigs.count { it.target == ReadWidgetTarget.TODAY },
            readWidgetCustomCount = readConfigs.count { it.target == ReadWidgetTarget.CUSTOM },
            taskWidgetCount = taskConfigs.size,
            taskWidgetTodayCount = taskConfigs.count { it.scope == TaskWidgetScope.TODAY },
            taskWidgetWeekCount = taskConfigs.count { it.scope == TaskWidgetScope.WEEK },
            taskWidgetMonthCount = taskConfigs.count { it.scope == TaskWidgetScope.MONTH },
            taskWidgetCustomCount = taskConfigs.count { it.scope == TaskWidgetScope.CUSTOM },
            customPageCount = TaskWidgetConfigStore.recentCustomPaths(context).size,
            floatingSaveOnClose = saveOnClose,
            floatingKeepDraftOnClose = !saveOnClose,
            floatingOpacityPercent = FloatingNoteAppearance.percent(context),
            toolbarVisibleCount = toolbarVisible.size,
            toolbarOrder = toolbarOrder,
        )
    }

    private fun <T> appWidgetIds(context: Context, provider: Class<T>): IntArray {
        return runCatching {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, provider))
        }.getOrDefault(IntArray(0))
    }

    private fun disable() {
        enabled = false
        logFile = null
        headerWritten = false
    }

    private fun deviceHeader(): String = buildString {
        appendLine(HEADER_MARKER)
        appendLine("app.package=${BuildConfig.APPLICATION_ID}")
        appendLine("app.versionName=${BuildConfig.VERSION_NAME}")
        appendLine("app.versionCode=${BuildConfig.VERSION_CODE}")
        appendLine("device.manufacturer=${Build.MANUFACTURER}")
        appendLine("device.brand=${Build.BRAND}")
        appendLine("device.model=${Build.MODEL}")
        appendLine("android.release=${Build.VERSION.RELEASE}")
        appendLine("android.sdk=${Build.VERSION.SDK_INT}")
        appendLine("android.incremental=${Build.VERSION.INCREMENTAL}")
        appendLine("android.display=${Build.DISPLAY}")
        appendLine("android.fingerprint=${Build.FINGERPRINT}")
        appendLine("=====================================")
    }

    private fun hasCurrentDeviceHeader(file: File): Boolean = try {
        if (!file.exists()) {
            false
        } else {
            val header = file.bufferedReader(Charsets.UTF_8).use { reader ->
                buildString {
                    repeat(12) {
                        val line = reader.readLine() ?: return@repeat
                        appendLine(line)
                    }
                }
            }
            header.contains(HEADER_MARKER) &&
                header.contains("app.versionName=${BuildConfig.VERSION_NAME}") &&
                header.contains("app.versionCode=${BuildConfig.VERSION_CODE}")
        }
    } catch (_: Exception) {
        false
    }

    private fun ensureDeviceHeader(file: File) {
        val existing = if (file.exists()) file.readText(Charsets.UTF_8) else ""
        val body = if (existing.startsWith(HEADER_MARKER)) {
            val separator = "=====================================\n"
            val separatorIndex = existing.indexOf(separator)
            if (separatorIndex >= 0) existing.substring(separatorIndex + separator.length) else ""
        } else {
            existing
        }
        file.writeText(deviceHeader() + body, Charsets.UTF_8)
    }

    fun clear() {
        try {
            logFile?.delete()
            headerWritten = false
        } catch (_: Exception) {}
    }

    fun shareLog(context: Context) {
        try {
            val content = getLogContent()
            if (content.isEmpty()) {
                android.widget.Toast.makeText(context, "日志为空", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            val cacheFile = java.io.File(context.cacheDir, "QuickDaily_log.txt")
            cacheFile.writeText(content)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", cacheFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享 Beta 日志"))
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, "分享失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
