package com.quickdaily

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quickdaily.util.DateUtil
import com.quickdaily.util.Debounce
import com.quickdaily.util.FileUtil
import com.quickdaily.util.FileFingerprint
import com.quickdaily.util.ContentUtil
import com.quickdaily.util.ReadResult
import com.quickdaily.util.VaultPathUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiaryConfig(
    val vaultPath: String = "",
    val obsidianConfigUri: String = "",
    val useCustomObsidianConfigPath: Boolean = false,
    val diaryFolder: String = "Daily",
    val dateFormat: String = "YYYY-MM-DD",
    val templatePath: String = "",
    val anchorText: String = "## 今日速记",
    val timestampFormat: String = "list_time",
    val addAnchorIfMissing: Boolean = true,
    val timestampOrder: String = "above",
    val enterToSave: Boolean = true,
    val openObsidianAfterFloatingSave: Boolean = FloatingNoteObsidianLaunchPolicy.DEFAULT_ENABLED,
    val saveDraftOnFloatingClose: Boolean = FloatingNoteEntryPolicy.DEFAULT_SAVE_ON_CLOSE,
    val widgetImageUri: String = "",
    val autoCheckUpdate: Boolean = true,
    val filterFrontmatter: Boolean = true,
    val imageStoragePath: String = "",
    val imageNamingFormat: String = "timestamp_original",
    val imageLinkFormat: String = "described",
    val imageCustomNamingFormat: String = "yyyy-MM-dd_HHmmss_{filename}{ext}",
    val tagAutocomplete: Boolean = true,
    val wikilinkAutocomplete: Boolean = true,
    val systemSidebarSupport: Boolean = FloatingNoteEntryPolicy.DEFAULT_SYSTEM_SIDEBAR_SUPPORT,
    val homeEntryMode: String = HomeEntryMode.OVERLAY.key,
    val toolbarOrder: List<String> = EditorToolbarPolicy.defaultOrder.map { it.id },
    val toolbarVisible: Set<String> = EditorToolbarPolicy.defaultVisible,
    val loggingEnabled: Boolean = false,
    val taskPeriod: String = "today",
    val taskCompletionSoundMode: String = TaskCompletionSoundPolicy.DEFAULT_MODE.key,
    val taskCompletionTimestamp: Boolean = false,
    val taskCompletionTimestampFormat: String = TaskCompletionTimestampPolicy.DEFAULT_FORMAT,
    val taskShowCompleted: Boolean = TaskWidgetDisplayPolicy.DEFAULT_SHOW_COMPLETED,
    val taskShowFullContent: Boolean = TaskWidgetDisplayPolicy.DEFAULT_SHOW_FULL_CONTENT,
    val taskGroupByDate: Boolean = TaskWidgetDisplayPolicy.DEFAULT_GROUP_BY_DATE,
    val widgetStyle: String = WidgetAppearance.DEFAULT_STYLE,
    val widgetBackgroundColor: Long = SettingsSliderDefaults.DEFAULT_WIDGET_BACKGROUND_COLOR,
    val widgetOpacity: Int = WidgetAppearance.DEFAULT_OPACITY_PERCENT,
    val floatingOpacity: Int = FloatingNoteAppearance.DEFAULT_OPACITY_PERCENT,
)

enum class ObsidianConfigReadStatus {
    SUCCESS,
    UNAVAILABLE,
    INVALID_JSON,
}

data class ObsidianConfigReadResult(
    val status: ObsidianConfigReadStatus,
    val config: DiaryConfig? = null,
)

data class EditorReloadSnapshot(
    val generation: Long,
    val target: String,
    val absolutePath: String,
    val lastLoadedMtime: Long,
    val lastLoadedFingerprint: FileFingerprint? = null,
)

object EditorReloadPolicy {
    fun shouldStart(request: EditorReloadSnapshot, observedMtime: Long, observedFingerprint: FileFingerprint? = null): Boolean =
        fileChanged(request.lastLoadedMtime, observedMtime, request.lastLoadedFingerprint, observedFingerprint)

    fun canApply(
        request: EditorReloadSnapshot,
        current: EditorReloadSnapshot,
        observedMtime: Long,
        observedFingerprint: FileFingerprint? = null,
    ): Boolean =
        request.generation == current.generation &&
            request.target == current.target &&
            request.absolutePath == current.absolutePath &&
            fileChanged(current.lastLoadedMtime, observedMtime, current.lastLoadedFingerprint, observedFingerprint)
}

private fun fileChanged(
    lastLoadedMtime: Long,
    observedMtime: Long,
    lastLoadedFingerprint: FileFingerprint?,
    observedFingerprint: FileFingerprint?,
): Boolean = if (lastLoadedFingerprint != null && observedFingerprint != null) {
    !lastLoadedFingerprint.hasSameContentAs(observedFingerprint)
} else {
    observedMtime != lastLoadedMtime
}

internal data class EditorConflict(
    val absolutePath: String,
    val externalContent: String,
    val externalMtime: Long,
    val externalFingerprint: FileFingerprint? = null,
)

internal object EditorConflictPolicy {
    fun shouldPrompt(
        isDirty: Boolean,
        observedMtime: Long,
        lastLoadedMtime: Long,
        ignoredExternalMtime: Long,
        observedFingerprint: FileFingerprint? = null,
        lastLoadedFingerprint: FileFingerprint? = null,
        ignoredExternalFingerprint: FileFingerprint? = null,
    ): Boolean =
        isDirty &&
            fileChanged(lastLoadedMtime, observedMtime, lastLoadedFingerprint, observedFingerprint) &&
            if (ignoredExternalFingerprint != null && observedFingerprint != null) {
                !ignoredExternalFingerprint.hasSameContentAs(observedFingerprint)
            } else {
                observedMtime != ignoredExternalMtime
            }

    fun canClearDirty(writeSucceeded: Boolean, savedVersion: Long, currentVersion: Long): Boolean =
        writeSucceeded && savedVersion == currentVersion
}

private data class EditorSaveSnapshot(
    val path: String,
    val saveContent: String,
    val contentVersion: Long,
    val lastLoadedMtime: Long,
    val lastLoadedFingerprint: FileFingerprint?,
    val ignoredExternalMtime: Long,
    val ignoredExternalFingerprint: FileFingerprint?,
)

const val TASK_PERIOD_TODAY = "today"
const val TASK_PERIOD_WEEK = "week"
const val TASK_PERIOD_MONTH = "month"


/** Obsidian 应用配置（来自 .obsidian/app.json） */
data class ObsidianAppConfig(
    val attachmentFolderPath: String = "/",
    val useMarkdownLinks: Boolean = false
)

class AppState(application: Application) : AndroidViewModel(application) {

    private val app: Application = application

    private val appScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs: SharedPreferences =
        application.getSharedPreferences("QuickDaily", 0)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<DiaryConfig> = _config.asStateFlow()

    private val _diaryContent = MutableStateFlow("")
    val diaryContent: StateFlow<String> = _diaryContent.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _frontmatter = MutableStateFlow("")
    val frontmatter: StateFlow<String> = _frontmatter.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _editorConflict = MutableStateFlow<EditorConflict?>(null)
    internal val editorConflict: StateFlow<EditorConflict?> = _editorConflict.asStateFlow()

    private val _todayPath = MutableStateFlow("")
    val todayPath: StateFlow<String> = _todayPath.asStateFlow()

    private val _editorTargetRelativePath = MutableStateFlow("")
    val editorTargetRelativePath: StateFlow<String> = _editorTargetRelativePath.asStateFlow()

    private var autoSave: Debounce? = null

    private var _lastLoadedMtime: Long = 0L
    private var _lastLoadedFingerprint: FileFingerprint? = null
    private var ignoredExternalMtime: Long = 0L
    private var ignoredExternalFingerprint: FileFingerprint? = null
    private var contentVersion: Long = 0L
    private val loadLock = Any()
    private var loadGeneration = 0L

    // ── Undo/Redo ────────────────────────────────────────
    private val _undoStack = mutableListOf<String>()
    private val _redoStack = mutableListOf<String>()
    private var _lastUndoPushTime = 0L
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // ── Config ──────────────────────────────────────────

    private fun loadConfig(): DiaryConfig {
        // These effects were removed in 1.8.5. Drop their legacy flags without
        // disturbing any other persisted QuickDaily settings.
        prefs.edit()
            .remove("widget_background_blur")
            .remove("floating_background_blur")
            .apply()
        val obsidianConfigUri = prefs.getString("obsidian_config_uri", "") ?: ""
        return DiaryConfig(
            vaultPath = prefs.getString("vault_path", "") ?: "",
            obsidianConfigUri = obsidianConfigUri,
            useCustomObsidianConfigPath = if (prefs.contains("use_custom_obsidian_config_path")) {
                prefs.getBoolean("use_custom_obsidian_config_path", false)
            } else {
                // Migrate the 1.7.4 URI-only setting without disabling an existing choice.
                obsidianConfigUri.isNotBlank()
            },
            diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily",
            dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD",
            templatePath = prefs.getString("template_path", "") ?: "",
            anchorText = prefs.getString("anchor_text", "## 今日速记") ?: "## 今日速记",
            timestampFormat = prefs.getString("timestamp_format", "list_time") ?: "list_time",
            addAnchorIfMissing = prefs.getBoolean("add_anchor_if_missing", true),
            timestampOrder = prefs.getString("timestamp_order", "above") ?: "above",
            enterToSave = prefs.getBoolean("enter_to_save", true),
            openObsidianAfterFloatingSave = prefs.getBoolean(
                FloatingNoteObsidianLaunchPolicy.PREF_KEY,
                FloatingNoteObsidianLaunchPolicy.DEFAULT_ENABLED,
            ),
            saveDraftOnFloatingClose = FloatingNoteEntryPolicy.shouldSaveOnClose(app),
            widgetImageUri = prefs.getString("widget_image_uri", "") ?: "",
            autoCheckUpdate = prefs.getBoolean("auto_check_update", true),
            filterFrontmatter = prefs.getBoolean("filter_frontmatter", true),
            imageStoragePath = prefs.getString("image_storage_path", "") ?: "",
            imageNamingFormat = prefs.getString("image_naming_format", "timestamp_original") ?: "timestamp_original",
            imageLinkFormat = prefs.getString("image_link_format", "described") ?: "described",
            imageCustomNamingFormat = prefs.getString("image_custom_naming_format", "yyyy-MM-dd_HHmmss_{filename}{ext}") ?: "yyyy-MM-dd_HHmmss_{filename}{ext}",
            // Autocomplete remains a built-in feature; the settings switches were removed.
            tagAutocomplete = true,
            wikilinkAutocomplete = true,
            systemSidebarSupport = prefs.getBoolean(
                FloatingNoteEntryPolicy.PREF_SYSTEM_SIDEBAR_SUPPORT,
                FloatingNoteEntryPolicy.DEFAULT_SYSTEM_SIDEBAR_SUPPORT,
            ),
            homeEntryMode = HomeEntryMode.fromKey(prefs.getString("home_entry_mode", HomeEntryMode.OVERLAY.key)).key,
            toolbarOrder = if (prefs.contains(EditorToolbarPolicy.PREF_ORDER)) {
                EditorToolbarPolicy.migrateOrder(
                    prefs.getString(EditorToolbarPolicy.PREF_ORDER, null),
                    prefs.getInt(EditorToolbarPolicy.PREF_SCHEMA_VERSION, 0) < EditorToolbarPolicy.CURRENT_SCHEMA_VERSION,
                )
            } else {
                EditorToolbarPolicy.defaultOrder.map { it.id }
            },
            toolbarVisible = if (prefs.contains(EditorToolbarPolicy.PREF_VISIBLE)) {
                EditorToolbarPolicy.readVisible(
                    prefs.getString(EditorToolbarPolicy.PREF_VISIBLE, null),
                    prefs.getInt(EditorToolbarPolicy.PREF_SCHEMA_VERSION, 0),
                )
            } else {
                EditorToolbarPolicy.defaultVisible
            },
            loggingEnabled = prefs.getBoolean("logging_enabled", false),
            taskPeriod = prefs.getString("task_period", "today") ?: "today",
            taskCompletionSoundMode = TaskCompletionSoundPolicy.migrateMode(
                storedMode = prefs.getString(TaskCompletionSoundPolicy.PREF_MODE_KEY, null),
                legacyEnabled = if (prefs.contains(TaskCompletionSoundPolicy.LEGACY_PREF_KEY)) {
                    prefs.getBoolean(TaskCompletionSoundPolicy.LEGACY_PREF_KEY, true)
                } else {
                    null
                },
            ).key,
            taskCompletionTimestamp = prefs.getBoolean(TaskCompletionTimestampPolicy.PREF_KEY, TaskCompletionTimestampPolicy.DEFAULT_ENABLED),
            taskCompletionTimestampFormat = TaskCompletionTimestampPolicy.normalizeFormat(
                prefs.getString(
                    TaskCompletionTimestampPolicy.PREF_FORMAT_KEY,
                    TaskCompletionTimestampPolicy.DEFAULT_FORMAT,
                ),
            ),
            taskShowCompleted = prefs.getBoolean(
                TaskWidgetDisplayPolicy.SHOW_COMPLETED_PREF_KEY,
                TaskWidgetDisplayPolicy.DEFAULT_SHOW_COMPLETED,
            ),
            taskShowFullContent = prefs.getBoolean(
                TaskWidgetDisplayPolicy.SHOW_FULL_CONTENT_PREF_KEY,
                TaskWidgetDisplayPolicy.DEFAULT_SHOW_FULL_CONTENT,
            ),
            taskGroupByDate = prefs.getBoolean(
                TaskWidgetDisplayPolicy.GROUP_BY_DATE_PREF_KEY,
                TaskWidgetDisplayPolicy.DEFAULT_GROUP_BY_DATE,
            ),
            widgetStyle = WidgetAppearance.resolveStyle(prefs.getString("widget_style", null)),
            widgetBackgroundColor = prefs.getLong(
                "widget_background_color",
                SettingsSliderDefaults.DEFAULT_WIDGET_BACKGROUND_COLOR,
            ),
            widgetOpacity = prefs.getInt("widget_opacity", WidgetAppearance.DEFAULT_OPACITY_PERCENT).coerceIn(0, 100),
            floatingOpacity = prefs.getInt(FloatingNoteAppearance.PREF_OPACITY, FloatingNoteAppearance.DEFAULT_OPACITY_PERCENT).coerceIn(0, 100),
        )
    }

    fun saveConfig(raw: DiaryConfig) {
        val previousConfig = _config.value
        val config = DiaryConfig(
            vaultPath = raw.vaultPath.trim(),
            obsidianConfigUri = raw.obsidianConfigUri.trim(),
            useCustomObsidianConfigPath = raw.useCustomObsidianConfigPath,
            diaryFolder = raw.diaryFolder.trim().ifBlank { "Daily" },
            dateFormat = raw.dateFormat.trim().ifBlank { "YYYY-MM-DD" },
            templatePath = raw.templatePath.trim(),
        anchorText = raw.anchorText,
            timestampFormat = raw.timestampFormat,
            addAnchorIfMissing = raw.addAnchorIfMissing,
            timestampOrder = raw.timestampOrder,
            enterToSave = raw.enterToSave,
            openObsidianAfterFloatingSave = raw.openObsidianAfterFloatingSave,
            saveDraftOnFloatingClose = raw.saveDraftOnFloatingClose,
            widgetImageUri = raw.widgetImageUri,
            autoCheckUpdate = raw.autoCheckUpdate,
        filterFrontmatter = raw.filterFrontmatter,
            imageStoragePath = raw.imageStoragePath,
            imageNamingFormat = raw.imageNamingFormat,
            imageLinkFormat = raw.imageLinkFormat,
            imageCustomNamingFormat = raw.imageCustomNamingFormat,
            tagAutocomplete = true,
            wikilinkAutocomplete = true,
            systemSidebarSupport = raw.systemSidebarSupport,
            homeEntryMode = HomeEntryMode.fromKey(raw.homeEntryMode).key,
            toolbarOrder = EditorToolbarPolicy.normalizeOrder(raw.toolbarOrder),
            toolbarVisible = EditorToolbarPolicy.normalizeVisible(raw.toolbarVisible),
            loggingEnabled = raw.loggingEnabled,
            taskPeriod = raw.taskPeriod,
            taskCompletionSoundMode = TaskCompletionSoundMode.fromKey(raw.taskCompletionSoundMode).key,
            taskCompletionTimestamp = raw.taskCompletionTimestamp,
            taskCompletionTimestampFormat = TaskCompletionTimestampPolicy.normalizeFormat(
                raw.taskCompletionTimestampFormat,
            ),
            taskShowCompleted = raw.taskShowCompleted,
            taskShowFullContent = raw.taskShowFullContent,
            taskGroupByDate = raw.taskGroupByDate,
            widgetStyle = raw.widgetStyle,
            widgetBackgroundColor = raw.widgetBackgroundColor,
            floatingOpacity = raw.floatingOpacity.coerceIn(0, 100),
            widgetOpacity = raw.widgetOpacity.coerceIn(0, 100),
        )
        prefs.edit()
            .putString("vault_path", config.vaultPath)
            .putString("obsidian_config_uri", config.obsidianConfigUri)
            .putBoolean("use_custom_obsidian_config_path", config.useCustomObsidianConfigPath)
            .putString("diary_folder", config.diaryFolder)
            .putString("date_format", config.dateFormat)
            .putString("template_path", config.templatePath)
            .putString("anchor_text", config.anchorText)
            .putString("timestamp_format", config.timestampFormat)
            .putBoolean("add_anchor_if_missing", config.addAnchorIfMissing)
            .putString("timestamp_order", config.timestampOrder)
            .putBoolean(FloatingNoteEntryPolicy.PREF_SAVE_ON_CLOSE, config.saveDraftOnFloatingClose)
            .putBoolean("enter_to_save", config.enterToSave)
            .putBoolean(
                FloatingNoteObsidianLaunchPolicy.PREF_KEY,
                config.openObsidianAfterFloatingSave,
            )
            .putBoolean(FloatingNoteEntryPolicy.PREF_KEEP_DRAFT_ON_CLOSE, !config.saveDraftOnFloatingClose)
            .putInt(
                FloatingNoteEntryPolicy.PREF_SAVE_ON_CLOSE_SCHEMA_VERSION,
                FloatingNoteEntryPolicy.SAVE_ON_CLOSE_SCHEMA_VERSION,
            )
            .putString("widget_image_uri", config.widgetImageUri)
            .putBoolean("auto_check_update", config.autoCheckUpdate)
            .putBoolean("filter_frontmatter", config.filterFrontmatter)
            .putString("image_storage_path", config.imageStoragePath)
            .putString("image_naming_format", config.imageNamingFormat)
            .putString("image_link_format", config.imageLinkFormat)
            .putString("image_custom_naming_format", config.imageCustomNamingFormat)
            .putBoolean("tag_autocomplete", true)
            .putBoolean("wikilink_autocomplete", true)
            .putBoolean(FloatingNoteEntryPolicy.PREF_SYSTEM_SIDEBAR_SUPPORT, config.systemSidebarSupport)
            .putString("home_entry_mode", config.homeEntryMode)
            .putString(EditorToolbarPolicy.PREF_ORDER, EditorToolbarPolicy.serializeOrder(config.toolbarOrder))
            .putString(EditorToolbarPolicy.PREF_VISIBLE, EditorToolbarPolicy.serializeVisible(config.toolbarVisible))
            .putInt(EditorToolbarPolicy.PREF_SCHEMA_VERSION, EditorToolbarPolicy.CURRENT_SCHEMA_VERSION)
            .putBoolean("logging_enabled", config.loggingEnabled)
            .putString("task_period", config.taskPeriod)
            .putString(TaskCompletionSoundPolicy.PREF_MODE_KEY, config.taskCompletionSoundMode)
            .putBoolean(TaskCompletionTimestampPolicy.PREF_KEY, config.taskCompletionTimestamp)
            .putString(TaskCompletionTimestampPolicy.PREF_FORMAT_KEY, config.taskCompletionTimestampFormat)
            .putBoolean(TaskWidgetDisplayPolicy.SHOW_COMPLETED_PREF_KEY, config.taskShowCompleted)
            .putBoolean(TaskWidgetDisplayPolicy.SHOW_FULL_CONTENT_PREF_KEY, config.taskShowFullContent)
            .putBoolean(TaskWidgetDisplayPolicy.GROUP_BY_DATE_PREF_KEY, config.taskGroupByDate)
            .putInt(FloatingNoteAppearance.PREF_OPACITY, config.floatingOpacity.coerceIn(0, 100))
            .putString("widget_style", config.widgetStyle)
            .putLong("widget_background_color", config.widgetBackgroundColor)
            .putInt("widget_opacity", config.widgetOpacity.coerceIn(0, 100))
            .remove("widget_background_blur")
            .remove("floating_background_blur")
            // Config changes are initiated from Compose click handlers. `commit()` blocks
            // the main thread and was especially visible in Settings because every switch
            // also used to reload the current Markdown file below.
            .apply()
        _config.value = config
        BetaLogger.log(
            "Config/Save",
            "vaultPath=${config.vaultPath} diaryFolder=${config.diaryFolder} dateFormat=${config.dateFormat} " +
                "filterFrontmatter=${config.filterFrontmatter} loggingEnabled=${config.loggingEnabled} " +
                "taskCompletionSoundMode=${config.taskCompletionSoundMode} taskCompletionTimestamp=${config.taskCompletionTimestamp} " +
                "taskCompletionTimestampFormat=${config.taskCompletionTimestampFormat} " +
                "taskShowCompleted=${config.taskShowCompleted} taskShowFullContent=${config.taskShowFullContent} " +
                "systemSidebarSupport=${config.systemSidebarSupport}",
        )
        if (previousConfig.requiresEditorReloadComparedTo(config)) {
            loadEditorTarget(_editorTargetRelativePath.value)
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("logging_enabled", enabled).apply()
        _config.value = _config.value.copy(loggingEnabled = enabled)
    }

    private fun DiaryConfig.requiresEditorReloadComparedTo(other: DiaryConfig): Boolean =
        vaultPath != other.vaultPath ||
            diaryFolder != other.diaryFolder ||
            dateFormat != other.dateFormat ||
            templatePath != other.templatePath ||
            filterFrontmatter != other.filterFrontmatter

    suspend fun loadObsidianConfig(vaultPath: String): DiaryConfig? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val cleanPath = vaultPath.trimEnd('/')
            val jsonPath = "$cleanPath/.obsidian/daily-notes.json"
            val raw = FileUtil.readOrNull(jsonPath) ?: return@withContext null
            parseObsidianConfig(raw, vaultPath, "vault-default")
        }
    }

    suspend fun loadObsidianConfig(uri: Uri, vaultPath: String): DiaryConfig? {
        return inspectObsidianConfig(uri, vaultPath).config
    }

    suspend fun inspectObsidianConfig(uri: Uri, vaultPath: String): ObsidianConfigReadResult {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val raw = try {
                app.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            } catch (error: Exception) {
                BetaLogger.logException("ObsidianConfig", "read_uri_failed uri=$uri", error)
                null
            } ?: return@withContext ObsidianConfigReadResult(ObsidianConfigReadStatus.UNAVAILABLE)
            val parsed = parseObsidianConfig(raw, vaultPath, "custom-uri")
            if (parsed == null) {
                ObsidianConfigReadResult(ObsidianConfigReadStatus.INVALID_JSON)
            } else {
                ObsidianConfigReadResult(ObsidianConfigReadStatus.SUCCESS, parsed)
            }
        }
    }

    private fun parseObsidianConfig(raw: String, vaultPath: String, source: String): DiaryConfig? {
        return try {
            val json = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            val obj = json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject
                ?: return null

            fun field(key: String): String? =
                (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNullBlank()

            val folder = field("folder") ?: "Daily"
            // Keep the exact Moment/Obsidian format in persisted config. The
            // conversion to Java's DateTimeFormatter happens only when a
            // filename is generated, so casing from daily-notes.json remains
            // visible and round-trippable in settings.
            val format = field("format") ?: "YYYY-MM-DD"
            var template = field("template") ?: ""
            if (template.isNotBlank() && !template.endsWith(".md", ignoreCase = true)) {
                template += ".md"
            }
            BetaLogger.log("ObsidianConfig", "parsed source=$source path=$vaultPath bytes=${raw.length}")
            DiaryConfig(vaultPath = vaultPath, diaryFolder = folder, dateFormat = format, templatePath = template)
        } catch (error: Exception) {
            BetaLogger.logException("ObsidianConfig", "parse_failed source=$source bytes=${raw.length}", error)
            null
        }
    }

    suspend fun loadObsidianAppConfig(vaultPath: String): ObsidianAppConfig? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val cleanPath = vaultPath.trimEnd('/')
            val jsonPath = "$cleanPath/.obsidian/app.json"
            val raw = FileUtil.readOrNull(jsonPath) ?: return@withContext null
            try {
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
               val obj = json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject ?: return@withContext null
                val folder = (obj["attachmentFolderPath"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "/"
                val useMarkdown = (obj["useMarkdownLinks"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
                ObsidianAppConfig(attachmentFolderPath = folder, useMarkdownLinks = useMarkdown)
            } catch (_: Exception) {
                null
            }
        }
    }

// ── Diary ───────────────────────────────────────────

    fun todayFilePath(): String {
        val cfg = _config.value
        val date = DateUtil.todayStr(cfg.dateFormat)
        val base = cfg.vaultPath.trimEnd('/')
        return "$base/${cfg.diaryFolder.trimEnd('/')}/${date}.md"
    }

    fun loadToday() = loadEditorTarget("")

    /** Load either today's diary or an absolute/vault-relative Markdown file. */
    fun loadEditorTarget(relativePath: String?) {
        val normalized = relativePath.orEmpty().trim()
        val generation = synchronized(loadLock) {
            loadGeneration += 1L
            contentVersion += 1L
            _editorTargetRelativePath.value = normalized
            _isLoaded.value = false
            _isDirty.value = false
            _editorConflict.value = null
            ignoredExternalMtime = 0L
            ignoredExternalFingerprint = null
            loadGeneration
        }
        if (_config.value.vaultPath.isBlank() && normalized.isBlank()) {
            synchronized(loadLock) {
                if (generation == loadGeneration) {
                    _todayPath.value = ""
                    _lastLoadedMtime = 0L
                    _lastLoadedFingerprint = null
                    _diaryContent.value = ""
                    _frontmatter.value = ""
                    _isDirty.value = false
                    _isLoaded.value = true
                }
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val path = if (normalized.isBlank()) {
                todayFilePath()
            } else {
                VaultPathUtil.resolveTarget(_config.value.vaultPath, normalized).orEmpty()
            }
            val loadedMtime = FileUtil.lastModified(path)
            val isToday = normalized.isBlank()
            val logTag = if (isToday) "LoadToday" else "LoadEditorTarget"
            BetaLogger.log(logTag, "path=$path | mtime=$loadedMtime")

            val content = when (val result = FileUtil.readResult(path)) {
                is ReadResult.Success -> result.content
                is ReadResult.NotFound -> null
                is ReadResult.Error -> {
                    BetaLogger.logException(logTag, "read_error path=$path", result.exception)
                    null
                }
            }

            val rawContent: String
            var contentSource = "empty"
            if (content != null && content.isNotEmpty()) {
                val parsedContent = ContentUtil.parseFrontmatter(content)
                if (isToday && parsedContent.hasFrontmatter && parsedContent.body.isBlank() && config.value.templatePath.isNotBlank()) {
                    val tpl = loadTemplate()
                    rawContent = if (tpl.isNotEmpty()) tpl else content
                    contentSource = if (tpl.isNotEmpty()) "template(fm-file)" else "file(fm-only,no-tpl)"
                } else {
                    rawContent = content
                    contentSource = "file"
                }
            } else if (isToday) {
                rawContent = loadTemplate()
                contentSource = if (rawContent.isNotEmpty()) "template" else "empty"
            } else {
                rawContent = ""
            }
            BetaLogger.log(logTag, "rawContent from=$contentSource raw_len=${rawContent.length}")
            val loadedMtimeAfterRead = FileUtil.lastModified(path)
            val loadedFingerprint = FileUtil.fingerprint(path)

            val parsed = ContentUtil.parseFrontmatter(rawContent)
            val applied = synchronized(loadLock) {
                if (generation != loadGeneration) {
                    false
                } else {
                    _todayPath.value = path
                    _lastLoadedMtime = loadedMtimeAfterRead
                    _lastLoadedFingerprint = loadedFingerprint
                    ignoredExternalMtime = 0L
                    ignoredExternalFingerprint = null
                    _isDirty.value = false
                    _editorConflict.value = null
                    _frontmatter.value = parsed.frontmatter
                    _diaryContent.value = if (parsed.hasFrontmatter && config.value.filterFrontmatter) {
                        parsed.body
                    } else {
                        rawContent
                    }
                    _undoStack.clear()
                    _redoStack.clear()
                    _canUndo.value = false
                    _canRedo.value = false
                    _lastUndoPushTime = 0L
                    _isLoaded.value = true
                    autoSave?.cancel()
                    autoSave = Debounce(scope = viewModelScope, onFire = { saveNow() })
                    true
                }
            }
            if (!applied) {
                BetaLogger.log(logTag, "discarded_stale_load generation=$generation")
            }
        }
    }

    fun currentEditorRelativePath(): String {
        val target = _editorTargetRelativePath.value
        if (target.isNotBlank()) return target
        val cfg = _config.value
        return "${cfg.diaryFolder.trimEnd('/')}/${DateUtil.todayStr(cfg.dateFormat)}.md"
    }

    fun reloadIfNewerOnDisk() {
        if (_config.value.vaultPath.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val request = synchronized(loadLock) {
                _todayPath.value.takeIf { it.isNotEmpty() }?.let { path ->
                    EditorReloadSnapshot(
                        generation = loadGeneration,
                        target = _editorTargetRelativePath.value,
                        absolutePath = path,
                        lastLoadedMtime = _lastLoadedMtime,
                        lastLoadedFingerprint = _lastLoadedFingerprint,
                    )
                }
            }
            if (request == null) {
                val currentTarget = synchronized(loadLock) { _editorTargetRelativePath.value }
                val targetToReload = EditorReloadTargetPolicy.targetToReload(currentTarget)
                if (targetToReload.isBlank()) loadToday() else loadEditorTarget(targetToReload)
                return@launch
            }
            val mtime = FileUtil.lastModified(request.absolutePath)
            val observedFingerprint = FileUtil.fingerprint(request.absolutePath)
            BetaLogger.log("ReloadIfNewer", "old_mtime=${request.lastLoadedMtime} new_mtime=$mtime")
            if (!EditorReloadPolicy.shouldStart(request, mtime, observedFingerprint)) return@launch

            val existingConflict = synchronized(loadLock) { _editorConflict.value }
            if (existingConflict?.absolutePath == request.absolutePath &&
                if (existingConflict.externalFingerprint != null && observedFingerprint != null) {
                    existingConflict.externalFingerprint.hasSameContentAs(observedFingerprint)
                } else {
                    mtime <= existingConflict.externalMtime
                }
            ) {
                return@launch
            }

            val dirty = synchronized(loadLock) { _isDirty.value }
            val ignoredMtime = synchronized(loadLock) { ignoredExternalMtime }
            val ignoredFingerprint = synchronized(loadLock) { ignoredExternalFingerprint }
            if (EditorConflictPolicy.shouldPrompt(
                    isDirty = dirty,
                    observedMtime = mtime,
                    lastLoadedMtime = request.lastLoadedMtime,
                    ignoredExternalMtime = ignoredMtime,
                    observedFingerprint = observedFingerprint,
                    lastLoadedFingerprint = request.lastLoadedFingerprint,
                    ignoredExternalFingerprint = ignoredFingerprint,
                )
            ) {
                val conflict = readEditorConflict(request.absolutePath, mtime, observedFingerprint)
                if (conflict == null) return@launch
                val shown = synchronized(loadLock) {
                    val current = EditorReloadSnapshot(
                        generation = loadGeneration,
                        target = _editorTargetRelativePath.value,
                        absolutePath = _todayPath.value,
                        lastLoadedMtime = _lastLoadedMtime,
                        lastLoadedFingerprint = _lastLoadedFingerprint,
                    )
                    if (EditorReloadPolicy.canApply(request, current, mtime, observedFingerprint) &&
                        EditorConflictPolicy.shouldPrompt(
                            isDirty = _isDirty.value,
                            observedMtime = mtime,
                            lastLoadedMtime = _lastLoadedMtime,
                            ignoredExternalMtime = ignoredExternalMtime,
                            observedFingerprint = observedFingerprint,
                            lastLoadedFingerprint = _lastLoadedFingerprint,
                            ignoredExternalFingerprint = ignoredExternalFingerprint,
                        )
                    ) {
                        _editorConflict.value = conflict
                        true
                    } else {
                        false
                    }
                }
                if (shown) {
                    BetaLogger.log("ReloadIfNewer", "conflict_pending path=${request.absolutePath} mtime=$mtime")
                }
                return@launch
            }

            if (existingConflict != null) return@launch
            if (dirty && ignoredFingerprint != null && observedFingerprint != null &&
                ignoredFingerprint.hasSameContentAs(observedFingerprint)
            ) {
                BetaLogger.log("ReloadIfNewer", "skip_acknowledged_external path=${request.absolutePath}")
                return@launch
            }


            val content = when (val result = FileUtil.readResult(request.absolutePath)) {
                is ReadResult.Success -> result.content
                else -> {
                    BetaLogger.log("ReloadIfNewer", "read_failed path=${request.absolutePath}")
                    return@launch
                }
            }
            val reloadedFingerprint = FileUtil.fingerprint(request.absolutePath)
            val parsed = ContentUtil.parseFrontmatter(content)
            // If file has only frontmatter (empty body), try loading template.
            val effectiveContent = if (parsed.hasFrontmatter && parsed.body.isBlank()) {
                val tpl = loadTemplate()
                if (tpl.isNotEmpty()) tpl else content
            } else {
                content
            }
            val effectiveParsed = ContentUtil.parseFrontmatter(effectiveContent)
            val applied = synchronized(loadLock) {
                val current = EditorReloadSnapshot(
                    generation = loadGeneration,
                    target = _editorTargetRelativePath.value,
                    absolutePath = _todayPath.value,
                    lastLoadedMtime = _lastLoadedMtime,
                    lastLoadedFingerprint = _lastLoadedFingerprint,
                )
                if (!EditorReloadPolicy.canApply(request, current, mtime, reloadedFingerprint)) {
                    false
                } else {
                    _frontmatter.value = effectiveParsed.frontmatter
                    if (effectiveParsed.hasFrontmatter && _config.value.filterFrontmatter) {
                        _diaryContent.value = effectiveParsed.body
                    } else {
                        _diaryContent.value = effectiveContent
                    }
                    _lastLoadedMtime = mtime
                    _lastLoadedFingerprint = reloadedFingerprint
                    _isDirty.value = false
                    _editorConflict.value = null
                    ignoredExternalMtime = 0L
                    ignoredExternalFingerprint = null
                    true
                }
            }
            if (applied) {
                BetaLogger.log("ReloadIfNewer", "RELOADED | frontmatter_len=${effectiveParsed.frontmatter.length} body_len=${effectiveParsed.body.length} filtered=${_config.value.filterFrontmatter && effectiveParsed.hasFrontmatter}")
            } else {
                BetaLogger.log("ReloadIfNewer", "discarded_stale_reload generation=${request.generation} path=${request.absolutePath}")
            }
        }
    }

    private fun loadTemplate(): String {
        val cfg = _config.value
        if (cfg.templatePath.isBlank()) {
            BetaLogger.log("LoadTemplate", "templatePath empty in config")
            return ""
        }
        val tplPath = if (cfg.templatePath.startsWith("/")) {
            cfg.templatePath
        } else {
            "${cfg.vaultPath.trimEnd('/')}/${cfg.templatePath}"
        }
        val tplContent = FileUtil.readOrNull(tplPath) ?: ""
        if (tplContent.isEmpty()) {
            BetaLogger.log("LoadTemplate", "template file empty or not found path=" + tplPath)
        } else {
            BetaLogger.log("LoadTemplate", "loaded len=" + tplContent.length + " path=" + tplPath)
        }
        return tplContent
    }

    fun onContentChanged(newContent: String, forceUndoPoint: Boolean = false) {
        if (newContent == _diaryContent.value) return
        saveUndoPoint(forceUndoPoint)
        _diaryContent.value = newContent
        synchronized(loadLock) {
            contentVersion += 1L
            _isDirty.value = true
        }
        autoSave?.trigger()
        BetaLogger.log("Edit", "content_changed length=${newContent.length}")
    }

    fun useDiskConflict() {
        val conflict = synchronized(loadLock) { _editorConflict.value } ?: return
        val parsed = ContentUtil.parseFrontmatter(conflict.externalContent)
        synchronized(loadLock) {
            if (_editorConflict.value != conflict) return
            _frontmatter.value = parsed.frontmatter
            _diaryContent.value = if (parsed.hasFrontmatter && _config.value.filterFrontmatter) {
                parsed.body
            } else {
                conflict.externalContent
            }
            _lastLoadedMtime = conflict.externalMtime
            _lastLoadedFingerprint = conflict.externalFingerprint ?: FileUtil.fingerprint(conflict.absolutePath)
            _isDirty.value = false
            _editorConflict.value = null
            ignoredExternalMtime = 0L
            ignoredExternalFingerprint = null
            contentVersion += 1L
            _undoStack.clear()
            _redoStack.clear()
            _canUndo.value = false
            _canRedo.value = false
            _lastUndoPushTime = 0L
            autoSave?.cancel()
        }
        BetaLogger.log("ReloadIfNewer", "conflict_resolved resolution=external path=${conflict.absolutePath}")
    }

    fun keepLocalConflict() {
        val conflict = synchronized(loadLock) {
            val current = _editorConflict.value ?: return@synchronized null
            ignoredExternalMtime = maxOf(ignoredExternalMtime, current.externalMtime)
            ignoredExternalFingerprint = current.externalFingerprint
            _editorConflict.value = null
            current
        } ?: return
        BetaLogger.log("ReloadIfNewer", "conflict_resolved resolution=local path=${conflict.absolutePath}")
        saveNow()
    }

    private fun readEditorConflict(path: String, mtime: Long, fingerprint: FileFingerprint?): EditorConflict? =
        when (val result = FileUtil.readResult(path)) {
            is ReadResult.Success -> EditorConflict(path, result.content, mtime, fingerprint)
            else -> {
                BetaLogger.log("ReloadIfNewer", "conflict_read_failed path=$path")
                null
            }
        }

    // ── Undo/Redo helpers ──────────────────────────────────

    fun saveUndoPoint(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - _lastUndoPushTime < 1500) {
            _redoStack.clear()
            _canRedo.value = false
            return
        }
        _lastUndoPushTime = if (force) 0L else now
        _undoStack.add(_diaryContent.value)
        if (_undoStack.size > 50) _undoStack.removeAt(0)
        _redoStack.clear()
        _canUndo.value = _undoStack.isNotEmpty()
        _canRedo.value = false
    }

    fun undo() {
        if (_undoStack.isEmpty()) return
        val currentContent = _diaryContent.value
        _redoStack.add(currentContent)
        if (_redoStack.size > 50) _redoStack.removeAt(0)
        val prevContent = _undoStack.removeAt(_undoStack.lastIndex)
        _diaryContent.value = prevContent
        _canUndo.value = _undoStack.isNotEmpty()
        _canRedo.value = _redoStack.isNotEmpty()
        _lastUndoPushTime = 0L
        autoSave?.trigger()
        BetaLogger.log("Undo", "restored_len=${prevContent.length} remaining=${_undoStack.size}")
    }

    fun redo() {
        if (_redoStack.isEmpty()) return
        val currentContent = _diaryContent.value
        _undoStack.add(currentContent)
        if (_undoStack.size > 50) _undoStack.removeAt(0)
        val nextContent = _redoStack.removeAt(_redoStack.lastIndex)
        _diaryContent.value = nextContent
        _canUndo.value = _undoStack.isNotEmpty()
        _canRedo.value = _redoStack.isNotEmpty()
        _lastUndoPushTime = 0L
        autoSave?.trigger()
        BetaLogger.log("Redo", "restored_len=${nextContent.length} remaining=${_redoStack.size}")
    }

    fun scanTags() {
        val path = _config.value.vaultPath
        if (path.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = com.quickdaily.util.TagScanner.getTags(path)
            _tags.value = result
            BetaLogger.log("ScanTags", "found ${result.size} tags")
        }
    }

    fun refreshTags() {
        com.quickdaily.util.TagScanner.invalidateCache()
        scanTags()
    }

    fun saveNow(onComplete: (() -> Unit)? = null) {
        val snapshot = synchronized(loadLock) {
            val path = _todayPath.value
            val content = _diaryContent.value
            if (!_isDirty.value) {
                BetaLogger.log("SaveNow", "skip saving clean content path=$path")
                return@synchronized null
            }
            // don't create empty file if diary hasn't been loaded yet
            if (content.isEmpty() && path.isNotEmpty() && !java.io.File(path).exists()) {
                BetaLogger.log("SaveNow", "skip saving empty content for new file")
                return@synchronized null
            }
            if (path.isBlank() || _editorConflict.value != null) {
                if (_editorConflict.value != null) {
                    BetaLogger.log("SaveNow", "blocked_by_pending_conflict path=$path")
                }
                return@synchronized null
            }
            val hasFm = _frontmatter.value.isNotEmpty() && config.value.filterFrontmatter
            val saveContent = if (hasFm) {
                ContentUtil.reconstructWithFrontmatter(_frontmatter.value, content)
            } else {
                content
            }
            EditorSaveSnapshot(
                path = path,
                saveContent = saveContent,
                contentVersion = contentVersion,
                lastLoadedMtime = _lastLoadedMtime,
                lastLoadedFingerprint = _lastLoadedFingerprint,
                ignoredExternalMtime = ignoredExternalMtime,
                ignoredExternalFingerprint = ignoredExternalFingerprint,
            )
        } ?: run {
            onComplete?.invoke()
            return
        }

        BetaLogger.log(
            "SaveNow",
            "body_len=${snapshot.saveContent.length} path=${snapshot.path} " +
                "last_loaded_mtime=${snapshot.lastLoadedMtime} saving_len=${snapshot.saveContent.length}",
        )
        appScope.launch(Dispatchers.IO) {
            val mutationGuard = FileUtil.acquirePathMutation(snapshot.path)
            try {
            val snapshotStillCurrent = synchronized(loadLock) {
                _todayPath.value == snapshot.path &&
                    _isDirty.value &&
                    contentVersion == snapshot.contentVersion
            }
            if (!snapshotStillCurrent) {
                BetaLogger.log("SaveNow", "skip stale snapshot path=${snapshot.path}")
                return@launch
            }
            val observedMtime = FileUtil.lastModified(snapshot.path)
            val observedFingerprint = FileUtil.fingerprint(snapshot.path)
            if (observedFingerprint == null) {
                BetaLogger.log("SaveNow", "blocked_by_unavailable_fingerprint path=${snapshot.path}")
                return@launch
            }
            val conflictNeeded = synchronized(loadLock) {
                EditorConflictPolicy.shouldPrompt(
                    isDirty = _isDirty.value,
                    observedMtime = observedMtime,
                    lastLoadedMtime = snapshot.lastLoadedMtime,
                    ignoredExternalMtime = snapshot.ignoredExternalMtime,
                    observedFingerprint = observedFingerprint,
                    lastLoadedFingerprint = snapshot.lastLoadedFingerprint,
                    ignoredExternalFingerprint = snapshot.ignoredExternalFingerprint,
                )
            }
            if (conflictNeeded) {
                readEditorConflict(snapshot.path, observedMtime, observedFingerprint)?.let { conflict ->
                    synchronized(loadLock) {
                        if (_todayPath.value == snapshot.path && _isDirty.value) {
                            _editorConflict.value = conflict
                        }
                    }
                    BetaLogger.log("SaveNow", "blocked_by_external_update path=${snapshot.path}")
                }
                return@launch
            }

            val writeSucceeded = FileUtil.write(snapshot.path, snapshot.saveContent)
            val writtenFingerprint = if (writeSucceeded) FileUtil.fingerprint(snapshot.path) else null
            val writtenMtime = writtenFingerprint?.lastModified ?: if (writeSucceeded) FileUtil.lastModified(snapshot.path) else 0L
            synchronized(loadLock) {
                if (writeSucceeded && _todayPath.value == snapshot.path) {
                    _lastLoadedMtime = writtenMtime
                    _lastLoadedFingerprint = writtenFingerprint
                    ignoredExternalMtime = 0L
                    ignoredExternalFingerprint = null
                    if (EditorConflictPolicy.canClearDirty(
                            writeSucceeded = true,
                            savedVersion = snapshot.contentVersion,
                            currentVersion = contentVersion,
                        )
                    ) {
                        _isDirty.value = false
                    }
                }
            }
            if (writeSucceeded) {
                WidgetRefreshHelper.refreshAll(app)
                BetaLogger.log("SaveNow", "written_ok path=${snapshot.path}")
            } else {
                BetaLogger.log("SaveNow", "write_failed path=${snapshot.path}")
            }
            } finally {
                mutationGuard.close()
                onComplete?.let { callback -> viewModelScope.launch { callback() } }
            }
        }
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullBlank(): String? =
    if (this.isString) {
        content.takeIf { it.isNotBlank() }
    } else {
        content.takeIf { it.isNotBlank() }
    }
