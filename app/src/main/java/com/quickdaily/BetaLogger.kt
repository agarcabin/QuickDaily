package com.quickdaily

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BetaLogger {
    private const val HEADER_MARKER = "===== QuickDaily Beta Debug Log ====="
    @Volatile private var logFile: File? = null
    @Volatile private var enabled = false
    private var headerWritten = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            if (prefs.getBoolean("logging_enabled", false)) {
                configure(context, enabled = true, useExternal = true)
            } else {
                disable()
            }
        } catch (_: Exception) { enabled = false }
    }

    fun log(tag: String, message: String) {
        if (!enabled) return
        val time = dateFormat.format(Date())
        val line = "[$time] [$tag] $message"
        android.util.Log.d("QD-Beta", line)
        ioScope.launch {
            writeMutex.withLock {
                try {
                    if (!enabled) return@withLock
                    val file = logFile ?: return@withLock
                    if (!headerWritten) {
                        val existing = if (file.exists()) file.readText(Charsets.UTF_8) else ""
                        if (!existing.startsWith(HEADER_MARKER)) {
                            file.writeText(deviceHeader() + existing, Charsets.UTF_8)
                        }
                        headerWritten = true
                    }
                    file.appendText("$line\n", Charsets.UTF_8)
                } catch (_: Exception) {}
            }
        }
    }

    fun logException(tag: String, message: String, error: Throwable) {
        log(tag, "$message\n${android.util.Log.getStackTraceString(error)}")
    }

    fun getLogContent(): String = if (!enabled) "" else try {
        logFile?.readText(Charsets.UTF_8) ?: ""
    } catch (_: Exception) { "" }

    fun configure(context: Context, enabled: Boolean, useExternal: Boolean) {
        try {
            if (!enabled) {
                disable()
                return
            }
            val file = if (useExternal) {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val docsDir = ExternalStoragePaths.diagnosticsDirectory()
                if (!docsDir.exists()) docsDir.mkdirs()
                val migration = ExternalStoragePaths.migrateLegacyLogs()
                File(docsDir, "QuickDaily_log_$date.txt").also {
                    logFile = it
                    headerWritten = hasDeviceHeader(it)
                    this.enabled = true
                    if (migration.moved > 0 || migration.skipped > 0) {
                        log("BetaLogger", "legacy logs migrated=${migration.moved} skipped=${migration.skipped}")
                    }
                }
            } else {
                File(context.filesDir, "beta_log.txt")
            }
            logFile = file
            headerWritten = hasDeviceHeader(file)
            this.enabled = true
            log("BetaLogger", "configured: external=$useExternal path=${file.absolutePath}")
        } catch (_: Exception) { }
    }

    private fun disable() {
        enabled = false
        logFile = null
        headerWritten = false
    }

    private fun deviceHeader(): String = buildString {
        appendLine(HEADER_MARKER)
        appendLine("device.manufacturer=${Build.MANUFACTURER}")
        appendLine("device.brand=${Build.BRAND}")
        appendLine("device.model=${Build.MODEL}")
        appendLine("android.release=${Build.VERSION.RELEASE}")
        appendLine("android.sdk=${Build.VERSION.SDK_INT}")
        appendLine("android.incremental=${Build.VERSION.INCREMENTAL}")
        appendLine("android.display=${Build.DISPLAY}")
        appendLine("android.fingerprint=${Build.FINGERPRINT}")
        appendLine("=====================================")
    }

    private fun hasDeviceHeader(file: File): Boolean = try {
        file.exists() && file.bufferedReader(Charsets.UTF_8).use { it.readLine() == HEADER_MARKER }
    } catch (_: Exception) {
        false
    }

    fun clear() {
        try {
            logFile?.delete()
            headerWritten = false
        } catch (_: Exception) {}
    }

    fun shareLog(context: Context) {
        try {
            val content = getLogContent()
            if (content.isEmpty()) {
                android.widget.Toast.makeText(context, "日志为空", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            val cacheFile = java.io.File(context.cacheDir, "QuickDaily_log.txt")
            cacheFile.writeText(content)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", cacheFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享 Beta 日志"))
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, "分享失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
