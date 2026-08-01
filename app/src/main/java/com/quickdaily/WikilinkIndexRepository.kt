package com.quickdaily

import android.content.Context
import android.util.Base64
import com.quickdaily.util.ContentUtil
import com.quickdaily.util.TagScanner
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WikilinkAlias(
    val alias: String,
    val targetPath: String,
)

data class WikilinkIndexState(
    val rootPath: String = "",
    val entries: List<String> = emptyList(),
    val aliases: List<WikilinkAlias> = emptyList(),
    val candidates: List<WikilinkCandidate> = emptyList(),
    val tags: List<String> = emptyList(),
    val loading: Boolean = false,
    val indexed: Boolean = false,
    val tagsIndexed: Boolean = false,
    val error: String? = null,
) {
    val aliasCount: Int
        get() = aliases.asSequence().map { it.alias }.distinct().count()
}

/** Shared, cached completion index for the main editor and the floating editor. */
object WikilinkIndexRepository {
    private const val MAX_SCAN_DEPTH = 50
    private const val CACHE_FILE = "wikilink_index_cache_v2.txt"
    private const val CACHE_VERSION = "2"
    private const val TYPE_PAGE = "P"
    private const val TYPE_ALIAS = "A"
    private const val TYPE_TAG = "T"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow(WikilinkIndexState())
    private val activeJob = AtomicReference<Job?>(null)

    val indexState: StateFlow<WikilinkIndexState> = state.asStateFlow()

    /** Load the cached index, or perform the first full scan for this vault. */
    fun ensureIndexed(context: Context, rootPath: String) {
        val root = rootPath.trim()
        if (root.isBlank()) return
        val current = state.value
        if (current.rootPath == root && (current.loading || current.indexed)) return
        // A changed vault path is a new configuration and must be scanned now.
        // Only the initial app process can restore a matching persisted cache.
        val pathChanged = current.rootPath.isNotBlank() && current.rootPath != root
        refreshInternal(context.applicationContext, root, force = pathChanged)
    }

    /** Manual refresh always scans pages, aliases and tags. */
    fun refresh(context: Context, rootPath: String) {
        val root = rootPath.trim()
        if (root.isBlank()) {
            state.value = WikilinkIndexState(rootPath = root, indexed = true, error = "请先设置仓库路径")
            return
        }
        refreshInternal(context.applicationContext, root, force = true)
    }

    private fun refreshInternal(context: Context, root: String, force: Boolean) {
        synchronized(this) {
            val current = state.value
            if (!force && current.rootPath == root && (current.loading || current.indexed)) return
            activeJob.get()?.cancel()
            state.value = WikilinkIndexState(rootPath = root, loading = true)
            activeJob.set(scope.launch {
                val result = runCatching {
                    if (!force) readCache(context, root) ?: scan(root).also { writeCache(context, root, it) }
                    else scan(root).also { writeCache(context, root, it) }
                }
                val next = result.fold(
                    onSuccess = { scanResult -> scanResult.toState(root) },
                    onFailure = { error ->
                        WikilinkIndexState(
                            rootPath = root,
                            indexed = false,
                            error = error.message ?: "双链索引失败",
                        )
                    },
                )
                state.value = next
            })
        }
    }

    private data class ScanResult(
        val entries: List<String>,
        val aliases: List<WikilinkAlias>,
        val tags: List<String>,
    ) {
        fun toState(root: String): WikilinkIndexState {
            val pageCandidates = entries.map { WikilinkCandidate(targetPath = it) }
            val aliasCandidates = aliases.map { WikilinkCandidate(targetPath = it.targetPath, alias = it.alias) }
            return WikilinkIndexState(
                rootPath = root,
                entries = entries,
                aliases = aliases,
                candidates = pageCandidates + aliasCandidates,
                tags = tags,
                loading = false,
                indexed = true,
                tagsIndexed = true,
            )
        }
    }

    private fun scan(rootPath: String): ScanResult {
        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) error("仓库路径不可读：$rootPath")

        val entries = mutableListOf<String>()
        val aliases = mutableListOf<WikilinkAlias>()
        val tags = linkedSetOf<String>()
        root.walkTopDown()
            .maxDepth(MAX_SCAN_DEPTH)
            .onEnter { !it.isHidden }
            .filter { file ->
                file.isFile && !file.isHidden && file.extension.equals("md", ignoreCase = true)
            }
            .forEach { file ->
                runCatching {
                    val targetPath = root.toPath().relativize(file.toPath()).toString()
                        .replace(File.separatorChar, '/')
                        .removeSuffix(".${file.extension}")
                    entries += targetPath
                    val content = file.readText(Charsets.UTF_8)
                    val parsed = ContentUtil.parseFrontmatter(content)
                    WikilinkAliasParser.parse(parsed.frontmatter).forEach { alias ->
                        aliases += WikilinkAlias(alias = alias, targetPath = targetPath)
                    }
                    tags += TagScanner.extractTags(content)
                }
            }

        val uniqueEntries = entries.distinctBy { it.lowercase() }
            .sortedWith(compareBy<String> { it.length }.thenBy(String.CASE_INSENSITIVE_ORDER) { it })
        val uniqueAliases = aliases
            .distinctBy { "${it.alias}\u001f${it.targetPath}" }
            .sortedWith(
                compareBy<WikilinkAlias> { it.alias.length }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.alias }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.targetPath },
            )
        return ScanResult(
            entries = uniqueEntries,
            aliases = uniqueAliases,
            tags = tags.toList().sortedWith(String.CASE_INSENSITIVE_ORDER),
        )
    }

    private fun readCache(context: Context, root: String): ScanResult? {
        val file = File(context.filesDir, CACHE_FILE)
        if (!file.isFile) return null
        val lines = file.readLines(Charsets.UTF_8)
        if (lines.firstOrNull() != CACHE_VERSION || decode(lines.getOrNull(1).orEmpty()) != root) return null

        val entries = mutableListOf<String>()
        val aliases = mutableListOf<WikilinkAlias>()
        val tags = mutableListOf<String>()
        lines.drop(2).forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                TYPE_PAGE -> parts.getOrNull(1)?.let { decode(it) }?.takeIf(String::isNotBlank)?.let(entries::add)
                TYPE_ALIAS -> {
                    val alias = parts.getOrNull(1)?.let(::decode).orEmpty()
                    val target = parts.getOrNull(2)?.let(::decode).orEmpty()
                    if (alias.isNotBlank() && target.isNotBlank()) aliases += WikilinkAlias(alias, target)
                }
                TYPE_TAG -> parts.getOrNull(1)?.let { decode(it) }?.takeIf(String::isNotBlank)?.let(tags::add)
            }
        }
        return ScanResult(
            entries = entries.distinctBy { it.lowercase() },
            aliases = aliases.distinctBy { "${it.alias}\u001f${it.targetPath}" },
            tags = tags.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER),
        )
    }

    private fun writeCache(context: Context, root: String, result: ScanResult) {
        val file = File(context.filesDir, CACHE_FILE)
        val lines = buildList {
            add(CACHE_VERSION)
            add(encode(root))
            result.entries.forEach { add("$TYPE_PAGE\t${encode(it)}") }
            result.aliases.forEach { add("$TYPE_ALIAS\t${encode(it.alias)}\t${encode(it.targetPath)}") }
            result.tags.forEach { add("$TYPE_TAG\t${encode(it)}") }
        }
        file.writeText(lines.joinToString("\n"), Charsets.UTF_8)
    }

    private fun encode(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun decode(value: String): String =
        runCatching { String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8) }.getOrDefault("")
}
