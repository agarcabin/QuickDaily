package com.quickdaily

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quickdaily.util.DateUtil
import com.quickdaily.util.Debounce
import com.quickdaily.util.FileUtil
import com.quickdaily.util.ReadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiaryConfig(
    val vaultPath: String = "",
    val diaryFolder: String = "Daily",
    val dateFormat: String = "YYYY-MM-DD",
    val templatePath: String = "",
    val anchorText: String = ""
)

class AppState(application: Application) : AndroidViewModel(application) {

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

    // ── Config ──────────────────────────────────────────

    private fun loadConfig(): DiaryConfig {
        return DiaryConfig(
            vaultPath = prefs.getString("vault_path", "") ?: "",
            diaryFolder = prefs.getString("diary_folder", "Daily") ?: "Daily",
            dateFormat = prefs.getString("date_format", "YYYY-MM-DD") ?: "YYYY-MM-DD",
            templatePath = prefs.getString("template_path", "") ?: "",
            anchorText = prefs.getString("anchor_text", "") ?: ""
        )
    }

    fun saveConfig(raw: DiaryConfig) {
        // trim + 空白字段用默认值兜底
        val config = DiaryConfig(
            vaultPath = raw.vaultPath.trim(),
            diaryFolder = raw.diaryFolder.trim().ifBlank { "Daily" },
            dateFormat = raw.dateFormat.trim().ifBlank { "YYYY-MM-DD" },
            templatePath = raw.templatePath.trim(),
            anchorText = raw.anchorText.trim()
        )
        prefs.edit()
            .putString("vault_path", config.vaultPath)
            .putString("diary_folder", config.diaryFolder)
            .putString("date_format", config.dateFormat)
            .putString("template_path", config.templatePath)
            .putString("anchor_text", config.anchorText)
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
                val folder = extractJsonString(raw, "folder") ?: "Daily"
                val format = DateUtil.convertObsidianFormat(
                    extractJsonString(raw, "format") ?: "YYYY-MM-DD"
                )
                var template = extractJsonString(raw, "template") ?: ""
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

    private fun extractJsonString(raw: String, key: String): String? {
        val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return regex.find(raw)?.groupValues?.getOrNull(1)
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

            val content = when (val result = FileUtil.readResult(path)) {
                is ReadResult.Success -> result.content
                is ReadResult.NotFound -> {
                    FileUtil.write(path, "")
                    ""
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

    private fun loadTemplate(): String {
        val cfg = _config.value
        val tplPath = if (cfg.templatePath.startsWith("/")) {
            cfg.templatePath
        } else {
            "${cfg.vaultPath}/${cfg.templatePath}"
        }
        return FileUtil.read(tplPath)
    }

    /** 编辑时调用：更新内容 + 触发防抖保存 */
    fun onContentChanged(newContent: String) {
        _diaryContent.value = newContent
        autoSave?.trigger()
    }

    /** 切后台时调用：立即保存 */
    fun saveNow() {
        val path = _todayPath.value
        val content = _diaryContent.value
        if (path.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                FileUtil.write(path, content)
                QuickDailyWidget.updateAllWidgets(getApplication())
            }
        }
    }
}
