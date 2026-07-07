package com.quickdaily

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quickdaily.util.DateUtil
import com.quickdaily.util.Debounce
import com.quickdaily.util.FileUtil
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
    val autoCheckUpdate: Boolean = true
)

class AppState(application: Application) : AndroidViewModel(application) {

    private val app: Application = application

    /**
     * 应用级协程作用域 — 用于必须在 Activity 销毁后仍需完成的 IO 操作（如 saveNow）。
     * 使用 SupervisorJob：单个子协程失败不会取消其他子协程。
     * viewModelScope 在 Activity finish 后会被取消，导致写入中断；改用此作用域避免数据丢失。
     */
    private val appScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs: SharedPreferences =
        application.getSharedPreferences("QuickDaily", 0)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<DiaryConfig> = _config.asStateFlow()

    private val _diaryContent = MutableStateFlow("")
    val diaryContent: StateFlow<String> = _diaryContent.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _todayPath = MutableStateFlow("")
    val todayPath: StateFlow<String> = _todayPath.asStateFlow()

    private var autoSave: Debounce? = null

    /** 上次从磁盘加载文件时的 mtime，用于 onResume 时判断是否需要重读 */
    private var _lastLoadedMtime: Long = 0L

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
            autoCheckUpdate = prefs.getBoolean("auto_check_update", true)
        )
    }

    fun saveConfig(raw: DiaryConfig) {
        // trim + 空白字段用默认值兜底
        val config = DiaryConfig(
            vaultPath = raw.vaultPath.trim(),
            diaryFolder = raw.diaryFolder.trim().ifBlank { "Daily" },
            dateFormat = raw.dateFormat.trim().ifBlank { "YYYY-MM-DD" },
            templatePath = raw.templatePath.trim(),
            anchorText = raw.anchorText.trim(),
            timestampFormat = raw.timestampFormat,
            addAnchorIfMissing = raw.addAnchorIfMissing,
            timestampOrder = raw.timestampOrder,
            enterToSave = raw.enterToSave,
            widgetImageUri = raw.widgetImageUri,
            autoCheckUpdate = raw.autoCheckUpdate
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
            .commit()  // 同步写入，防止进程被杀时配置丢失
        _config.value = config
        // 保存后重新加载日记，确保立即生效
        loadToday()
    }

    /** 从 Obsidian 的 .obsidian/daily-notes.json 读取配置（异步，避免主线程 IO 崩溃） */
    suspend fun loadObsidianConfig(vaultPath: String): DiaryConfig? {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val cleanPath = vaultPath.trimEnd('/')
            val jsonPath = "$cleanPath/.obsidian/daily-notes.json"
            val raw = FileUtil.readOrNull(jsonPath) ?: return@withContext null
            try {
                // 使用 kotlinx-serialization-json 的 JsonObject 解析，
                // 替代原正则方案——正则无法处理 JSON 转义字符（如路径中的反斜杠）
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
                // 模板路径没有 .md 后缀时自动补上
                if (template.isNotBlank() && !template.endsWith(".md", ignoreCase = true)) {
                    template += ".md"
                }
                DiaryConfig(vaultPath, folder, format, template)
            } catch (_: Exception) {
                null
            }
        }
    }

    // ── Diary ───────────────────────────────────────────

    /** 根据配置拼出今天的日记完整路径 */
    fun todayFilePath(): String {
        val cfg = _config.value
        // 始终转换 Obsidian 格式 → Java 格式，避免 DD=day-of-year 问题
        val date = DateUtil.todayStr(cfg.dateFormat)
        val base = cfg.vaultPath.trimEnd('/')
        return "$base/${cfg.diaryFolder.trimEnd('/')}/${date}.md"
    }

    /** 加载今天的日记 */
    fun loadToday() {
        // 仓库路径未设置时直接跳过
        if (_config.value.vaultPath.isBlank()) {
            _isLoaded.value = true
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val path = todayFilePath()
            _todayPath.value = path
            _lastLoadedMtime = FileUtil.lastModified(path)

            val content = when (val result = FileUtil.readResult(path)) {
                is ReadResult.Success -> result.content
                is ReadResult.NotFound -> {
                    // 不立即创建空文件——Obsidian 不会自动创建空日记，
                    // 此处仅加载模板内容到内存，真正写入推迟到用户首次编辑后的防抖保存
                    null
                }
                is ReadResult.Error -> {
                    android.util.Log.e("QuickDaily", "读取日记失败: $path", result.exception)
                    null
                }
            }
            _diaryContent.value = if (content != null && content.isNotEmpty()) content else loadTemplate()

            _isLoaded.value = true

            // 取消旧的防抖再创建新的
            autoSave?.cancel()
            autoSave = Debounce(scope = viewModelScope, onFire = { saveNow() })
        }
    }

    /**
     * 仅当磁盘上的日记文件比上次加载时更新（例如 Obsidian 同步覆盖、外部修改）才重新加载。
     * 避免每次 onResume 都盲目重读，覆盖掉用户尚未保存到磁盘的编辑。
     */
    fun reloadIfNewerOnDisk() {
        if (_config.value.vaultPath.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val path = _todayPath.value
            if (path.isEmpty()) {
                loadToday()
                return@launch
            }
            val mtime = FileUtil.lastModified(path)
            if (mtime > _lastLoadedMtime) {
                _lastLoadedMtime = mtime
                val content = when (val result = FileUtil.readResult(path)) {
                    is ReadResult.Success -> result.content
                    else -> return@launch
                }
                _diaryContent.value =
                    if (content.isNotEmpty()) content else loadTemplate()
            }
        }
    }

    private fun loadTemplate(): String {
        val cfg = _config.value
        if (cfg.templatePath.isBlank()) return ""
        val tplPath = if (cfg.templatePath.startsWith("/")) {
            cfg.templatePath
        } else {
            "${cfg.vaultPath.trimEnd('/')}/${cfg.templatePath}"
        }
        // 用 readOrNull 区分"模板不存在"与"读取失败"，避免吞异常返回空串
        return FileUtil.readOrNull(tplPath) ?: ""
    }

    /** 编辑时调用：更新内容 + 触发防抖保存 */
    fun onContentChanged(newContent: String) {
        _diaryContent.value = newContent
        autoSave?.trigger()
    }

    /**
     * 立即保存（切后台/离开 Activity 时调用）。
     *
     * 关键修复：使用 [appScope] 而非 viewModelScope。
     * MainActivity.onUserLeaveHint 会在 saveNow 后立即 finishAffinity，
     * viewModelScope 随之取消，可能导致写入中途被切断、文件损坏。
     * appScope 生命周期与 Application 绑定，可保证协程完整执行后再进程退出。
     */
    fun saveNow() {
        val path = _todayPath.value
        val content = _diaryContent.value
        if (path.isNotEmpty()) {
            appScope.launch(Dispatchers.IO) {
                FileUtil.write(path, content)
                QuickDailyWidget.updateAllWidgets(app)
            }
        }
    }
}

/** 把 JsonPrimitive 转为 String，空白视为 null（便于默认值兜底） */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullBlank(): String? =
    if (this.isString) {
        content.takeIf { it.isNotBlank() }
    } else {
        content.takeIf { it.isNotBlank() }
    }
