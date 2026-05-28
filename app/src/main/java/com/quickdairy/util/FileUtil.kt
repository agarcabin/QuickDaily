package com.quickdairy.util

import java.io.File

object FileUtil {

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
        return try {
            File(path).parentFile?.mkdirs()
            File(path).writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            android.util.Log.e("QuickDairy", "写入失败: $path", e)
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
            android.util.Log.e("QuickDairy", "写入失败: $path", e)
            WriteResult.Error(e)
        }
    }

    fun exists(path: String): Boolean = File(path).exists()
    fun isDirectory(path: String): Boolean = File(path).isDirectory
}
