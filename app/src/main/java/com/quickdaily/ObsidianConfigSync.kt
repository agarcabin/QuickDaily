package com.quickdaily

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.quickdaily.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

data class ObsidianSyncState(
    val rules: ObsidianRules,
    val dailyLoaded: Boolean = false,
    val appLoaded: Boolean = false,
    val dailyStatus: String = "unavailable",
    val appStatus: String = "unavailable",
    val dailySource: String = "",
    val appSource: String = "",
)

/** Shared by all entry points. Only cached preferences are accessed on the UI thread. */
object ObsidianConfigSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reader = BoundedLoader(timeoutMs = 3_000, capacity = 4)
    private val pending = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private const val CACHE = "QuickDailyObsidianConfig"
    private const val OWNER = "obsidian_rules_vault"

    private fun owner(prefs: SharedPreferences): String = prefs.getString(OWNER, prefs.getString("vault_path", "")).orEmpty().trimEnd('/')

    /** Run before any picker can change vault_path, so old QD values keep their original owner. */
    fun initialize(context: Context) = QuickDailyConfigMutationLock.withLock {
        val prefs = context.getSharedPreferences("QuickDaily", 0)
        if (!prefs.contains(OWNER)) {
            val vault = VaultStoragePrefs.current(context).rootPath
            prefs.edit().putString(OWNER, vault.trimEnd('/')).apply()
            if (vault.isNotBlank()) context.getSharedPreferences(CACHE, 0).edit()
                .putString(key(identity(prefs, vault)), encode(ObsidianSyncState(legacy(prefs)))).apply()
        }
    }

    fun configFolder(prefs: SharedPreferences): String = ObsidianConfigFolderPolicy.effectiveName(
        prefs.getBoolean("use_custom_obsidian_config_path", false),
        prefs.getString("obsidian_config_folder", ".obsidian").orEmpty(),
    )

    private fun identity(prefs: SharedPreferences, vault: String): String = listOf(vault.trimEnd('/'), configFolder(prefs),
        if (prefs.getBoolean("use_custom_obsidian_config_path", false)) prefs.getString("obsidian_config_uri", "").orEmpty() else "")
        .joinToString("\u0000")
    private fun key(identity: String): String = FileFingerprint.sha256(identity.toByteArray(Charsets.UTF_8))

    fun legacy(prefs: SharedPreferences): ObsidianRules = ObsidianRules(
        diaryFolder = prefs.getString("diary_folder", "Daily").orEmpty(),
        dateFormat = prefs.getString("date_format", "YYYY-MM-DD").orEmpty().ifBlank { "YYYY-MM-DD" },
        templatePath = prefs.getString("template_path", "").orEmpty(),
        attachmentFolder = prefs.getString("image_storage_path", "").orEmpty().ifEmpty { "/" },
        markdownLinks = prefs.getString("image_link_format", "described") != "obsidian_wikilink",
        linkPathFormat = prefs.getString("link_path_format", "absolute").orEmpty(),
    )

    fun state(context: Context, vault: String = VaultStoragePrefs.current(context).rootPath): ObsidianSyncState {
        val prefs = context.getSharedPreferences("QuickDaily", 0)
        val sameOwner = owner(prefs) == vault.trimEnd('/')
        val fallback = if (sameOwner) legacy(prefs) else ObsidianRules()
        return cached(context, identity(prefs, vault), fallback, sameOwner)
    }

    fun rules(context: Context, vault: String = VaultStoragePrefs.current(context).rootPath): ObsidianRules = state(context, vault).rules

    fun sourceNote(context: Context, vault: String, target: String? = null, rules: ObsidianRules = rules(context, vault)): String {
        if (target.isNullOrBlank()) return VaultPathUtil.dailyRelativePath(rules.diaryFolder, DateUtil.todayStr(rules.dateFormat))
        if (SafDocumentPath.isPath(target)) {
            val parsed = requireNotNull(SafDocumentPath.parse(target))
            val virtual = VaultStoragePrefs.virtualPathForDocumentUri(context, vault, parsed.documentUri)
            return requireNotNull(virtual?.let { VaultPathUtil.relativePath(vault, it) }) { "note_outside_vault" }
        }
        return VaultPathUtil.relativePath(vault, target) ?: requireNotNull(ObsidianLinkPolicy.normalize(target)).also { require(it.isNotBlank()) }
    }

    private fun cached(context: Context, id: String, fallback: ObsidianRules, useFallback: Boolean = true): ObsidianSyncState {
        val raw = context.getSharedPreferences(CACHE, 0).getString(key(id), null) ?: return ObsidianSyncState(fallback)
        return runCatching {
            val obj = Json.parseToJsonElement(raw).jsonObject
            fun text(name: String, default: String = "") = obj[name]?.jsonPrimitive?.content ?: default
            fun bool(name: String) = obj[name]?.jsonPrimitive?.booleanOrNull ?: false
            ObsidianSyncState(
                ObsidianRules(text("folder"), text("format", "YYYY-MM-DD"), text("template"),
                    text("attachment", "/"), bool("markdown"), text("links", "shortest")),
                bool("dailyLoaded"), bool("appLoaded"), text("dailyStatus", "unavailable"), text("appStatus", "unavailable"),
                text("dailySource"), text("appSource"),
            ).let { state -> if (!useFallback) state else state.copy(rules = state.rules.copy(
                diaryFolder = if (state.dailyLoaded) state.rules.diaryFolder else fallback.diaryFolder,
                dateFormat = if (state.dailyLoaded) state.rules.dateFormat else fallback.dateFormat,
                templatePath = if (state.dailyLoaded) state.rules.templatePath else fallback.templatePath,
                attachmentFolder = if (state.appLoaded) state.rules.attachmentFolder else fallback.attachmentFolder,
                markdownLinks = if (state.appLoaded) state.rules.markdownLinks else fallback.markdownLinks,
                linkPathFormat = if (state.appLoaded) state.rules.linkPathFormat else fallback.linkPathFormat,
            )) }
        }.getOrElse { ObsidianSyncState(fallback) }
    }

    private fun encode(state: ObsidianSyncState) = buildJsonObject {
        put("folder", state.rules.diaryFolder); put("format", state.rules.dateFormat); put("template", state.rules.templatePath)
        put("attachment", state.rules.attachmentFolder); put("markdown", state.rules.markdownLinks); put("links", state.rules.linkPathFormat)
        put("dailyLoaded", state.dailyLoaded); put("appLoaded", state.appLoaded)
        put("dailyStatus", state.dailyStatus); put("appStatus", state.appStatus)
        put("dailySource", state.dailySource); put("appSource", state.appSource)
    }.toString()

    /** Reapply known OB values when an old Settings draft is saved. New vaults never inherit old paths. */
    fun applyCached(context: Context, config: DiaryConfig): DiaryConfig {
        val prefs = context.getSharedPreferences("QuickDaily", 0)
        val sameVault = owner(prefs) == config.vaultPath.trimEnd('/')
        val fallback = if (sameVault) ObsidianRules(config.diaryFolder, config.dateFormat, config.templatePath,
            config.imageStoragePath.ifEmpty { "/" }, config.imageLinkFormat != "obsidian_wikilink", config.linkPathFormat) else ObsidianRules()
        // Use the draft's source identity, without temporarily changing global preferences.
        val id = listOf(config.vaultPath.trimEnd('/'), ObsidianConfigFolderPolicy.effectiveName(config.useCustomObsidianConfigPath, config.obsidianConfigFolder),
            config.obsidianConfigUri.takeIf { config.useCustomObsidianConfigPath }.orEmpty()).joinToString("\u0000")
        val state = cached(context, id, fallback, sameVault)
        val rules = state.rules
        return config.copy(diaryFolder = rules.diaryFolder, dateFormat = rules.dateFormat, templatePath = rules.templatePath,
            imageStoragePath = rules.attachmentFolder.takeUnless { it == "/" }.orEmpty(),
            imageLinkFormat = if (state.appLoaded || !sameVault) { if (rules.markdownLinks) "described" else "obsidian_wikilink" } else config.imageLinkFormat,
            linkPathFormat = rules.linkPathFormat)
    }

    fun rememberSettings(context: Context, config: DiaryConfig) {
        val prefs = context.getSharedPreferences("QuickDaily", 0)
        val id = identity(prefs, config.vaultPath)
        val saved = cached(context, id, legacy(prefs))
        context.getSharedPreferences(CACHE, 0).edit().putString(key(id), encode(saved)).apply()
        prefs.edit().putString(OWNER, config.vaultPath.trimEnd('/')).apply()
    }

    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("QuickDaily", 0)
        val vault = VaultStoragePrefs.current(app).rootPath
        if (vault.isBlank()) return
        val id = identity(prefs, vault)
        if (!pending.add(id)) return
        scope.launch { try { refresh(app) } finally { pending.remove(id) } }
    }

    suspend fun refresh(context: Context): ObsidianSyncState = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("QuickDaily", 0)
        val vault = VaultStoragePrefs.current(context).rootPath
        val id = identity(prefs, vault)
        if (vault.isBlank()) return@withContext state(context, vault)
        val folder = configFolder(prefs)
        val defaultDaily = VaultPathUtil.resolve(vault, "$folder/daily-notes.json").orEmpty()
        val selected = prefs.getString("obsidian_config_uri", "").orEmpty().takeIf { prefs.getBoolean("use_custom_obsidian_config_path", false) }
        val dailyPath = selected?.takeIf(String::isNotBlank) ?: defaultDaily
        val appPath = VaultPathUtil.resolve(vault, "$folder/app.json").orEmpty()
        data class Read(val raw: String? = null, val status: String = "unavailable")
        fun read(path: String): Read = try {
            reader.load("config:$path", path) {
                val raw = if (path.startsWith("content:")) {
                    context.contentResolver.openInputStream(Uri.parse(path))?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 1_048_576) { "config_too_large" }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                } else FileUtil.readOrNull(path)
                if (raw == null) Read() else Read(raw, "ok")
            }
        } catch (_: Exception) { Read(status = "unavailable") }
        val (dailyRead, appRead) = coroutineScope {
            val daily = async { read(dailyPath) }
            val app = async { read(appPath) }
            daily.await() to app.await()
        }
        QuickDailyConfigMutationLock.withLock {
            val sameOwner = owner(prefs) == vault.trimEnd('/')
            val result = ObsidianSyncPolicy.merge(cached(context, id, if (sameOwner) legacy(prefs) else ObsidianRules(), sameOwner), dailyRead.raw, appRead.raw,
                dailyPath, appPath, dailyRead.status, appRead.status)
            // A late read must not repopulate the current vault's global preferences.
            if (identity(prefs, VaultStoragePrefs.current(context).rootPath) == id) {
                context.getSharedPreferences(CACHE, 0).edit().putString(key(id), encode(result)).apply()
                val r = result.rules
                prefs.edit().putString("diary_folder", r.diaryFolder).putString("date_format", r.dateFormat)
                    .putString(OWNER, vault.trimEnd('/'))
                    .putString("template_path", r.templatePath).putString("image_storage_path", r.attachmentFolder.takeUnless { it == "/" }.orEmpty())
                    .putString("image_link_format", if (r.markdownLinks) "described" else "obsidian_wikilink")
                    .putString("link_path_format", r.linkPathFormat).apply()
                ObsidianFileIndex.request(context, vault)
            }
            result
        }
    }
}
