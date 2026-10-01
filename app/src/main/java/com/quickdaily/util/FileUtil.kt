package com.quickdaily.util

import java.io.File
import java.security.MessageDigest
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.ConcurrentHashMap

data class FileFingerprint(
    val exists: Boolean,
    val length: Long,
    val sha256: String,
    val lastModified: Long,
) {
    fun hasSameContentAs(other: FileFingerprint?): Boolean =
        other != null && exists == other.exists && length == other.length && sha256 == other.sha256
}

data class FileReadSnapshot(
    val result: ReadResult,
    val fingerprint: FileFingerprint?,
)

object FileUtil {

    private val mutationLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun acquirePathMutation(path: String): AutoCloseable {
        val key = if (isSafBackedPath(path)) {
            path.trim()
        } else {
            runCatching { File(path).canonicalPath }
                .getOrElse { File(path).absolutePath }
        }
        val lock = mutationLocks.computeIfAbsent(key) { ReentrantLock() }
        lock.lock()
        return AutoCloseable { lock.unlock() }
    }

    /** Serializes a complete read-modify-write section by canonical file path. */
    fun <T> withPathMutation(path: String, block: () -> T): T {
        val guard = acquirePathMutation(path)
        return try {
            block()
        } finally {
            guard.close()
        }
    }

    fun read(path: String): String {
        if (isSafBackedPath(path)) {
            return StorageContextHolder.get()?.let { VaultStorage.readText(it, path).orEmpty() }.orEmpty()
        }
        return try {
            File(path).readText(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun readOrNull(path: String): String? {
        if (isSafBackedPath(path)) {
            return StorageContextHolder.get()?.let { VaultStorage.readText(it, path) }
        }
        return try {
            File(path).readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun write(path: String, content: String): Boolean {
        if (isSafBackedPath(path)) {
            val context = StorageContextHolder.get()
            return context != null && VaultStorage.writeText(context, path, content)
        }
        val target = File(path).absoluteFile
        return try {
            target.parentFile?.mkdirs()
            val temporary = File.createTempFile(".${target.name}.", ".tmp", target.parentFile)
            try {
                temporary.writeText(content, Charsets.UTF_8)
                try {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            } finally {
                if (temporary.exists()) temporary.delete()
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("QuickDaily", "写入失败: $path", e)
            false
        }
    }

    fun readResult(path: String): ReadResult {
        if (isSafBackedPath(path)) {
            val context = StorageContextHolder.get()
                ?: return ReadResult.Error(IllegalStateException("SAF context unavailable"))
            if (!VaultStorage.exists(context, path)) return ReadResult.NotFound
            return VaultStorage.readText(context, path)?.let(ReadResult::Success)
                ?: ReadResult.Error(IllegalStateException("SAF document cannot be read"))
        }
        val file = java.io.File(path)
        if (!file.exists()) return ReadResult.NotFound
        return try {
            ReadResult.Success(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            ReadResult.Error(e)
        }
    }

    /**
     * Reads content and its fingerprint while sharing the same path lock as widget mutations.
     * The before/after check also rejects a torn snapshot if another process replaces the file.
     */
    fun readStableSnapshot(path: String, maxAttempts: Int = 3): FileReadSnapshot =
        withPathMutation(path) {
            var lastFingerprint: FileFingerprint? = null
            repeat(maxAttempts.coerceAtLeast(1)) {
                val before = fingerprint(path)
                val result = readResult(path)
                val after = fingerprint(path)
                lastFingerprint = after
                if (before != null && after != null && before.hasSameContentAs(after)) {
                    return@withPathMutation FileReadSnapshot(result, after)
                }
            }
            FileReadSnapshot(
                result = ReadResult.Error(IOException("File changed while reading: $path")),
                fingerprint = lastFingerprint,
            )
        }

    /** Reads a stable content fingerprint for external-edit detection. */
    fun fingerprint(path: String): FileFingerprint? {
        if (isSafBackedPath(path)) {
            return StorageContextHolder.get()?.let { VaultStorage.fingerprint(it, path) }
        }
        val file = File(path)
        if (!file.exists()) return FileFingerprint(false, 0L, "", 0L)
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            FileFingerprint(
                exists = true,
                length = file.length(),
                sha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) },
                lastModified = file.lastModified(),
            )
        }.getOrNull()
    }

    fun writeResult(path: String, content: String): WriteResult {
        if (isSafBackedPath(path)) {
            val context = StorageContextHolder.get()
            return if (context != null && VaultStorage.writeText(context, path, content)) {
                WriteResult.Success
            } else {
                WriteResult.Error(IllegalStateException("SAF document cannot be written"))
            }
        }
        return try {
            java.io.File(path).parentFile?.mkdirs()
            java.io.File(path).writeText(content, Charsets.UTF_8)
            WriteResult.Success
        } catch (e: Exception) {
            android.util.Log.e("QuickDaily", "写入失败: $path", e)
            WriteResult.Error(e)
        }
    }

    fun exists(path: String): Boolean = if (isSafBackedPath(path)) {
        StorageContextHolder.get()?.let { VaultStorage.exists(it, path) } == true
    } else {
        File(path).exists()
    }

    fun isDirectory(path: String): Boolean = if (isSafBackedPath(path)) {
        StorageContextHolder.get()?.let { VaultStorage.isDirectory(it, path) } == true
    } else {
        File(path).isDirectory
    }

    /** 返回文件最后修改时间戳；不存在返回 0 */
    fun canWrite(path: String): Boolean = if (isSafBackedPath(path)) {
        StorageContextHolder.get()?.let { VaultStorage.canWrite(it, path) } == true
    } else {
        runCatching { File(path).isFile && File(path).canWrite() }.getOrDefault(false)
    }

    fun lastModified(path: String): Long = if (isSafBackedPath(path)) {
        StorageContextHolder.get()?.let { VaultStorage.lastModified(it, path) } ?: 0L
    } else {
        runCatching { File(path).lastModified() }.getOrDefault(0L)
    }

    private fun isSafBackedPath(path: String): Boolean =
        SafVirtualPath.isSafPath(path) || SafDocumentPath.isPath(path)
}
