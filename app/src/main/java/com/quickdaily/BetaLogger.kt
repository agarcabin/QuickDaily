package com.quickdaily

import android.content.Context
import android.content.Intent
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BetaLogger {
    private var logFile: File? = null
    private var enabled = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private const val MAX_FILE_SIZE = 500 * 1024
    private val ioScope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences("QuickDaily", 0)
            val extEnabled = prefs.getBoolean("logging_enabled", false)
            if (extEnabled) {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                if (!docsDir.exists()) docsDir.mkdirs()
                logFile = File(docsDir, "QuickDaily_log_" + date + ".txt")
                enabled = true
                log("BetaLogger", "init with external logging from prefs")
            } else {
                logFile = File(context.filesDir, "beta_log.txt")
                enabled = true
                log("BetaLogger", "init internal (logging pref is false)")
            }
        } catch (_: Exception) { enabled = false }
    }

    fun log(tag: String, message: String) {
        if (!enabled) return
        val time = dateFormat.format(Date())
        val line = "[$time] [$tag] $message"
        android.util.Log.d("QD-Beta", line)
        ioScope.launch {
            try {
                val file = logFile ?: return@launch
                if (file.exists() && file.length() > MAX_FILE_SIZE) {
                    val content = file.readText()
                    val keepFrom = content.length / 2
                    file.writeText(content.substring(keepFrom))
                }
                file.appendText("$line\n")
            } catch (_: Exception) {}
        }
    }

    fun getLogContent(): String = try { logFile?.readText() ?: "" } catch (_: Exception) { "" }

    fun configure(context: Context, enabled: Boolean, useExternal: Boolean) {
        try {
            if (enabled && useExternal) {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                if (!docsDir.exists()) docsDir.mkdirs()
                logFile = File(docsDir, "QuickDaily_log_" + date + ".txt")
            } else if (enabled) {
                logFile = File(context.filesDir, "beta_log.txt")
            } else {
                logFile = null
            }
            this.enabled = enabled
            if (enabled) log("BetaLogger", "configured: external=" + useExternal + " path=" + (logFile?.absolutePath ?: "none"))
        } catch (_: Exception) { }
    }

    fun clear() { try { logFile?.delete() } catch (_: Exception) {} }

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
