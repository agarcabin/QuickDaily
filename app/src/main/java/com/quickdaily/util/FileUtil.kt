package com.quickdaily.util

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.ConcurrentHashMap

object FileUtil {

    private val mutationLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun acquirePathMutation(path: String): AutoCloseable {
        val key = runCatching { File(path).canonicalPath }
            .getOrElse { File(path).absolutePath }
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
        return try {
            File(path).readText(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun readOrNull(path: String): String? {
        return try {
            File(path).readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun write(path: String, content: String): Boolean {
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
        val file = java.io.File(path)
        if (!file.exists()) return ReadResult.NotFound
        return try {
            ReadResult.Success(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            ReadResult.Error(e)
        }
    }

    fun writeResult(path: String, content: String): WriteResult {
        return try {
            java.io.File(path).parentFile?.mkdirs()
            java.io.File(path).writeText(content, Charsets.UTF_8)
            WriteResult.Success
        } catch (e: Exception) {
            android.util.Log.e("QuickDaily", "写入失败: $path", e)
            WriteResult.Error(e)
        }
    }

    fun exists(path: String): Boolean = File(path).exists()
    fun isDirectory(path: String): Boolean = File(path).isDirectory

    /** 返回文件最后修改时间戳；不存在返回 0 */
    fun lastModified(path: String): Long = runCatching {
        File(path).lastModified()
    }.getOrDefault(0L)
}
