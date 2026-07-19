package com.quickdaily

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quickdaily.util.DateUtil
import com.quickdaily.util.Debounce
import com.quickdaily.util.FileUtil
import com.quickdaily.util.ContentUtil
import com.quickdaily.util.ReadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiaryConfig(
    val vaultPath: String = "",
    val diaryFolder: String = "Daily",
    val dateFormat: String = "YYYY-MM-DD",
    val templatePath: String = "",
    val anchorText: String = "## 今日速记",
    val timestampFormat: String = "list_time",
    val addAnchorIfMissing: Boolean = true,
    val timestampOrder: String = "above",
    val enterToSave: Boolean = true,
    val widgetImageUri: String = "",
    val autoCheckUpdate: Boolean = true,
    val filterFrontmatter: Boolean = true,
    val imageStoragePath: String = "",
    val imageNamingFormat: String = "timestamp_original",
    val imageLinkFormat: String = "described",
    val imageCustomNamingFormat: String = "yyyy-MM-dd_HHmmss_{filename}{ext}",
    val tagAutocomplete: Boolean = true,
    val loggingEnabled: Boolean = false,
    val taskPeriod: String = "today",
    val widgetStyle: String = "dark",
    val widgetBackgroundColor: Long = 0xFF202124L,
    val widgetOpacity: Int = 100
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

    private val _todayPath = MutableStateFlow("")
    val todayPath: StateFlow<String> = _todayPath.asStateFlow()

    private var autoSave: Debounce? = null

    private var _lastLoadedMtime: Long = 0L

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
        return DiaryConfig(
            vaultPath = prefs.getString("vault_path", "") ?: "",
            diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily",
            dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD",
            templatePath = prefs.getString("template_path", "") ?: "",
            anchorText = prefs.getString("anchor_text", "## 今日速记") ?: "## 今日速记",
            timestampFormat = prefs.getString("timestamp_format", "list_time") ?: "list_time",
            addAnchorIfMissing = prefs.getBoolean("add_anchor_if_missing", true),
            timestampOrder = prefs.getString("timestamp_order", "above") ?: "above",
            enterToSave = prefs.getBoolean("enter_to_save", true),
            widgetImageUri = prefs.getString("widget_image_uri", "") ?: "",
            autoCheckUpdate = prefs.getBoolean("auto_check_update", true),
            filterFrontmatter = prefs.getBoolean("filter_frontmatter", true),
            imageStoragePath = prefs.getString("image_storage_path", "") ?: "",
            imageNamingFormat = prefs.getString("image_naming_format", "timestamp_original") ?: "timestamp_original",
            imageLinkFormat = prefs.getString("image_link_format", "described") ?: "described",
            imageCustomNamingFormat = prefs.getString("image_custom_naming_format", "yyyy-MM-dd_HHmmss_{filename}{ext}") ?: "yyyy-MM-dd_HHmmss_{filename}{ext}",
            tagAutocomplete = prefs.getBoolean("tag_autocomplete", true),
            loggingEnabled = prefs.getBoolean("logging_enabled", false),
            taskPeriod = prefs.getString("task_period", "today") ?: "today",
            widgetStyle = prefs.getString("widget_style", "dark") ?: "dark",
            widgetBackgroundColor = prefs.getLong("widget_background_color", 0xFF202124L),
            widgetOpacity = prefs.getInt("widget_opacity", 100).coerceIn(0, 100)
        )
    }

    fun saveConfig(raw: DiaryConfig) {
        val config = DiaryConfig(
            vaultPath = raw.vaultPath.trim(),
            diaryFolder = raw.diaryFolder.trim().ifBlank { "Daily" },
            dateFormat = raw.dateFormat.trim().ifBlank { "YYYY-MM-DD" },
            templatePath = raw.templatePath.trim(),
        anchorText = raw.anchorText,
            timestampFormat = raw.timestampFormat,
            addAnchorIfMissing = raw.addAnchorIfMissing,
            timestampOrder = raw.timestampOrder,
            enterToSave = raw.enterToSave,
            widgetImageUri = raw.widgetImageUri,
            autoCheckUpdate = raw.autoCheckUpdate,
        filterFrontmatter = raw.filterFrontmatter,
            imageStoragePath = raw.imageStoragePath,
            imageNamingFormat = raw.imageNamingFormat,
            imageLinkFormat = raw.imageLinkFormat,
            imageCustomNamingFormat = raw.imageCustomNamingFormat,
            tagAutocomplete = raw.tagAutocomplete,
            loggingEnabled = raw.loggingEnabled,
            taskPeriod = raw.taskPeriod,
            widgetStyle = raw.widgetStyle,
            widgetBackgroundColor = raw.widgetBackgroundColor,
            widgetOpacity = raw.widgetOpacity.coerceIn(0, 100)
        )
        prefs.edit()
            .putString("vault_path", config.vaultPath)
            .putString("diary_folder", config.diaryFolder)
            .putString("date_format", config.dateFormat)
            .putString("template_path", config.templatePath)
            .putString("anchor_text", config.anchorText)
            .putString("timestamp_format", config.timestampFormat)
            .putBoolean("add_anchor_if_missing", config.addAnchorIfMissing)
            .putString("timestamp_order", config.timestampOrder)
            .putBoolean("enter_to_save", config.enterToSave)
            .putString("widget_image_uri", config.widgetImageUri)
            .putBoolean("auto_check_update", config.autoCheckUpdate)
            .putBoolean("filter_frontmatter", config.filterFrontmatter)
            .putString("image_storage_path", config.imageStoragePath)
            .putString("image_naming_format", config.imageNamingFormat)
            .putString("image_link_format", config.imageLinkFormat)
            .putString("image_custom_naming_format", config.imageCustomNamingFormat)
            .putBoolean("tag_autocomplete", config.tagAutocomplete)
            .putBoolean("logging_enabled", config.loggingEnabled)
            .putString("task_period", config.taskPeriod)
            .putString("widget_style", config.widgetStyle)
            .putLong("widget_background_color", config.widgetBackgroundColor)
            .putInt("widget_opacity", config.widgetOpacity.coerceIn(0, 100))
            .commit()
        _config.value = config
        loadToday()
    }

    fun setLoggingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("logging_enabled", enabled).commit()
        _config.value = _config.value.copy(loggingEnabled = enabled)
    }

    suspend fun loadObsidianConfig(vaultPath: String): DiaryConfig? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val cleanPath = vaultPath.trimEnd('/')
            val jsonPath = "$cleanPath/.obsidian/daily-notes.json"
            val raw = FileUtil.readOrNull(jsonPath) ?: return@withContext null
            try {
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
                val obj = json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject
                    ?: return@withContext null

                fun field(key: String): String? =
                    (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNullBlank()

                val folder = field("folder") ?: "Daily"
                val format = DateUtil.convertObsidianFormat(field("format") ?: "YYYY-MM-DD")
                var template = field("template") ?: ""
                if (template.isNotBlank() && !template.endsWith(".md", ignoreCase = true)) {
                    template += ".md"
                }
                DiaryConfig(vaultPath, folder, format, template)
            } catch (_: Exception) {
                null
            }
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

    fun loadToday() {
        if (_config.value.vaultPath.isBlank()) {
            _isLoaded.value = true
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val path = todayFilePath()
            _todayPath.value = path
            _lastLoadedMtime = FileUtil.lastModified(path)
            BetaLogger.log("LoadToday", "path=$path | mtime=$_lastLoadedMtime")

            val content = when (val result = FileUtil.readResult(path)) {
                is ReadResult.Success -> result.content
                is ReadResult.NotFound -> null
                is ReadResult.Error -> {
                    android.util.Log.e("QuickDaily", "读取日记失败: $path", result.exception)
                    BetaLogger.log("LoadToday", "read_error: ${result.exception.message}")
                    null
                }
            }
            BetaLogger.log("LoadToday", "file_exists=" + (content != null) + " content_len=" + (content?.length ?: 0))
        // If file exists with only frontmatter (empty body), still try template
        val rawContent: String
        var contentSource = "template"
        if (content != null && content.isNotEmpty()) {
            val pc = ContentUtil.parseFrontmatter(content)
            if (pc.hasFrontmatter && pc.body.isBlank() && config.value.templatePath.isNotBlank()) {
                val tpl = loadTemplate()
                if (tpl.isNotEmpty()) {
                    rawContent = tpl
                    contentSource = "template(fm-file)"
                } else {
                    rawContent = content
                    contentSource = "file(fm-only,no-tpl)"
                }
            } else {
                rawContent = content
                contentSource = "file"
            }
        } else {
            rawContent = loadTemplate()  // loadTemplate() returns "" if no template set
            contentSource = if (rawContent.isNotEmpty()) "template" else "empty"
        }
        BetaLogger.log("LoadToday", "rawContent from=" + contentSource + " raw_len=" + rawContent.length)
            val parsed = ContentUtil.parseFrontmatter(rawContent)
            _frontmatter.value = parsed.frontmatter
            if (parsed.hasFrontmatter && config.value.filterFrontmatter) {
                _diaryContent.value = parsed.body
            } else {
                _diaryContent.value = rawContent
            }
            BetaLogger.log("LoadToday", "frontmatter_len=${parsed.frontmatter.length} body_len=${parsed.body.length} has_fm=${parsed.hasFrontmatter} filtering=${config.value.filterFrontmatter}")
            scanTags()

            _isLoaded.value = true
            autoSave?.cancel()
            autoSave = Debounce(scope = viewModelScope, onFire = { saveNow() })
        }
    }

    fun reloadIfNewerOnDisk() {
        if (_config.value.vaultPath.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val path = _todayPath.value
            if (path.isEmpty()) {
                loadToday()
                return@launch
            }
            val oldMtime = _lastLoadedMtime
            val mtime = FileUtil.lastModified(path)
            BetaLogger.log("ReloadIfNewer", "old_mtime=$oldMtime new_mtime=$mtime")
            if (mtime > _lastLoadedMtime) {
                _lastLoadedMtime = mtime
                val content = when (val result = FileUtil.readResult(path)) {
                    is ReadResult.Success -> result.content
                    else -> { BetaLogger.log("ReloadIfNewer", "read_failed"); return@launch }
                }
                val parsed = ContentUtil.parseFrontmatter(content)
                _frontmatter.value = parsed.frontmatter
                // If file has only frontmatter (empty body), try loading template
                val effectiveContent = if (parsed.hasFrontmatter && parsed.body.isBlank()) {
                    val tpl = loadTemplate()
                    if (tpl.isNotEmpty()) tpl else content
                } else {
                    content
                }
                val effectiveParsed = ContentUtil.parseFrontmatter(effectiveContent)
                _frontmatter.value = effectiveParsed.frontmatter
                if (effectiveParsed.hasFrontmatter && _config.value.filterFrontmatter) {
                    _diaryContent.value = effectiveParsed.body
                } else {
                    _diaryContent.value = effectiveContent
                }
                BetaLogger.log("ReloadIfNewer", "RELOADED | frontmatter_len=${parsed.frontmatter.length} body_len=${parsed.body.length} filtered=${_config.value.filterFrontmatter && parsed.hasFrontmatter}")
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

    fun onContentChanged(newContent: String) {
        saveUndoPoint()
        _diaryContent.value = newContent
        autoSave?.trigger()
        BetaLogger.log("Edit", "content_len=${newContent.length}")
    }

    // ── Undo/Redo helpers ──────────────────────────────────

    fun saveUndoPoint() {
        val now = System.currentTimeMillis()
        if (now - _lastUndoPushTime < 1500) return
        _lastUndoPushTime = now
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

    fun saveNow() {
        val path = _todayPath.value
        val content = _diaryContent.value
        // don't create empty file if diary hasn't been loaded yet
        if (content.isEmpty() && path.isNotEmpty() && !java.io.File(path).exists()) {
            BetaLogger.log("SaveNow", "skip saving empty content for new file")
            return
        }
        val hasFm = _frontmatter.value.isNotEmpty() && config.value.filterFrontmatter
        val saveContent = if (hasFm) {
            ContentUtil.reconstructWithFrontmatter(_frontmatter.value, content)
        } else {
            content
        }
        BetaLogger.log("SaveNow", "body_len=${content.length} frontmatter_len=${_frontmatter.value.length} has_fm=$hasFm saving_len=${saveContent.length}")
        if (path.isNotEmpty()) {
            appScope.launch(Dispatchers.IO) {
                FileUtil.write(path, saveContent)
                WidgetRefreshHelper.refreshAll(app)
                BetaLogger.log("SaveNow", "written_ok")
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
