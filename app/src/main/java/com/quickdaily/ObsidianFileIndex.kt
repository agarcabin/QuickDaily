package com.quickdaily

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.quickdaily.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/** Complete indexes include attachments. An incomplete scan is never used to claim uniqueness. */
object ObsidianFileIndex {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reader = BoundedLoader(timeoutMs = 5_000, capacity = 2)
    private val active = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val checkedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val _entries = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val entries = _entries.asStateFlow()

    fun paths(vault: String): List<String>? = _entries.value[vault]
    fun added(vault: String, relative: String) {
        _entries.update { map -> map[vault]?.let { map + (vault to (it + relative).distinct()) } ?: map }
    }

    fun request(context: Context, vault: String) {
        if (vault.isBlank() || checkedAt[vault]?.let { System.nanoTime() - it < 30_000_000_000L } == true || !active.add(vault)) return
        val app = context.applicationContext
        scope.launch {
            try {
                val paths = reader.load("attachment-index:$vault", vault) { scan(app, vault) }
                _entries.update { it + (vault to paths) }
                checkedAt[vault] = System.nanoTime()
            } catch (error: Exception) {
                BetaLogger.logException("ObsidianIndex", "index_unavailable", error)
            } finally { active.remove(vault) }
        }
    }

    private fun scan(context: Context, vault: String): List<String> {
        val result = mutableListOf<String>()
        val started = System.nanoTime()
        fun checkBudget(depth: Int) {
            check(depth <= 64 && result.size < 50_000 && System.nanoTime() - started < 4_000_000_000L) { "index_budget_exceeded" }
        }
        fun walkSaf(directory: DocumentFile, parent: String, depth: Int) {
            checkBudget(depth)
            check(directory.canRead()) { "index_directory_unreadable" }
            directory.listFiles().forEach { child ->
                checkBudget(depth)
                val name = child.name ?: error("index_name_unavailable")
                if (name.startsWith('.')) return@forEach
                val relative = listOf(parent, name).filter(String::isNotEmpty).joinToString("/")
                if (child.isDirectory) walkSaf(child, relative, depth + 1) else result += relative
            }
        }
        if (SafVirtualPath.isSafPath(vault)) {
            walkSaf(requireNotNull(VaultStorage.documentForPath(context, vault)), "", 0)
        } else {
            val root = File(vault).canonicalFile
            val visited = mutableSetOf<String>()
            fun walk(directory: File, depth: Int) {
                checkBudget(depth)
                check(visited.add(directory.canonicalPath)) { "index_cycle" }
                val children = directory.listFiles() ?: error("index_directory_unreadable")
                for (child in children) {
                    checkBudget(depth)
                    if (child.name.startsWith('.') || java.nio.file.Files.isSymbolicLink(child.toPath())) continue
                    val relative = VaultPathUtil.relativePath(root.path, child.canonicalPath) ?: error("index_outside_vault")
                    if (child.isDirectory) walk(child, depth + 1) else result += relative
                }
            }
            walk(root, 0)
        }
        return result
    }
}
